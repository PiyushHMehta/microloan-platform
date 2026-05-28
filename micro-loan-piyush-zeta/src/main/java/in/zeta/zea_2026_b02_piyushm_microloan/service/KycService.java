package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycVerifyOtpRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Kyc;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.OtpVerification;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.KycMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.KycRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.OtpVerificationRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(KycService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String FIELD_BORROWER_ID = "borrowerId";
    private static final String DOC_TYPE_PAN = "PAN";

    private final BorrowerRepository borrowerRepository;
    private final KycRepository kycRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final KycMapper kycMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final EncryptionService encryptionService;

    @Transactional
    public KycInitiateResponse initiateKyc(UUID borrowerId, KycInitiateRequest dto) {
        // 1. Validate borrower
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BRW_003));
        if (!borrower.getIsActive()) {
            throw new BusinessException(ErrorCode.BRW_005);
        }

        String docType = dto.getDocumentType().toUpperCase();

        // 2. Validate document format and resolve normalised value
        String docValue = validateAndResolveDocValue(docType, dto);

        // 3. Check document uniqueness
        checkDocumentUniqueness(docType, docValue, borrowerId);

        // 4. Upsert KYC record (store doc, NOT verified yet)
        Kyc kyc = kycRepository.findByBorrowerId(borrowerId)
                .orElse(Kyc.builder().borrowerId(borrowerId).build());

        if (DOC_TYPE_PAN.equals(docType)) {
            kyc.setPanNumber(docValue);
        } else {
            kyc.setAadhaarNumber(docValue);
        }
        kycRepository.save(kyc);

        // 5. Generate OTP
        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        OtpVerification otp = OtpVerification.builder()
                .borrowerId(borrowerId)
                .documentType(docType)
                .otpCode(otpCode)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .build();
        otpVerificationRepository.save(otp);

        // 6. Send OTP via SMS — always bypasses Atropos (credentials must not enter the event bus)
        // Re-fetch the borrower to guard against deletion between validation and notification.
        borrowerRepository.findById(borrowerId).ifPresent(b -> {
            if (b.getPhoneNumber() != null && !b.getPhoneNumber().isBlank()) {
                notificationDispatcher.dispatchOtpGenerated(b.getPhoneNumber(), docType, otpCode);
            }
        });
        log.info("OTP generated").attr(FIELD_BORROWER_ID, borrowerId).attr("docType", docType).log();

        return KycInitiateResponse.builder()
                .message("OTP sent for verification")
                .documentType(docType)
                .expiresInSeconds(OTP_EXPIRY_MINUTES * 60)
                .otp(otpCode)
                .build();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public KycResponse verifyOtp(UUID borrowerId, KycVerifyOtpRequest dto) {
        String docType = dto.getDocumentType().toUpperCase();

        // 1. Fetch latest unverified OTP
        OtpVerification otp = otpVerificationRepository
                .findLatestUnverified(borrowerId, docType)
                .orElseThrow(() -> new BusinessException(ErrorCode.KYC_008));

        // 2. Check expiry
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            findActivePhone(borrowerId).ifPresent(phone ->
                    notificationDispatcher.dispatchOtpExpired(phone, docType));
            throw new BusinessException(ErrorCode.KYC_008);
        }

        // 3. Check attempt limit
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            findActivePhone(borrowerId).ifPresent(phone ->
                    notificationDispatcher.dispatchOtpMaxAttemptsExceeded(phone, docType));
            throw new BusinessException(ErrorCode.KYC_010);
        }

        // 4. Increment attempts
        otp.setAttempts(otp.getAttempts() + 1);
        otpVerificationRepository.save(otp);

        // 5. Verify OTP
        if (!dto.getOtp().equals(otp.getOtpCode())) {
            int attemptsRemaining = MAX_OTP_ATTEMPTS - otp.getAttempts();
            findActivePhone(borrowerId).ifPresent(phone ->
                    notificationDispatcher.dispatchOtpFailed(phone, docType, attemptsRemaining));
            throw new BusinessException(ErrorCode.KYC_009);
        }

        // 6. Mark OTP verified
        otp.setVerified(true);
        otpVerificationRepository.save(otp);

        // 7. Update KYC record
        Kyc kyc = kycRepository.findByBorrowerId(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.KYC_006));

        if (DOC_TYPE_PAN.equals(docType)) {
            kyc.setPanVerified(true);
        } else {
            kyc.setAadhaarVerified(true);
        }
        kycRepository.save(kyc);

        // 8. Derive KYC level
        KycLevel newLevel = deriveKycLevel(kyc);

        // 9. Update borrower KYC level if changed
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BRW_003));

        if (newLevel != borrower.getKycLevel()) {
            KycLevel oldLevel = borrower.getKycLevel();
            log.info("KYC level upgraded").attr(FIELD_BORROWER_ID, borrowerId).attr("oldLevel", oldLevel).attr("newLevel", newLevel).log();
            borrower.setKycLevel(newLevel);
            borrowerRepository.save(borrower);

            Map<String, Object> kycPayload = new HashMap<>();
            kycPayload.put(FIELD_BORROWER_ID, borrower.getBorrowerId());
            kycPayload.put("email", borrower.getEmail());
            kycPayload.put("newLevel", newLevel.name());
            kycPayload.put("oldLevel", oldLevel != null ? oldLevel.name() : "NONE");
            notificationDispatcher.dispatchKycStatusUpdated(borrower.getEmail(), kycPayload);
        }

        // Notify borrower of successful verification (regardless of whether level changed)
        findActivePhone(borrowerId).ifPresent(phone ->
            notificationDispatcher.dispatchKycVerified(phone, docType, newLevel.name())
        );

        return kycMapper.toResponse(kyc, newLevel);
    }

    @Transactional(readOnly = true)
    public KycResponse getByBorrowerId(UUID borrowerId) {
        if (!borrowerRepository.existsById(borrowerId)) {
            throw new ResourceNotFoundException(ErrorCode.BRW_003);
        }

        Kyc kyc = kycRepository.findByBorrowerId(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.KYC_006));

        KycLevel level = deriveKycLevel(kyc);
        return kycMapper.toResponse(kyc, level);
    }

    private String validateAndResolveDocValue(String docType, KycInitiateRequest dto) {
        return switch (docType) {
            case DOC_TYPE_PAN -> {
                String upper = dto.getDocumentValue().toUpperCase();
                if (!upper.matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) {
                    throw new BusinessException(ErrorCode.KYC_001);
                }
                yield upper;
            }
            case "AADHAAR" -> {
                if (!dto.getDocumentValue().matches("^\\d{12}$")) {
                    throw new BusinessException(ErrorCode.KYC_002);
                }
                yield dto.getDocumentValue();
            }
            default -> throw new BusinessException(ErrorCode.KYC_001);
        };
    }

    private void checkDocumentUniqueness(String docType, String docValue, UUID borrowerId) {
        // Encrypt the search value so it matches the encrypted values stored in the DB.
        // The converter stores encrypted ciphertext; repository derived queries pass the
        // parameter as-is to SQL, so we must encrypt manually before querying.
        String encryptedValue = encryptionService.encrypt(docValue);
        if (DOC_TYPE_PAN.equals(docType)) {
            kycRepository.findByPanNumber(encryptedValue).ifPresent(existing -> {
                if (!existing.getBorrowerId().equals(borrowerId)) {
                    throw new DuplicateResourceException(ErrorCode.KYC_003);
                }
            });
        } else {
            kycRepository.findByAadhaarNumber(encryptedValue).ifPresent(existing -> {
                if (!existing.getBorrowerId().equals(borrowerId)) {
                    throw new DuplicateResourceException(ErrorCode.KYC_004);
                }
            });
        }
    }

    private Optional<String> findActivePhone(UUID borrowerId) {
        return borrowerRepository.findById(borrowerId)
                .map(Borrower::getPhoneNumber)
                .filter(phone -> phone != null && !phone.isBlank());
    }

    private KycLevel deriveKycLevel(Kyc kyc) {
        if (Boolean.TRUE.equals(kyc.getPanVerified()) && Boolean.TRUE.equals(kyc.getAadhaarVerified())) {
            return KycLevel.FULL_KYC;
        } else if (Boolean.TRUE.equals(kyc.getPanVerified()) || Boolean.TRUE.equals(kyc.getAadhaarVerified())) {
            return KycLevel.MIN_KYC;
        }
        return KycLevel.NO_KYC;
    }

}
