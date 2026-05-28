package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.RepaymentScheduleEntry;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProduct;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.LoanMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(LoanService.class);
    private static final String FIELD_LOAN_ID = "loanId";
    private static final String FIELD_BORROWER_ID = "borrowerId";
    private static final String FIELD_EMAIL = "email";

    private final LoanRepository loanRepository;
    private final LoanProductRepository loanProductRepository;
    private final InstallmentRepository installmentRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanMapper loanMapper;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Internal — called from LoanApplicationService.approve().
     * Creates a Loan in KFS_PENDING with computed terms and KFS snapshot.
     */
    @Transactional
    public LoanResponse createFromApplication(LoanApplication application) {
        LoanProduct product = loanProductRepository.findById(application.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRD_001));

        LoanTerms terms = computeLoanTerms(application, product);
        Loan loan = persistLoanWithKfsSnapshot(application, terms);

        log.info("Loan created").attr("applicationId", application.getApplicationId())
                .attr(FIELD_LOAN_ID, loan.getLoanId()).attr("principal", terms.principal)
                .attr("totalAmount", terms.totalAmount).attr("epi", terms.epiAmount).log();

        dispatchKfsNotification(loan);
        return toResponse(loan);
    }

    private LoanTerms computeLoanTerms(LoanApplication application, LoanProduct product) {
        BigDecimal principal = application.getRequestedAmount();
        BigDecimal interestRate = product.getInterestRate();
        BigDecimal penaltyRate = product.getPenaltyRate() != null ? product.getPenaltyRate() : BigDecimal.ZERO;
        int tenureMonths = application.getRequestedTenureMonths();
        RepaymentFrequency frequency = application.getRepaymentFrequency();

        BigDecimal totalInterest = principal
                .multiply(interestRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(tenureMonths))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = principal.add(totalInterest);
        int numInstallments = computeInstallmentCount(tenureMonths, frequency);
        BigDecimal epiAmount = totalAmount.divide(BigDecimal.valueOf(numInstallments), 2, RoundingMode.HALF_UP);
        BigDecimal lastEpi = totalAmount.subtract(epiAmount.multiply(BigDecimal.valueOf(numInstallments - 1L)));

        return new LoanTerms(principal, interestRate, penaltyRate, tenureMonths, frequency,
                totalInterest, totalAmount, numInstallments, epiAmount, lastEpi);
    }

    private Loan persistLoanWithKfsSnapshot(LoanApplication application, LoanTerms terms) {
        Loan loan = Loan.builder()
                .borrowerId(application.getBorrowerId())
                .applicationId(application.getApplicationId())
                .productId(application.getProductId())
                .principalAmount(terms.principal)
                .interestRate(terms.interestRate)
                .penaltyRate(terms.penaltyRate)
                .tenureMonths(terms.tenureMonths)
                .totalAmount(terms.totalAmount)
                .totalPenaltyAmount(BigDecimal.ZERO)
                .totalPayable(terms.totalAmount)
                .totalPaid(BigDecimal.ZERO)
                .epiAmount(terms.epiAmount)
                .repaymentFrequency(terms.frequency)
                .status(LoanStatus.KFS_PENDING)
                .build();
        loan = loanRepository.save(loan);

        loan.setKfsSnapshot(buildKfsSnapshot(loan, terms));
        loan.setKfsGeneratedAt(LocalDateTime.now());
        return loanRepository.saveAndFlush(loan);
    }

    private KfsSnapshot buildKfsSnapshot(Loan loan, LoanTerms terms) {
        List<RepaymentScheduleEntry> schedule = buildKfsSchedule(terms);
        String borrowerName = borrowerRepository.findById(loan.getBorrowerId())
                .map(b -> b.getFullName()).orElse(null);

        return KfsSnapshot.builder()
                .loanId(loan.getLoanId())
                .borrowerId(loan.getBorrowerId())
                .borrowerName(borrowerName)
                .principal(terms.principal)
                .interestRate(terms.interestRate)
                .totalInterest(terms.totalInterest)
                .totalAmount(terms.totalAmount)
                .epiAmount(terms.epiAmount)
                .repaymentFrequency(terms.frequency)
                .tenureMonths(terms.tenureMonths)
                .numInstallments(terms.numInstallments)
                .penaltyRate(terms.penaltyRate)
                .repaymentSchedule(schedule)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private List<RepaymentScheduleEntry> buildKfsSchedule(LoanTerms terms) {
        List<RepaymentScheduleEntry> schedule = new ArrayList<>();
        LocalDate dueDate = LocalDate.now();
        for (int i = 1; i <= terms.numInstallments; i++) {
            dueDate = advanceDate(dueDate, terms.frequency);
            BigDecimal amt = (i == terms.numInstallments) ? terms.lastEpi : terms.epiAmount;
            schedule.add(RepaymentScheduleEntry.builder()
                    .installmentNo(i).dueDate(dueDate).epiAmount(amt).build());
        }
        return schedule;
    }

    private void dispatchKfsNotification(Loan loan) {
        borrowerRepository.findById(loan.getBorrowerId()).ifPresent(b -> {
            KfsResponse kfsResponse = getKfs(loan.getLoanId());
            Map<String, Object> kfsPayload = buildKfsPayload(loan, b.getEmail(), kfsResponse);
            notificationDispatcher.dispatchKfsGenerated(b.getEmail(), kfsResponse, kfsPayload);
        });
    }

    private Map<String, Object> buildKfsPayload(Loan loan, String email, KfsResponse kfs) {
        List<Map<String, Object>> scheduleList = kfs.getRepaymentSchedule().stream()
                .map(e -> Map.<String, Object>of(
                        "installmentNo", e.getInstallmentNo(),
                        "dueDate", e.getDueDate().toString(),
                        "epiAmount", e.getEpiAmount().toPlainString()))
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_LOAN_ID, loan.getLoanId().toString());
        payload.put(FIELD_BORROWER_ID, loan.getBorrowerId().toString());
        payload.put(FIELD_EMAIL, email);
        payload.put("principal", kfs.getPrincipal().toPlainString());
        payload.put("interestRate", kfs.getInterestRate().toPlainString());
        payload.put("totalInterest", kfs.getTotalInterest().toPlainString());
        payload.put("totalAmount", kfs.getTotalAmount().toPlainString());
        payload.put("epiAmount", kfs.getEpiAmount().toPlainString());
        payload.put("repaymentFrequency", kfs.getRepaymentFrequency().name());
        payload.put("tenureMonths", kfs.getTenureMonths());
        payload.put("numInstallments", kfs.getNumInstallments());
        payload.put("penaltyRate", kfs.getPenaltyRate().toPlainString());
        payload.put("repaymentSchedule", scheduleList);
        return payload;
    }

    /** Value object carrying computed loan terms to avoid long parameter lists. */
    private record LoanTerms(
            BigDecimal principal, BigDecimal interestRate, BigDecimal penaltyRate,
            int tenureMonths, RepaymentFrequency frequency,
            BigDecimal totalInterest, BigDecimal totalAmount,
            int numInstallments, BigDecimal epiAmount, BigDecimal lastEpi) {
    }

    @Transactional(readOnly = true)
    public KfsResponse getKfs(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));

        KfsSnapshot snap = loan.getKfsSnapshot();
        if (snap == null) {
            throw new ResourceNotFoundException(ErrorCode.LOAN_001);
        }

        return KfsResponse.builder()
                .loanId(loan.getLoanId())
                .borrowerName(snap.getBorrowerName())
                .principal(snap.getPrincipal())
                .interestRate(snap.getInterestRate())
                .totalInterest(snap.getTotalInterest())
                .totalAmount(snap.getTotalAmount())
                .epiAmount(snap.getEpiAmount())
                .repaymentFrequency(snap.getRepaymentFrequency())
                .tenureMonths(snap.getTenureMonths())
                .numInstallments(snap.getNumInstallments())
                .penaltyRate(snap.getPenaltyRate())
                .repaymentSchedule(snap.getRepaymentSchedule())
                .status(loan.getStatus() == LoanStatus.KFS_PENDING ? "PENDING_ACCEPTANCE" : "ACCEPTED")
                .build();
    }

    @Transactional
    public LoanResponse acceptKfs(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));

        if (loan.getStatus() != LoanStatus.KFS_PENDING) {
            throw new InvalidStateException(ErrorCode.LOAN_002);
        }

        // Update loan
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setKfsAcknowledgedAt(LocalDateTime.now());
        loan.setDisbursedAt(LocalDateTime.now());

        // Generate installment rows from KFS snapshot schedule
        KfsSnapshot snap = loan.getKfsSnapshot();
        List<Installment> installments = new ArrayList<>();
        for (RepaymentScheduleEntry entry : snap.getRepaymentSchedule()) {
            Installment inst = Installment.builder()
                    .loanId(loan.getLoanId())
                    .installmentNo(entry.getInstallmentNo())
                    .dueDate(entry.getDueDate())
                    .epiAmount(entry.getEpiAmount())
                    .penaltyAmount(BigDecimal.ZERO)
                    .totalDue(entry.getEpiAmount())
                    .amountPaid(BigDecimal.ZERO)
                    .status(InstallmentStatus.PENDING)
                    .penaltyApplied(false)
                    .build();
            installments.add(inst);
        }
        installmentRepository.saveAll(installments);

        loan = loanRepository.save(loan);

        log.info("KFS accepted").attr(FIELD_LOAN_ID, loanId).attr("installments", installments.size()).log();

        final Loan acceptedLoan = loan;
        borrowerRepository.findById(acceptedLoan.getBorrowerId()).ifPresent(b -> {
            Map<String, Object> issuedPayload = new HashMap<>();
            issuedPayload.put(FIELD_LOAN_ID, acceptedLoan.getLoanId());
            issuedPayload.put(FIELD_BORROWER_ID, acceptedLoan.getBorrowerId());
            issuedPayload.put(FIELD_EMAIL, b.getEmail());
            issuedPayload.put("loanAmount", acceptedLoan.getPrincipalAmount());
            // Convert LocalDateTime to ISO string for serialization
            LocalDateTime disbursedAt = acceptedLoan.getDisbursedAt();
            issuedPayload.put("disbursedAt", disbursedAt != null ? disbursedAt.toString() : null);
            notificationDispatcher.dispatchLoanIssued(
                    b.getEmail(), acceptedLoan.getLoanId(),
                    acceptedLoan.getPrincipalAmount(), disbursedAt, issuedPayload);
        });

        return toResponse(acceptedLoan);
    }

    @Transactional(readOnly = true)
    public LoanResponse getById(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));
        return toResponse(loan);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LoanResponse> list(Map<String, String> filters, Pageable pageable) {

        Specification<Loan> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String status = filters.get("status");
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), LoanStatus.valueOf(status.toUpperCase())));
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

        Page<Loan> page = loanRepository.findAll(spec, pageable);

        return PagedResponse.<LoanResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    /**
     * Internal — called after repayments and overdue detection.
     * Recalculates loan status, totalPenaltyAmount, totalPayable from installments.
     */
    @Transactional
    public void recalculateStatus(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));
        List<Installment> installments = installmentRepository.findByLoanId(loanId);

        recalculatePenaltyTotals(loan, installments);

        LoanStatus newStatus = deriveNewLoanStatus(loan, installments);
        if (newStatus != loan.getStatus()) {
            applyStatusTransition(loan, newStatus);
        }

        loanRepository.save(loan);
    }

    private LoanStatus deriveNewLoanStatus(Loan loan, List<Installment> installments) {
        if (loan.getStatus() == LoanStatus.KFS_PENDING) {
            return loan.getStatus();
        }
        boolean allPaid = !installments.isEmpty() && installments.stream()
                .allMatch(i -> i.getStatus() == InstallmentStatus.PAID);
        boolean anyOverdue = installments.stream()
                .anyMatch(i -> i.getStatus() == InstallmentStatus.OVERDUE);
        if (allPaid) return LoanStatus.CLOSED;
        if (anyOverdue) return LoanStatus.OVERDUE;
        return LoanStatus.ACTIVE;
    }

    private void recalculatePenaltyTotals(Loan loan, List<Installment> installments) {
        BigDecimal totalPenalty = installments.stream()
                .map(Installment::getPenaltyAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        loan.setTotalPenaltyAmount(totalPenalty);
        loan.setTotalPayable(loan.getTotalAmount().add(totalPenalty));
    }

    private void applyStatusTransition(Loan loan, LoanStatus newStatus) {
        LoanStatus oldStatus = loan.getStatus();
        loan.setStatus(newStatus);
        log.info("Loan status changed").attr(FIELD_LOAN_ID, loan.getLoanId()).attr("oldStatus", oldStatus).attr("newStatus", newStatus).log();
        if (newStatus == LoanStatus.CLOSED) {
            dispatchLoanClosedNotification(loan);
        }
    }

    private void dispatchLoanClosedNotification(Loan loan) {
        final UUID loanId = loan.getLoanId();
        final BigDecimal totalPaid = loan.getTotalPaid();
        borrowerRepository.findById(loan.getBorrowerId()).ifPresent(b -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put(FIELD_LOAN_ID, loanId);
            payload.put(FIELD_BORROWER_ID, b.getBorrowerId());
            payload.put(FIELD_EMAIL, b.getEmail());
            payload.put("totalPaid", totalPaid);
            notificationDispatcher.dispatchLoanClosed(b.getEmail(), loanId, totalPaid, payload);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int computeInstallmentCount(int tenureMonths, RepaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> tenureMonths;
            case BIWEEKLY -> tenureMonths * 2;
            case WEEKLY -> tenureMonths * 4;
        };
    }

    private LocalDate advanceDate(LocalDate from, RepaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> from.plusMonths(1);
            case BIWEEKLY -> from.plusDays(14);
            case WEEKLY -> from.plusDays(7);
        };
    }

    private LoanResponse toResponse(Loan loan) {
        return loanMapper.toResponse(loan);
    }
}
