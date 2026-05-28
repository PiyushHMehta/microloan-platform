package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.Gender;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.BorrowerMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BorrowerService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(BorrowerService.class);

    private static final String FIELD_BORROWER_ID = "borrowerId";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_FULL_NAME = "fullName";
    private static final String FIELD_KYC_LEVEL = "kycLevel";

    private final BorrowerRepository borrowerRepository;
    private final BorrowerMapper borrowerMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final EncryptionService encryptionService;

    @Transactional
    public BorrowerResponse create(BorrowerCreateRequest dto) {
        log.debug("Creating borrower").attr("phone", dto.getPhoneNumber()).log();

        Borrower borrower = borrowerMapper.toEntity(dto);

        try {
            borrower = borrowerRepository.save(borrower);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(ErrorCode.BRW_002);
        }

        log.info("Borrower created").attr(FIELD_BORROWER_ID, borrower.getBorrowerId()).log();

        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_BORROWER_ID, borrower.getBorrowerId());
        payload.put(FIELD_EMAIL, borrower.getEmail());
        payload.put(FIELD_FULL_NAME, borrower.getFullName());

        if (borrower.getEmail() != null && !borrower.getEmail().isBlank()) {
            notificationDispatcher.dispatchBorrowerRegistered(
                borrower.getEmail(), borrower.getFullName(), borrower.getBorrowerId(), payload);
        }

        return borrowerMapper.toResponse(borrower);
    }

    @Transactional
    public BorrowerResponse update(UUID borrowerId, BorrowerUpdateRequest dto) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BRW_003));

        if (!borrower.getIsActive()) {
            throw new BusinessException(ErrorCode.BRW_005);
        }

        borrowerMapper.updateEntity(borrower, dto);
        borrower = borrowerRepository.save(borrower);

        log.info("Borrower updated").attr(FIELD_BORROWER_ID, borrowerId).log();

        return borrowerMapper.toResponse(borrower);
    }

    @Transactional(readOnly = true)
    public BorrowerResponse getById(UUID borrowerId) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BRW_003));

        return borrowerMapper.toResponse(borrower);
    }

    // Encrypted fields (fullName, phoneNumber, email, city, state, pincode) cannot be
    // partially searched via LIKE because values are stored as AES ciphertext. Only
    // enum and boolean fields remain filterable.

        // Only equality filtering allowed for encrypted string fields
        private static final Set<String> STRING_FIELDS = Set.of(FIELD_FULL_NAME, FIELD_EMAIL, "phoneNumber", "city", "state", "pincode");
        private static final Set<String> ENUM_FIELDS = Set.of(FIELD_KYC_LEVEL, "gender");
        private static final Set<String> BOOLEAN_FIELDS = Set.of("isActive");
        private static final Set<String> FILTERABLE_FIELDS = Set.of(FIELD_FULL_NAME, FIELD_EMAIL, "phoneNumber", "city", "state", "pincode", FIELD_KYC_LEVEL, "isActive", "gender");
        private static final Set<String> PAGINATION_PARAMS = Set.of("page", "size", "sort");

    @Transactional(readOnly = true)
    public PagedResponse<BorrowerResponse> list(Map<String, String> filters, Pageable pageable) {


        Specification<Borrower> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            filters.forEach((key, value) -> {
                if (PAGINATION_PARAMS.contains(key) || value == null || value.isBlank()) return;
                if (!FILTERABLE_FIELDS.contains(key)) return;

                // Only equality filtering allowed for encrypted fields
                if (STRING_FIELDS.contains(key)) {
                    String encryptedValue = encryptionService.encrypt(value);
                    predicates.add(cb.equal(root.get(key), encryptedValue));
                } else if (ENUM_FIELDS.contains(key)) {
                    predicates.add(buildEnumPredicate(root, cb, key, value));
                } else if (BOOLEAN_FIELDS.contains(key)) {
                    predicates.add(cb.equal(root.get(key), Boolean.parseBoolean(value)));
                }
            });
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Borrower> page = borrowerRepository.findAll(spec, pageable);

        return PagedResponse.<BorrowerResponse>builder()
                .content(page.getContent().stream().map(borrowerMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private Predicate buildEnumPredicate(Root<Borrower> root, CriteriaBuilder cb, String key, String value) {
        if (FIELD_KYC_LEVEL.equals(key)) {
            return cb.equal(root.get(key), KycLevel.valueOf(value.toUpperCase()));
        }
        return cb.equal(root.get(key), Gender.valueOf(value.toUpperCase()));
    }
}
