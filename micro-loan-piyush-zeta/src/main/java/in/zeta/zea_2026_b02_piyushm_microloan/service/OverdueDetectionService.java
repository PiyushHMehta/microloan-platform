package in.zeta.zea_2026_b02_piyushm_microloan.service;

import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scheduled service that runs daily and marks past-due installments as OVERDUE,
 * applies penalty once per installment, and recalculates loan status.
 *
 * Each loan is processed in its own transaction so a single failure doesn't
 * roll back the entire batch.
 */
@Service
@RequiredArgsConstructor
public class OverdueDetectionService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(OverdueDetectionService.class);
    private static final int BATCH_SIZE = 100;
    private static final String FIELD_LOAN_ID = "loanId";

    private final InstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanService loanService;
    private final NotificationDispatcher notificationDispatcher;

    @Value("${app.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Outer loop — NOT transactional. Paginates overdue candidates,
     * groups by loan, and delegates each loan to a separate transaction.
     * Triggered by {@link in.zeta.zea_2026_b02_piyushm_microloan.scheduler.OverdueDetectionScheduler}.
     */
    public void detectAndMarkOverdue() {
        if (!schedulerEnabled) {
            log.info("Overdue detection scheduler is disabled").log();
            return;
        }

        log.info("Overdue detection scheduler started").attr("date", LocalDate.now()).log();

        int pageNum = 0;
        int totalInstallmentsProcessed = 0;
        int totalLoansProcessed = 0;

        Page<Installment> page;
        do {
            page = installmentRepository.findOverdueCandidates(
                    LocalDate.now(), PageRequest.of(pageNum, BATCH_SIZE));

            if (page.isEmpty()) break;

            // Group by loanId — process each loan in its own transaction
            Map<UUID, List<Installment>> byLoan = page.getContent().stream()
                    .collect(Collectors.groupingBy(Installment::getLoanId));

            for (Map.Entry<UUID, List<Installment>> entry : byLoan.entrySet()) {
                try {
                    processOverdueForLoan(entry.getKey(), entry.getValue());
                    totalLoansProcessed++;
                    totalInstallmentsProcessed += entry.getValue().size();
                } catch (Exception e) {
                    // Isolated — one loan failure does not stop the batch
                    log.error("Failed to process overdue", e).attr(FIELD_LOAN_ID, entry.getKey()).attr("error", e.getMessage()).log();
                }
            }

            pageNum++;
        } while (page.hasNext());

        log.info("Overdue detection completed").attr("loans", totalLoansProcessed).attr("installments", totalInstallmentsProcessed).log();
    }

    /**
     * Per-loan transaction — marks installments OVERDUE, applies penalty once,
     * then recalculates loan status.
     */
    @Transactional
    public void processOverdueForLoan(UUID loanId, List<Installment> overdueInstallments) {
        Loan loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null) {
            log.warn("Loan not found during overdue processing").attr(FIELD_LOAN_ID, loanId).log();
            return;
        }

        for (Installment inst : overdueInstallments) {
            inst.setStatus(InstallmentStatus.OVERDUE);

            // Apply penalty once only (idempotent via penaltyApplied flag)
            if (Boolean.FALSE.equals(inst.getPenaltyApplied())) {
                BigDecimal penaltyAmount = inst.getEpiAmount()
                        .multiply(loan.getPenaltyRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                inst.setPenaltyAmount(penaltyAmount);
                inst.setTotalDue(inst.getEpiAmount().add(penaltyAmount));
                inst.setPenaltyApplied(true);

                log.debug("Penalty applied").attr(FIELD_LOAN_ID, loanId).attr("installmentId", inst.getInstallmentId()).attr("penalty", penaltyAmount).log();
            }

            installmentRepository.save(inst);

            log.debug("Installment marked OVERDUE").attr(FIELD_LOAN_ID, loanId).attr("installmentNo", inst.getInstallmentNo()).attr("dueDate", inst.getDueDate()).log();

            // Publish per-installment overdue event
            borrowerRepository.findById(loan.getBorrowerId()).ifPresent(b -> {
                Map<String, Object> overduePayload = new HashMap<>();
                overduePayload.put(FIELD_LOAN_ID, loanId);
                overduePayload.put("borrowerId", b.getBorrowerId());
                overduePayload.put("email", b.getEmail());
                overduePayload.put("installmentNo", inst.getInstallmentNo());
                overduePayload.put("dueDate", inst.getDueDate());
                if (b.getEmail() != null && !b.getEmail().isBlank()) {
                    notificationDispatcher.dispatchInstallmentOverdue(
                        b.getEmail(), inst.getInstallmentNo(), loanId, inst.getDueDate(), overduePayload);
                }
            });
        }

        // Recalculate loan status — updates totalPenaltyAmount, totalPayable, loan.status
        loanService.recalculateStatus(loanId);

        log.info("Overdue processing done").attr(FIELD_LOAN_ID, loanId).attr("installments", overdueInstallments.size()).log();
    }
}
