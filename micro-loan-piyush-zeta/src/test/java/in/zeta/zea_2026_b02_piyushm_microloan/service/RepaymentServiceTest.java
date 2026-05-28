package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Repayment;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentMode;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.RepaymentMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.RepaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepaymentService Tests")
class RepaymentServiceTest {

    @Mock private RepaymentRepository repaymentRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private BorrowerRepository borrowerRepository;
    @Mock private LoanService loanService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private RepaymentMapper repaymentMapper;

    @InjectMocks
    private RepaymentService repaymentService;

    private UUID loanId;
    private Loan activeLoan;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
        activeLoan = buildLoan(LoanStatus.ACTIVE, new BigDecimal("10000"), BigDecimal.ZERO);
    }

    // ── Status guard ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Loan status guard")
    class StatusGuard {

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenLoanNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
            var request = buildRequest(new BigDecimal("100"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when loan is CLOSED")
        void throwsOnClosedLoan() {
            Loan closed = buildLoan(LoanStatus.CLOSED, BigDecimal.ZERO, BigDecimal.ZERO);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(closed));
            var request = buildRequest(new BigDecimal("100"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when loan is KFS_PENDING")
        void throwsOnKfsPendingLoan() {
            Loan pending = buildLoan(LoanStatus.KFS_PENDING, BigDecimal.ZERO, BigDecimal.ZERO);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(pending));
            var request = buildRequest(new BigDecimal("100"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("Accepts payment when loan is OVERDUE (not closed or pending)")
        void acceptsPaymentOnOverdueLoan() {
            Loan overdue = buildLoan(LoanStatus.OVERDUE, new BigDecimal("5000"), BigDecimal.ZERO);
            Installment inst = buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("5000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(overdue));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(overdue, new BigDecimal("5000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("5000")));

            verify(repaymentRepository).saveAndFlush(any());
        }
    }

    // ── Overpayment guard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Overpayment guard")
    class OverpaymentGuard {

        @Test
        @DisplayName("Throws BusinessException when payment exceeds remaining balance")
        void throwsOnOverpayment() {
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("500"), BigDecimal.ZERO);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            var request = buildRequest(new BigDecimal("600"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Exact payment matching remaining balance is allowed")
        void exactPaymentIsAllowed() {
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(repaymentRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("Overpayment guard accounts for partially paid installments")
        void overpaymentGuardAccountsForPartialPayment() {
            // inst has totalDue=1000, amountPaid=600 → remaining=400
            Installment inst = buildInstallment(InstallmentStatus.PARTIAL, new BigDecimal("1000"), new BigDecimal("600"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            // attempt to pay 500 > 400 remaining
            var request = buildRequest(new BigDecimal("500"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── FIFO allocation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("FIFO allocation")
    class FifoAllocation {

        @Test
        @DisplayName("Exact payment fully pays first installment, second stays PENDING")
        void exactPaymentPaysFirstInstallment() {
            Installment inst1 = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            Installment inst2 = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst1, inst2));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            assertThat(inst1.getStatus()).isEqualTo(InstallmentStatus.PAID);
            assertThat(inst1.getAmountPaid()).isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(inst2.getStatus()).isEqualTo(InstallmentStatus.PENDING);
            assertThat(inst2.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Partial payment marks PENDING installment as PARTIAL")
        void partialPaymentMarksPending() {
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("400"));

            repaymentService.processPayment(buildRequest(new BigDecimal("400")));

            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
            assertThat(inst.getAmountPaid()).isEqualByComparingTo(new BigDecimal("400"));
        }

        @Test
        @DisplayName("OVERDUE installment remains OVERDUE on partial payment")
        void overdueStaysOverdueOnPartialPayment() {
            Installment inst = buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("400"));

            repaymentService.processPayment(buildRequest(new BigDecimal("400")));

            // OVERDUE must NOT become PARTIAL — status stays OVERDUE
            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
            assertThat(inst.getAmountPaid()).isEqualByComparingTo(new BigDecimal("400"));
        }

        @Test
        @DisplayName("OVERDUE installment becomes PAID when fully settled")
        void overdueBecomePaidWhenFullySettled() {
            Installment inst = buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.PAID);
        }

        @Test
        @DisplayName("Payment spanning multiple installments allocates in FIFO order")
        void paymentSpansMultipleInstallments() {
            Installment inst1 = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("500"), BigDecimal.ZERO);
            Installment inst2 = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("500"), BigDecimal.ZERO);
            Installment inst3 = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("500"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst1, inst2, inst3));
            stubSaveAndMapper(activeLoan, new BigDecimal("1200"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1200")));

            assertThat(inst1.getStatus()).isEqualTo(InstallmentStatus.PAID);
            assertThat(inst1.getAmountPaid()).isEqualByComparingTo(new BigDecimal("500"));
            assertThat(inst2.getStatus()).isEqualTo(InstallmentStatus.PAID);
            assertThat(inst2.getAmountPaid()).isEqualByComparingTo(new BigDecimal("500"));
            assertThat(inst3.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
            assertThat(inst3.getAmountPaid()).isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("Payment continuation from partial: adds to existing amountPaid")
        void continuationFromPartialInstallment() {
            // inst already has 300 paid of 1000, now paying another 700 to finish it
            Installment inst = buildInstallment(InstallmentStatus.PARTIAL, new BigDecimal("1000"), new BigDecimal("300"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("700"));

            repaymentService.processPayment(buildRequest(new BigDecimal("700")));

            assertThat(inst.getStatus()).isEqualTo(InstallmentStatus.PAID);
            assertThat(inst.getAmountPaid()).isEqualByComparingTo(new BigDecimal("1000"));
        }
    }

    // ── Post-payment side-effects ─────────────────────────────────────────────

    @Nested
    @DisplayName("Post-payment side-effects")
    class PostPaymentSideEffects {

        @Test
        @DisplayName("loan.totalPaid is incremented by payment amount")
        void totalPaidIncremented() {
            activeLoan.setTotalPaid(new BigDecimal("2000"));
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            assertThat(activeLoan.getTotalPaid()).isEqualByComparingTo(new BigDecimal("3000"));
        }

        @Test
        @DisplayName("recalculateStatus is called after payment")
        void recalculateStatusCalled() {
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(loanService).recalculateStatus(loanId);
        }

        @Test
        @DisplayName("Notification dispatched to borrower's email after successful payment")
        void notificationDispatched() {
            UUID borrowerId = UUID.randomUUID();
            activeLoan.setBorrowerId(borrowerId);
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(borrowerId);
            borrower.setEmail("borrower@example.com");

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(notificationDispatcher).dispatchRepaymentMade(
                    eq("borrower@example.com"), any(), eq(loanId), any(), any());
        }

        @Test
        @DisplayName("No notification dispatched when borrower email is blank")
        void noNotificationWhenEmailBlank() {
            UUID borrowerId = UUID.randomUUID();
            activeLoan.setBorrowerId(borrowerId);
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(borrowerId);
            borrower.setEmail("");

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(notificationDispatcher, never()).dispatchRepaymentMade(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("No notification dispatched when borrower email is null")
        void noNotificationWhenEmailNull() {
            UUID borrowerId = UUID.randomUUID();
            activeLoan.setBorrowerId(borrowerId);
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            Borrower borrower = new Borrower();
            borrower.setBorrowerId(borrowerId);
            borrower.setEmail(null);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            stubSaveAndMapper(activeLoan, new BigDecimal("1000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(notificationDispatcher, never()).dispatchRepaymentMade(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("No notification dispatched when loanForNotif is null (second findById returns empty)")
        void noNotificationWhenLoanForNotifNull() {
            Installment inst = buildInstallment(InstallmentStatus.PENDING, new BigDecimal("1000"), BigDecimal.ZERO);
            Repayment saved = new Repayment();
            saved.setRepaymentId(UUID.randomUUID());
            saved.setLoanId(loanId);
            saved.setAmount(new BigDecimal("1000"));
            saved.setPaymentReference("PAY-TEST");

            // 1st findById: loan lookup, 2nd: reload (orElseThrow), 3rd: loanForNotif=null
            when(loanRepository.findById(loanId))
                    .thenReturn(Optional.of(activeLoan))
                    .thenReturn(Optional.of(activeLoan))
                    .thenReturn(Optional.empty());
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            when(repaymentMapper.toEntity(any(), any())).thenReturn(saved);
            when(repaymentRepository.saveAndFlush(any())).thenReturn(saved);
            when(loanRepository.saveAndFlush(any())).thenReturn(activeLoan);
            when(repaymentMapper.toResponse(any(), any(), any())).thenReturn(new RepaymentResponse());

            repaymentService.processPayment(buildRequest(new BigDecimal("1000")));

            verify(borrowerRepository, never()).findById(any());
        }
    }

    // ── Concurrency serialization logic ──────────────────────────────────────

    @Nested
    @DisplayName("Concurrency serialization logic")
    class ConcurrencySerializationLogic {

        /**
         * Simulates what happens when a second concurrent payment tries to pay more
         * than what's left after the first payment has already been locked and committed.
         * In production, the SELECT FOR UPDATE forces thread-2 to wait; when it resumes,
         * it sees the updated (lower) remaining balance and this overpayment check fires.
         */
        @Test
        @DisplayName("Second payment sees updated balance and throws overpayment exception")
        void concurrentPaymentsAreSerialized() {
            // After thread-1 paid 800 of the 1000, only 200 remains.
            // Thread-2 arrives and tries to pay 800 again.
            Installment inst = buildInstallment(InstallmentStatus.PARTIAL, new BigDecimal("1000"), new BigDecimal("800"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));

            // remaining = 1000 - 800 = 200; paying 800 > 200 → overpayment
            var request = buildRequest(new BigDecimal("800"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(BusinessException.class);

            verify(repaymentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Zero remaining balance causes overpayment for any payment amount")
        void zeroRemainingBlocksAllPayments() {
            // Fully paid installment — no remaining balance
            Installment inst = buildInstallment(InstallmentStatus.PAID, new BigDecimal("1000"), new BigDecimal("1000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(installmentRepository.findUnpaidByLoanIdForUpdate(loanId)).thenReturn(List.of(inst));
            var request = buildRequest(new BigDecimal("1"));

            assertThatThrownBy(() -> repaymentService.processPayment(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── getByLoanId() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByLoanId()")
    class GetByLoanId {

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenLoanNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> repaymentService.getByLoanId(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns list of repayment responses for a valid loan")
        void returnsRepaymentList() {
            Repayment repayment = new Repayment();
            repayment.setRepaymentId(UUID.randomUUID());
            repayment.setLoanId(loanId);
            repayment.setAmount(new BigDecimal("500"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(repaymentRepository.findByLoanIdOrderByPaidAtAsc(loanId)).thenReturn(List.of(repayment));
            when(repaymentMapper.toResponse(any(), any(), any())).thenReturn(new RepaymentResponse());

            List<RepaymentResponse> result = repaymentService.getByLoanId(loanId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Returns empty list when no repayments exist for loan")
        void returnsEmptyListWhenNoRepayments() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(repaymentRepository.findByLoanIdOrderByPaidAtAsc(loanId)).thenReturn(List.of());

            List<RepaymentResponse> result = repaymentService.getByLoanId(loanId);

            assertThat(result).isEmpty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RepaymentRequest buildRequest(BigDecimal amount) {
        RepaymentRequest req = new RepaymentRequest();
        req.setLoanId(loanId);
        req.setAmount(amount);
        req.setPaymentMode(PaymentMode.UPI);
        return req;
    }

    private Loan buildLoan(LoanStatus status, BigDecimal totalPayable, BigDecimal totalPaid) {
        Loan loan = new Loan();
        loan.setLoanId(loanId);
        loan.setStatus(status);
        loan.setTotalPayable(totalPayable);
        loan.setTotalPaid(totalPaid);
        loan.setTotalAmount(totalPayable);
        loan.setTotalPenaltyAmount(BigDecimal.ZERO);
        return loan;
    }

    private Installment buildInstallment(InstallmentStatus status, BigDecimal totalDue, BigDecimal amountPaid) {
        Installment inst = new Installment();
        inst.setInstallmentId(UUID.randomUUID());
        inst.setLoanId(loanId);
        inst.setInstallmentNo(1);
        inst.setStatus(status);
        inst.setTotalDue(totalDue);
        inst.setAmountPaid(amountPaid);
        inst.setEpiAmount(totalDue);
        inst.setPenaltyAmount(BigDecimal.ZERO);
        return inst;
    }

    /** Stubs the repayment save, loan save, and mapper calls for happy-path tests. */
    private void stubSaveAndMapper(Loan loan, BigDecimal amount) {
        Repayment saved = new Repayment();
        saved.setRepaymentId(UUID.randomUUID());
        saved.setLoanId(loanId);
        saved.setAmount(amount);
        saved.setPaymentReference("PAY-TEST");
        when(repaymentMapper.toEntity(any(), any())).thenReturn(saved);
        when(repaymentRepository.saveAndFlush(any())).thenReturn(saved);
        when(loanRepository.saveAndFlush(any())).thenReturn(loan);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
        when(repaymentMapper.toResponse(any(), any(), any())).thenReturn(new RepaymentResponse());
    }
}
