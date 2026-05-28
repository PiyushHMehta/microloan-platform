package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycService Tests")
class KycServiceTest {

    @Mock private BorrowerRepository borrowerRepository;
    @Mock private KycRepository kycRepository;
    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private KycMapper kycMapper;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private KycService kycService;

    private UUID borrowerId;
    private Borrower borrower;
    private Kyc kyc;

    @BeforeEach
    void setUp() {
        borrowerId = UUID.randomUUID();
        lenient().when(encryptionService.encrypt(any())).thenAnswer(inv -> inv.getArgument(0));

        borrower = new Borrower();
        borrower.setBorrowerId(borrowerId);
        borrower.setIsActive(true);
        borrower.setEmail("test@example.com");
        borrower.setPhoneNumber("9876543210");
        borrower.setKycLevel(KycLevel.NO_KYC);

        kyc = new Kyc();
        kyc.setKycId(UUID.randomUUID());
        kyc.setBorrowerId(borrowerId);
        kyc.setPanVerified(false);
        kyc.setAadhaarVerified(false);
    }

    // ── initiateKyc() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiateKyc()")
    class InitiateKyc {

        @Test
        @DisplayName("Throws ResourceNotFoundException when borrower does not exist")
        void throwsWhenBorrowerNotFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            KycInitiateRequest request = buildInitiateRequest("PAN", "ABCDE1234F");

            assertThatThrownBy(() -> kycService.initiateKyc(borrowerId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when borrower is inactive")
        void throwsWhenBorrowerInactive() {
            borrower.setIsActive(false);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycInitiateRequest request = buildInitiateRequest("PAN", "ABCDE1234F");

            assertThatThrownBy(() -> kycService.initiateKyc(borrowerId, request))
                    .isInstanceOf(BusinessException.class);
        }


        @ParameterizedTest
        @MethodSource("invalidKycFormatProvider")
        @DisplayName("Throws BusinessException for invalid/unknown KYC document formats")
        void throwsOnInvalidKycFormat(String docType, String docValue) {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycInitiateRequest request = buildInitiateRequest(docType, docValue);
            assertThatThrownBy(() -> kycService.initiateKyc(borrowerId, request))
                    .isInstanceOf(BusinessException.class);
        }

        static java.util.stream.Stream<Arguments> invalidKycFormatProvider() {
            return java.util.stream.Stream.of(
                Arguments.of("PAN", "INVALID123"),
                Arguments.of("PAN", "1234567890"),
                Arguments.of("AADHAAR", "12345"),
                Arguments.of("PASSPORT", "ABC123") // unknown doc type
            );
        }

        @Test
        @DisplayName("Throws DuplicateResourceException when PAN belongs to different borrower")
        void throwsOnDuplicatePanForDifferentBorrower() {
            Kyc otherKyc = new Kyc();
            otherKyc.setBorrowerId(UUID.randomUUID()); // different borrower

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByPanNumber("ABCDE1234F")).thenReturn(Optional.of(otherKyc));
            KycInitiateRequest request = buildInitiateRequest("PAN", "ABCDE1234F");

            assertThatThrownBy(() -> kycService.initiateKyc(borrowerId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("Throws DuplicateResourceException when Aadhaar belongs to different borrower")
        void throwsOnDuplicateAadhaarForDifferentBorrower() {
            Kyc otherKyc = new Kyc();
            otherKyc.setBorrowerId(UUID.randomUUID());

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByAadhaarNumber("123456789012")).thenReturn(Optional.of(otherKyc));
            KycInitiateRequest request = buildInitiateRequest("AADHAAR", "123456789012");

            assertThatThrownBy(() -> kycService.initiateKyc(borrowerId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("PAN re-submission by same borrower does NOT throw duplicate error")
        void sameOwnerPanResubmissionAllowed() {
            Kyc ownKyc = new Kyc();
            ownKyc.setBorrowerId(borrowerId); // same borrower

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByPanNumber("ABCDE1234F")).thenReturn(Optional.of(ownKyc));
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            // Should NOT throw
            kycService.initiateKyc(borrowerId, buildInitiateRequest("PAN", "ABCDE1234F"));

            verify(otpVerificationRepository).save(any());
        }

        @Test
        @DisplayName("Success PAN: saves KYC, generates OTP, sends notification")
        void successPanFlow() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByPanNumber("ABCDE1234F")).thenReturn(Optional.empty());
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            var response = kycService.initiateKyc(borrowerId, buildInitiateRequest("PAN", "ABCDE1234F"));

            assertThat(response).isNotNull();
            assertThat(response.getDocumentType()).isEqualTo("PAN");
            assertThat(response.getOtp()).hasSize(6);
            verify(notificationDispatcher).dispatchOtpGenerated(eq("9876543210"), eq("PAN"), any());
        }

        @Test
        @DisplayName("Success AADHAAR: saves KYC record and generates OTP")
        void successAadhaarFlow() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByAadhaarNumber("123456789012")).thenReturn(Optional.empty());
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            var response = kycService.initiateKyc(borrowerId, buildInitiateRequest("AADHAAR", "123456789012"));

            assertThat(response.getDocumentType()).isEqualTo("AADHAAR");
            verify(kycRepository).save(any());
        }

        @Test
        @DisplayName("Creates new KYC record when borrower has no existing KYC")
        void createsNewKycWhenAbsent() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByPanNumber("ABCDE1234F")).thenReturn(Optional.empty());
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.empty()); // no existing KYC
            when(kycRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            kycService.initiateKyc(borrowerId, buildInitiateRequest("PAN", "ABCDE1234F"));

            // Should save a new Kyc with borrowerId set
            verify(kycRepository).save(argThat(k -> borrowerId.equals(k.getBorrowerId())));
        }
    }

    // ── verifyOtp() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtp {

        @Test
        @DisplayName("Throws BusinessException when no unverified OTP exists")
        void throwsWhenNoOtpFound() {
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.empty());
            KycVerifyOtpRequest request = buildVerifyRequest("123456", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when OTP is expired, dispatches expiry notification")
        void throwsOnExpiredOtp() {
            OtpVerification expired = buildOtp("123456", LocalDateTime.now().minusMinutes(15), false, 0);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(expired));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("123456", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher).dispatchOtpExpired("9876543210", "PAN");
        }

        @Test
        @DisplayName("Throws BusinessException when max attempts (5) exceeded, dispatches blocked notification")
        void throwsOnMaxAttemptsReached() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 5);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            // attempts check fires BEFORE saveAndFlush — verify NOT called
            verify(otpVerificationRepository, never()).saveAndFlush(any());
            verify(notificationDispatcher).dispatchOtpMaxAttemptsExceeded("9876543210", "PAN");
        }

        @Test
        @DisplayName("Throws BusinessException on wrong OTP, increments attempts, dispatches failure notification")
        void throwsOnWrongOtpAndIncrementsAttempts() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            // attempt counter must be persisted even though exception is thrown
            verify(otpVerificationRepository).save(any());
            assertThat(otp.getAttempts()).isEqualTo(1);
            // 4 attempts remaining (5 max - 1 just used)
            verify(notificationDispatcher).dispatchOtpFailed("9876543210", "PAN", 4);
        }

        @Test
        @DisplayName("PAN verified → borrower KYC level upgrades to MIN_KYC")
        void panVerifiedUpgradesToMinKyc() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            assertThat(kyc.getPanVerified()).isTrue();
            assertThat(borrower.getKycLevel()).isEqualTo(KycLevel.MIN_KYC);
        }

        @Test
        @DisplayName("Both PAN and Aadhaar verified → borrower KYC level upgrades to FULL_KYC")
        void bothVerifiedUpgradesToFullKyc() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            kyc.setPanVerified(true); // PAN already done
            borrower.setKycLevel(KycLevel.MIN_KYC);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "AADHAAR")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "AADHAAR"));

            assertThat(kyc.getAadhaarVerified()).isTrue();
            assertThat(borrower.getKycLevel()).isEqualTo(KycLevel.FULL_KYC);
        }

        @Test
        @DisplayName("KycStatusUpdated event published when KYC level changes")
        void eventPublishedOnLevelUpgrade() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher, atLeast(1)).dispatchKycStatusUpdated(any(), any());
        }

        @Test
        @DisplayName("No event published when KYC level does not change")
        void noEventWhenLevelUnchanged() {
            // borrower already at MIN_KYC and PAN already verified — verifying PAN again won't change level
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            kyc.setPanVerified(true);
            borrower.setKycLevel(KycLevel.MIN_KYC); // already at MIN_KYC

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher, never()).dispatchKycStatusUpdated(any(), any());
        }

        @Test
        @DisplayName("KYC verified notification dispatched on successful verification")
        void kycVerifiedNotificationDispatched() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher).dispatchKycVerified("9876543210", "PAN", KycLevel.MIN_KYC.name());
        }

        @Test
        @DisplayName("KYC verified notification dispatched even when KYC level does not change")
        void kycVerifiedNotificationDispatchedEvenIfLevelUnchanged() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            kyc.setPanVerified(true);
            borrower.setKycLevel(KycLevel.MIN_KYC);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher).dispatchKycVerified("9876543210", "PAN", KycLevel.MIN_KYC.name());
        }

        @Test
        @DisplayName("OTP is marked verified=true after successful verification")
        void otpMarkedVerified() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            assertThat(otp.getVerified()).isTrue();
        }
    }

    // ── getByBorrowerId() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByBorrowerId()")
    class GetByBorrowerId {

        @Test
        @DisplayName("Throws ResourceNotFoundException when borrower does not exist")
        void throwsWhenBorrowerNotFound() {
            when(borrowerRepository.existsById(borrowerId)).thenReturn(false);

            assertThatThrownBy(() -> kycService.getByBorrowerId(borrowerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when KYC record does not exist")
        void throwsWhenKycNotFound() {
            when(borrowerRepository.existsById(borrowerId)).thenReturn(true);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> kycService.getByBorrowerId(borrowerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns KYC response when both borrower and KYC exist")
        void returnsResponseWhenFound() {
            when(borrowerRepository.existsById(borrowerId)).thenReturn(true);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.getByBorrowerId(borrowerId);

            verify(kycMapper).toResponse(any(), any());
        }

        @Test
        @DisplayName("deriveKycLevel returns NO_KYC when neither PAN nor Aadhaar verified")
        void deriveKycLevelNoKyc() {
            kyc.setPanVerified(false);
            kyc.setAadhaarVerified(false);
            when(borrowerRepository.existsById(borrowerId)).thenReturn(true);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.getByBorrowerId(borrowerId);

            verify(kycMapper).toResponse(any(), eq(KycLevel.NO_KYC));
        }

        @Test
        @DisplayName("deriveKycLevel returns FULL_KYC when both PAN and Aadhaar verified")
        void deriveKycLevelFullKyc() {
            kyc.setPanVerified(true);
            kyc.setAadhaarVerified(true);
            when(borrowerRepository.existsById(borrowerId)).thenReturn(true);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.getByBorrowerId(borrowerId);

            verify(kycMapper).toResponse(any(), eq(KycLevel.FULL_KYC));
        }
    }

    // ── initiateKyc() — Aadhaar same-borrower false branch ───────────────────

    @Nested
    @DisplayName("initiateKyc() — Aadhaar same-borrower uniqueness false branch")
    class InitiateKycAadhaarSameBorrower {

        @Test
        @DisplayName("Aadhaar re-submission by same borrower does NOT throw duplicate error")
        void sameOwnerAadhaarResubmissionAllowed() {
            Kyc ownKyc = new Kyc();
            ownKyc.setBorrowerId(borrowerId); // same borrower — false branch of uniqueness check

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByAadhaarNumber("123456789012")).thenReturn(Optional.of(ownKyc));
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            kycService.initiateKyc(borrowerId, buildInitiateRequest("AADHAAR", "123456789012"));

            verify(otpVerificationRepository).save(any());
        }
    }

    // ── initiateKyc() — OTP SMS notification branches ─────────────────────────

    @Nested
    @DisplayName("initiateKyc() — OTP notification branches")
    class InitiateKycNotification {

        @Test
        @DisplayName("No OTP SMS when borrowerForOtp is null (second findById returns empty)")
        void noOtpNotificationWhenBorrowerNull() {
            when(borrowerRepository.findById(borrowerId))
                    .thenReturn(Optional.of(borrower))  // first call (validation)
                    .thenReturn(Optional.empty());        // second call (notification lookup)
            when(kycRepository.findByPanNumber(any())).thenReturn(Optional.empty());
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            kycService.initiateKyc(borrowerId, buildInitiateRequest("PAN", "ABCDE1234F"));

            verify(notificationDispatcher, never()).dispatchOtpGenerated(any(), any(), any());
        }

        @Test
        @DisplayName("No OTP SMS when borrowerForOtp phone is null")
        void noOtpNotificationWhenPhoneNull() {
            borrower.setPhoneNumber(null);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(kycRepository.findByPanNumber(any())).thenReturn(Optional.empty());
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(kycRepository.save(any())).thenReturn(kyc);
            when(otpVerificationRepository.save(any())).thenReturn(new OtpVerification());

            kycService.initiateKyc(borrowerId, buildInitiateRequest("PAN", "ABCDE1234F"));

            verify(notificationDispatcher, never()).dispatchOtpGenerated(any(), any(), any());
        }
    }

    // ── verifyOtp() — OTP SMS notification phone null/blank branches ──────────

    @Nested
    @DisplayName("verifyOtp() — OTP SMS notification phone null/blank branches")
    class VerifyOtpNotificationBranches {

        @Test
        @DisplayName("No expiry SMS when borrower phone is null")
        void noExpiryNotificationWhenPhoneNull() {
            OtpVerification expired = buildOtp("123456", LocalDateTime.now().minusMinutes(15), false, 0);
            borrower.setPhoneNumber(null);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(expired));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("123456", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpExpired(any(), any());
        }

        @Test
        @DisplayName("No max-attempts SMS when borrower phone is blank")
        void noMaxAttemptsNotificationWhenPhoneBlank() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 5);
            borrower.setPhoneNumber("  ");
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpMaxAttemptsExceeded(any(), any());
        }

        @Test
        @DisplayName("No wrong-OTP SMS when borrower phone is null")
        void noWrongOtpNotificationWhenPhoneNull() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            borrower.setPhoneNumber(null);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpFailed(any(), any(), anyInt());
        }

        @Test
        @DisplayName("No kycVerified SMS when borrower phone is null")
        void noKycVerifiedNotificationWhenPhoneNull() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            borrower.setPhoneNumber(null);

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher, never()).dispatchKycVerified(any(), any(), any());
        }

        @Test
        @DisplayName("No expiry SMS when borrower phone is blank")
        void noExpiryNotificationWhenPhoneBlank() {
            OtpVerification expired = buildOtp("123456", LocalDateTime.now().minusMinutes(15), false, 0);
            borrower.setPhoneNumber("  ");
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(expired));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("123456", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpExpired(any(), any());
        }

        @Test
        @DisplayName("No max-attempts SMS when borrower phone is null")
        void noMaxAttemptsNotificationWhenPhoneNull() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 5);
            borrower.setPhoneNumber(null);
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpMaxAttemptsExceeded(any(), any());
        }

        @Test
        @DisplayName("No wrong-OTP SMS when borrower phone is blank")
        void noWrongOtpNotificationWhenPhoneBlank() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            borrower.setPhoneNumber("  ");
            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN"))
                    .thenReturn(Optional.of(otp));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            KycVerifyOtpRequest request = buildVerifyRequest("999999", "PAN");

            assertThatThrownBy(() -> kycService.verifyOtp(borrowerId, request))
                    .isInstanceOf(BusinessException.class);

            verify(notificationDispatcher, never()).dispatchOtpFailed(any(), any(), anyInt());
        }

        @Test
        @DisplayName("No kycVerified SMS when borrower phone is blank")
        void noKycVerifiedNotificationWhenPhoneBlank() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            borrower.setPhoneNumber("  ");

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher, never()).dispatchKycVerified(any(), any(), any());
        }

        @Test
        @DisplayName("oldLevel null ternary: event payload uses 'NONE' when borrower kycLevel is null")
        void oldLevelNullUsesNoneInPayload() {
            OtpVerification otp = buildOtp("123456", LocalDateTime.now().plusMinutes(5), false, 0);
            borrower.setKycLevel(null); // null oldLevel → ternary false branch
            kyc.setPanVerified(false);  // verifying PAN → newLevel=MIN_KYC, differs from null → level changed block

            when(otpVerificationRepository.findLatestUnverified(borrowerId, "PAN")).thenReturn(Optional.of(otp));
            when(otpVerificationRepository.save(any())).thenReturn(otp);
            when(kycRepository.findByBorrowerId(borrowerId)).thenReturn(Optional.of(kyc));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(kycMapper.toResponse(any(), any())).thenReturn(null);

            // Should not throw — oldLevel=null path should be handled gracefully
            kycService.verifyOtp(borrowerId, buildVerifyRequest("123456", "PAN"));

            verify(notificationDispatcher).dispatchKycStatusUpdated(any(), any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OtpVerification buildOtp(String code, LocalDateTime expiresAt, boolean verified, int attempts) {
        OtpVerification otp = new OtpVerification();
        otp.setOtpId(UUID.randomUUID());
        otp.setBorrowerId(borrowerId);
        otp.setDocumentType("PAN");
        otp.setOtpCode(code);
        otp.setExpiresAt(expiresAt);
        otp.setVerified(verified);
        otp.setAttempts(attempts);
        return otp;
    }

    private KycInitiateRequest buildInitiateRequest(String docType, String docValue) {
        KycInitiateRequest req = new KycInitiateRequest();
        req.setDocumentType(docType);
        req.setDocumentValue(docValue);
        return req;
    }

    private KycVerifyOtpRequest buildVerifyRequest(String otp, String documentType) {
        KycVerifyOtpRequest req = new KycVerifyOtpRequest();
        req.setOtp(otp);
        req.setDocumentType(documentType);
        return req;
    }
}
