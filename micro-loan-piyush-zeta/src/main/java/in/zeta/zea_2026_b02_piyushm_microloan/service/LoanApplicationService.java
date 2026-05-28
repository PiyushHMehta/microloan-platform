package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.ApproveRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.RejectRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.LoanApplicationMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanApplicationRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductFrequencyRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(LoanApplicationService.class);
    private static final BigDecimal RBI_INCOME_LIMIT = new BigDecimal("300000");
    private static final String FIELD_BORROWER_ID = "borrowerId";
    private static final String FIELD_APPLICATION_ID = "applicationId";

    @Value("${app.loan.max-emi-to-income-ratio}")
    private BigDecimal maxEmiToIncomeRatio;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanProductFrequencyRepository frequencyRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final LoanService loanService;
    private final LoanApplicationMapper loanApplicationMapper;
    private final NotificationDispatcher notificationDispatcher;

    @Transactional
    public LoanApplicationResponse apply(LoanApplicationCreateRequest dto) {
        Borrower borrower = borrowerRepository.findById(dto.getBorrowerId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BRW_003));
        LoanProduct product = loanProductRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRD_001));

        assertBorrowerEligible(borrower);
        assertProductEligible(product, borrower);
        assertRequestWithinProductLimits(dto, product);
        assertFrequencyAllowed(dto, product);
        assertEpiAffordable(dto, product, borrower);
        assertNoActiveLoan(dto.getBorrowerId());

        LoanApplication app = loanApplicationRepository.save(loanApplicationMapper.toEntity(dto));
        log.info("Loan application created").attr(FIELD_APPLICATION_ID, app.getApplicationId()).log();
        return toResponse(app, null);
    }

    @Transactional
    public LoanApplicationResponse approve(UUID applicationId, ApproveRequest dto) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APP_006));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new InvalidStateException(ErrorCode.APP_007);
        }
        loanRepository.findByApplicationId(applicationId).ifPresent(existing -> {
            throw new DuplicateResourceException(ErrorCode.LOAN_003);
        });

        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(dto.getReviewedBy());
        app = loanApplicationRepository.saveAndFlush(app);

        UUID loanId = loanService.createFromApplication(app).getLoanId();
        log.info("Loan application approved").attr(FIELD_APPLICATION_ID, applicationId).attr("reviewedBy", dto.getReviewedBy()).attr("loanId", loanId).log();
        dispatchApprovalNotification(applicationId, app.getBorrowerId());
        return toResponse(app, loanId);
    }

    @Transactional
    public LoanApplicationResponse reject(UUID applicationId, RejectRequest dto) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APP_006));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new InvalidStateException(ErrorCode.APP_007);
        }

        app.setStatus(ApplicationStatus.REJECTED);
        app.setReviewedBy(dto.getReviewedBy());
        app.setRejectionReason(dto.getRejectionReason());
        loanApplicationRepository.save(app);

        log.info("Loan application rejected").attr(FIELD_APPLICATION_ID, applicationId).attr("reviewedBy", dto.getReviewedBy()).log();
        dispatchRejectionNotification(applicationId, app.getBorrowerId(), dto.getRejectionReason());
        return toResponse(app, null);
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getById(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APP_006));

        UUID loanId = loanRepository.findByApplicationId(applicationId)
                .map(Loan::getLoanId).orElse(null);

        return toResponse(app, loanId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LoanApplicationResponse> list(Map<String, String> filters, Pageable pageable) {

        Specification<LoanApplication> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String status = filters.get("status");
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), ApplicationStatus.valueOf(status.toUpperCase())));
            }

            String borrowerId = filters.get(FIELD_BORROWER_ID);
            if (borrowerId != null && !borrowerId.isBlank()) {
                predicates.add(cb.equal(root.get(FIELD_BORROWER_ID), UUID.fromString(borrowerId)));
            }

            String productId = filters.get("productId");
            if (productId != null && !productId.isBlank()) {
                predicates.add(cb.equal(root.get("productId"), UUID.fromString(productId)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LoanApplication> page = loanApplicationRepository.findAll(spec, pageable);

        return PagedResponse.<LoanApplicationResponse>builder()
                .content(page.getContent().stream().map(app -> toResponse(app, null)).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private void assertBorrowerEligible(Borrower borrower) {
        if (!borrower.getIsActive()) {
            throw new BusinessException(ErrorCode.APP_008);
        }
        if (borrower.getAnnualHouseholdIncome().compareTo(RBI_INCOME_LIMIT) > 0) {
            throw new BusinessException(ErrorCode.APP_010);
        }
    }

    private void assertProductEligible(LoanProduct product, Borrower borrower) {
        if (!product.getIsActive()) {
            throw new BusinessException(ErrorCode.APP_009);
        }
        if (!borrower.getKycLevel().meetsRequirement(product.getMinKycLevel())) {
            throw new BusinessException(ErrorCode.APP_004);
        }
    }

    private void assertRequestWithinProductLimits(LoanApplicationCreateRequest dto, LoanProduct product) {
        if (dto.getRequestedAmount().compareTo(product.getMinPrincipal()) < 0 ||
                dto.getRequestedAmount().compareTo(product.getMaxPrincipal()) > 0) {
            throw new BusinessException(ErrorCode.APP_001);
        }
        if (dto.getRequestedTenureMonths() < product.getMinTenureMonths() ||
                dto.getRequestedTenureMonths() > product.getMaxTenureMonths()) {
            throw new BusinessException(ErrorCode.APP_002);
        }
    }

    private void assertFrequencyAllowed(LoanApplicationCreateRequest dto, LoanProduct product) {
        List<LoanProductFrequency> allowedFreqs = frequencyRepository
                .findByLoanProductProductId(product.getProductId());
        boolean freqAllowed = allowedFreqs.stream()
                .anyMatch(f -> f.getFrequency() == dto.getRepaymentFrequency());
        if (!freqAllowed) {
            throw new BusinessException(ErrorCode.APP_003);
        }
    }

    private void assertEpiAffordable(LoanApplicationCreateRequest dto, LoanProduct product, Borrower borrower) {
        BigDecimal totalInterest = dto.getRequestedAmount()
                .multiply(product.getInterestRate())
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(dto.getRequestedTenureMonths()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = dto.getRequestedAmount().add(totalInterest);
        int numInstallments = computeInstallmentCount(dto.getRequestedTenureMonths(), dto.getRepaymentFrequency());
        BigDecimal epiAmount = totalAmount.divide(BigDecimal.valueOf(numInstallments), 2, RoundingMode.HALF_UP);

        BigDecimal monthlyBurden = switch (dto.getRepaymentFrequency()) {
            case MONTHLY -> epiAmount;
            case BIWEEKLY -> epiAmount.multiply(BigDecimal.valueOf(2));
            case WEEKLY -> epiAmount.multiply(BigDecimal.valueOf(4));
        };

        if (monthlyBurden.compareTo(borrower.getMonthlyIncome().multiply(maxEmiToIncomeRatio)) > 0) {
            throw new BusinessException(ErrorCode.APP_005);
        }
    }

    private void assertNoActiveLoan(UUID borrowerId) {
        if (loanRepository.existsByBorrowerIdAndStatusIn(
                borrowerId,
                List.of(LoanStatus.KFS_PENDING, LoanStatus.ACTIVE, LoanStatus.OVERDUE))) {
            throw new BusinessException(ErrorCode.APP_011);
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private void dispatchApprovalNotification(UUID applicationId, UUID borrowerId) {
        borrowerRepository.findById(borrowerId).ifPresent(b -> {
            if (b.getEmail() == null || b.getEmail().isBlank()) return;
            Map<String, Object> payload = buildApplicationPayload(applicationId, borrowerId, b.getEmail());
            notificationDispatcher.dispatchLoanApplicationApproved(b.getEmail(), applicationId, payload);
        });
    }

    private void dispatchRejectionNotification(UUID applicationId, UUID borrowerId, String rejectionReason) {
        borrowerRepository.findById(borrowerId).ifPresent(b -> {
            if (b.getEmail() == null || b.getEmail().isBlank()) return;
            Map<String, Object> payload = buildApplicationPayload(applicationId, borrowerId, b.getEmail());
            payload.put("rejectionReason", rejectionReason);
            notificationDispatcher.dispatchLoanApplicationRejected(b.getEmail(), applicationId, rejectionReason, payload);
        });
    }

    private Map<String, Object> buildApplicationPayload(UUID applicationId, UUID borrowerId, String email) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_APPLICATION_ID, applicationId);
        payload.put(FIELD_BORROWER_ID, borrowerId);
        payload.put("email", email);
        return payload;
    }

    // ── Other helpers ─────────────────────────────────────────────────────────

    private int computeInstallmentCount(int tenureMonths, RepaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> tenureMonths;
            case BIWEEKLY -> tenureMonths * 2;
            case WEEKLY -> tenureMonths * 4;
        };
    }

    private LoanApplicationResponse toResponse(LoanApplication app, UUID loanId) {
        return loanApplicationMapper.toResponse(app, loanId);
    }
}
