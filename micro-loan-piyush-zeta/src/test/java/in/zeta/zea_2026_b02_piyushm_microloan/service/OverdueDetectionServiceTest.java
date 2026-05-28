package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverdueDetectionService Tests")
class OverdueDetectionServiceTest {

    @Mock private InstallmentRepository installmentRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private BorrowerRepository borrowerRepository;
    @Mock private LoanService loanService;
    @Mock private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private OverdueDetectionService overdueDetectionService;

    private UUID loanId;
    private Loan loan;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();

        loan = new Loan();
        loan.setLoanId(loanId);
        loan.setPenaltyRate(new BigDecimal("2.00"));

        ReflectionTestUtils.setField(overdueDetectionService, "schedulerEnabled", true);
    }

    // ── Penalty calculation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Penalty calculation")
    class PenaltyCalculation {

        @Test
        @DisplayName("Penalty = epiAmount × (penaltyRate / 100), rounded HALF_UP 2dp")
        void penaltyCalculatedCorrectly() {
            Installment inst = buildInstallment(false, new BigDecimal("5000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            // 5000 * 2/100 = 100.00
            assertThat(inst.getPenaltyAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(inst.getTotalDue()).isEqualByComparingTo(new BigDecimal("5100.00"));
        }

        @Test
        @DisplayName("Penalty rounding: 1333.33 × 2% → 26.67 (HALF_UP)")
        void penaltyRoundingHalfUp() {
            Installment inst = buildInstallment(false, new BigDecimal("1333.33"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            // 1333.33 * 2 / 100 = 26.6666 → rounds to 26.67
            assertThat(inst.getPenaltyAmount()).isEqualByComparingTo(new BigDecimal("26.67"));
        }

        @Test
        @DisplayName("Zero penalty rate results in zero penalty")
        void zeroPenaltyRate() {
            loan.setPenaltyRate(BigDecimal.ZERO);
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getPenaltyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(inst.getTotalDue()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("Multiple installments each get their own penalty applied")
        void multipleInstallmentsEachGetPenalty() {
            Installment inst1 = buildInstallment(false, new BigDecimal("1000.00"));
            Installment inst2 = buildInstallment(false, new BigDecimal("2000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst1, inst2));

            assertThat(inst1.getPenaltyAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(inst2.getPenaltyAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        }
    }

    // ── Penalty idempotency ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Penalty idempotency")
    class PenaltyIdempotency {

        @Test
        @DisplayName("Penalty NOT re-applied when penaltyApplied=true")
        void penaltyNotAppliedTwice() {
            Installment inst = buildInstallment(true, new BigDecimal("5000.00"));
            inst.setPenaltyAmount(new BigDecimal("100.00"));
            inst.setTotalDue(new BigDecimal("5100.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getPenaltyAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(inst.getTotalDue()).isEqualByComparingTo(new BigDecimal("5100.00"));
        }

        @Test
        @DisplayName("penaltyApplied flag is set to true after first application")
        void penaltyAppliedFlagSetTrue() {
            Installment inst = buildInstallment(false, new BigDecimal("5000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getPenaltyApplied()).isTrue();
        }

        @Test
        @DisplayName("penaltyApplied flag stays true on second run (no double-flag)")
        void penaltyAppliedFlagRemainsTrue() {
            Installment inst = buildInstallment(true, new BigDecimal("5000.00"));
            inst.setPenaltyAmount(new BigDecimal("100.00"));
            inst.setTotalDue(new BigDecimal("5100.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getPenaltyApplied()).isTrue();
        }
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("PENDING installment becomes OVERDUE")
        void pendingBecomesOverdue() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            inst.setStatus(InstallmentStatus.PENDING);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
        }

        @Test
        @DisplayName("PARTIAL installment becomes OVERDUE")
        void partialBecomesOverdue() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            inst.setStatus(InstallmentStatus.PARTIAL);
            inst.setAmountPaid(new BigDecimal("300.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
        }
    }

    // ── Side effects ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Side effects after processing")
    class SideEffects {

        @Test
        @DisplayName("recalculateStatus called after processing overdue installments")
        void recalculateStatusCalled() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(loanService).recalculateStatus(loanId);
        }

        @Test
        @DisplayName("installmentRepository.saveAll called with updated installments")
        void installmentsSaved() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(installmentRepository).save(any());
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Loan not found → skips processing without throwing")
        void loanNotFoundSkipsGracefully() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(installmentRepository, never()).saveAll(any());
            verify(loanService, never()).recalculateStatus(any());
        }

        @Test
        @DisplayName("Empty installment list → no processing occurs")
        void emptyInstallmentList() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.processOverdueForLoan(loanId, List.of());

            verify(installmentRepository, never()).save(any());
            verify(loanService).recalculateStatus(loanId);
        }
    }

    // ── detectAndMarkOverdue() ────────────────────────────────────────────────

    @Nested
    @DisplayName("detectAndMarkOverdue()")
    class DetectAndMarkOverdue {

        @Test
        @DisplayName("Scheduler disabled → returns immediately without querying")
        void schedulerDisabledDoesNothing() {
            ReflectionTestUtils.setField(overdueDetectionService, "schedulerEnabled", false);

            overdueDetectionService.detectAndMarkOverdue();

            verify(installmentRepository, never()).findOverdueCandidates(any(), any());
        }

        @Test
        @DisplayName("Empty first page → loop exits without processing any loan")
        void emptyFirstPageDoesNothing() {
            when(installmentRepository.findOverdueCandidates(any(LocalDate.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            overdueDetectionService.detectAndMarkOverdue();

            verify(loanRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Normal flow: installments grouped by loan and processOverdueForLoan invoked")
        void normalFlowProcessesEachLoan() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            when(installmentRepository.findOverdueCandidates(any(LocalDate.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(inst)))
                    .thenReturn(new PageImpl<>(Collections.emptyList())); // stop loop
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            overdueDetectionService.detectAndMarkOverdue();

            verify(loanRepository, atLeastOnce()).findById(loanId);
            verify(loanService).recalculateStatus(loanId);
        }

        @Test
        @DisplayName("Exception in one loan's processing is caught and does not abort batch")
        void exceptionInLoanProcessingDoesNotAbortBatch() {
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));
            when(installmentRepository.findOverdueCandidates(any(LocalDate.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(inst)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(loanRepository.findById(loanId)).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            overdueDetectionService.detectAndMarkOverdue();

            verify(loanRepository, atLeastOnce()).findById(loanId);
        }

        @Test
        @DisplayName("Multi-page loop: processes second page when first page hasNext()=true")
        void multiPageLoopProcessesBothPages() {
            // Two distinct loans — one per page
            UUID loanId2 = UUID.randomUUID();
            Loan loan2 = new Loan();
            loan2.setLoanId(loanId2);
            loan2.setPenaltyRate(new BigDecimal("2.00"));

            Installment inst1 = buildInstallment(false, new BigDecimal("1000.00"));
            Installment inst2 = buildInstallment(false, new BigDecimal("2000.00"));
            inst2.setLoanId(loanId2);

            // First page: 1 item, total=2 → hasNext()=true
            // Second page: 1 item, total=2, page=1 → hasNext()=false
            when(installmentRepository.findOverdueCandidates(any(LocalDate.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(inst1), PageRequest.of(0, 1), 2))
                    .thenReturn(new PageImpl<>(List.of(inst2), PageRequest.of(1, 1), 2));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(loanRepository.findById(loanId2)).thenReturn(Optional.of(loan2));

            overdueDetectionService.detectAndMarkOverdue();

            verify(loanService).recalculateStatus(loanId);
            verify(loanService).recalculateStatus(loanId2);
        }
    }

    // ── Notification dispatch ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Notification dispatch in processOverdueForLoan()")
    class NotificationDispatch {

        @Test
        @DisplayName("Overdue notification dispatched when borrower has valid email")
        void overdueNotificationDispatchedWhenBorrowerFound() {
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(UUID.randomUUID());
            borrower.setEmail("overdue@example.com");

            loan.setBorrowerId(borrower.getBorrowerId());
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(borrowerRepository.findById(loan.getBorrowerId())).thenReturn(Optional.of(borrower));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(notificationDispatcher).dispatchInstallmentOverdue(
                    eq("overdue@example.com"), anyInt(), eq(loanId), any(), any());
        }

        @Test
        @DisplayName("No overdue notification when borrower email is null")
        void overdueNotificationNotDispatchedWhenEmailNull() {
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(UUID.randomUUID());
            borrower.setEmail(null);

            loan.setBorrowerId(borrower.getBorrowerId());
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(borrowerRepository.findById(loan.getBorrowerId())).thenReturn(Optional.of(borrower));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(notificationDispatcher, never()).dispatchInstallmentOverdue(any(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("No overdue notification when borrower email is blank")
        void overdueNotificationNotDispatchedWhenEmailBlank() {
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(UUID.randomUUID());
            borrower.setEmail("  ");

            loan.setBorrowerId(borrower.getBorrowerId());
            Installment inst = buildInstallment(false, new BigDecimal("1000.00"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(borrowerRepository.findById(loan.getBorrowerId())).thenReturn(Optional.of(borrower));

            overdueDetectionService.processOverdueForLoan(loanId, List.of(inst));

            verify(notificationDispatcher, never()).dispatchInstallmentOverdue(any(), anyInt(), any(), any(), any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Installment buildInstallment(boolean penaltyApplied, BigDecimal epiAmount) {
        Installment inst = new Installment();
        inst.setInstallmentId(UUID.randomUUID());
        inst.setLoanId(loanId);
        inst.setInstallmentNo(1);
        inst.setStatus(InstallmentStatus.PENDING);
        inst.setEpiAmount(epiAmount);
        inst.setTotalDue(epiAmount);
        inst.setAmountPaid(BigDecimal.ZERO);
        inst.setPenaltyAmount(BigDecimal.ZERO);
        inst.setPenaltyApplied(penaltyApplied);
        return inst;
    }
}
