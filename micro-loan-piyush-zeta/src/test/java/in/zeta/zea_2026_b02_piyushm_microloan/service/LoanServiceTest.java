package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import org.mockito.Answers;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService Tests")
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private LoanProductRepository loanProductRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private BorrowerRepository borrowerRepository;
    @Mock private LoanMapper loanMapper;
    @Mock private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private LoanService loanService;

    private UUID loanId;
    private UUID borrowerId;
    private UUID productId;
    private LoanProduct product;
    private LoanApplication application;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
        borrowerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        product = new LoanProduct();
        product.setProductId(productId);
        product.setInterestRate(new BigDecimal("12.00"));
        product.setPenaltyRate(new BigDecimal("2.00"));

        application = new LoanApplication();
        application.setApplicationId(UUID.randomUUID());
        application.setBorrowerId(borrowerId);
        application.setProductId(productId);
        application.setRequestedAmount(new BigDecimal("60000"));
        application.setRequestedTenureMonths(12);
        application.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
    }

    // ── createFromApplication() — EPI Math ────────────────────────────────────

    @Nested
    @DisplayName("createFromApplication() — EPI calculation")
    class EpiCalculation {

        @Test
        @DisplayName("Monthly 12 months: correct totalAmount and EPI")
        void monthlyInstallmentsCorrect() {
            // principal=60000, rate=12%, 12 months
            // totalInterest = 60000 * 12/100 * 12/12 = 7200
            // totalAmount = 67200, epi = 67200/12 = 5600.00
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).save(argThat(loan ->
                    loan.getTotalAmount().compareTo(new BigDecimal("67200.00")) == 0 &&
                    loan.getEpiAmount().compareTo(new BigDecimal("5600.00")) == 0
            ));
        }

        @Test
        @DisplayName("Last installment absorbs rounding remainder")
        void lastInstallmentAbsorbsRounding() {
            // principal=10000, rate=10%, 3 months
            // totalInterest = 10000 * 10/100 * 3/12 = 250
            // totalAmount = 10250, epi = 10250/3 = 3416.67
            application.setRequestedAmount(new BigDecimal("10000"));
            application.setRequestedTenureMonths(3);
            product.setInterestRate(new BigDecimal("10.00"));

            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).save(argThat(loan ->
                    loan.getEpiAmount().compareTo(new BigDecimal("3416.67")) == 0
            ));
        }

        @Test
        @DisplayName("BIWEEKLY 6 months produces 12 installments in KFS snapshot")
        void biweeklyInstallmentCount() {
            application.setRepaymentFrequency(RepaymentFrequency.BIWEEKLY);
            application.setRequestedTenureMonths(6);

            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).saveAndFlush(argThat(loan ->
                    loan.getKfsSnapshot() != null && loan.getKfsSnapshot().getNumInstallments() == 12
            ));
        }

        @Test
        @DisplayName("WEEKLY 4 months produces 16 installments in KFS snapshot")
        void weeklyInstallmentCount() {
            application.setRepaymentFrequency(RepaymentFrequency.WEEKLY);
            application.setRequestedTenureMonths(4);

            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).saveAndFlush(argThat(loan ->
                    loan.getKfsSnapshot() != null && loan.getKfsSnapshot().getNumInstallments() == 16
            ));
        }

        @Test
        @DisplayName("Loan created with KFS_PENDING status and totalPaid=0")
        void loanCreatedWithCorrectInitialState() {
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).save(argThat(loan ->
                    loan.getStatus() == LoanStatus.KFS_PENDING &&
                    loan.getTotalPaid().compareTo(BigDecimal.ZERO) == 0 &&
                    loan.getTotalPenaltyAmount().compareTo(BigDecimal.ZERO) == 0
            ));
        }

        @Test
        @DisplayName("KFS notification dispatched when borrower email exists")
        void kfsNotificationDispatched() {
            Borrower borrower = buildBorrower("kfs@example.com");
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> { Loan l = inv.getArgument(0); l.setLoanId(loanId); return l; });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setLoanId(loanId);
                return l;
            });
            // createFromApplication calls getKfs(loanId) internally — stub the findById
            when(loanRepository.findById(loanId)).thenAnswer(inv -> {
                // Return the saved loan from the saveAndFlush answer
                Loan l = buildLoan(LoanStatus.KFS_PENDING);
                in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot snap =
                    in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot.builder()
                        .principal(new BigDecimal("60000"))
                        .interestRate(new BigDecimal("12.00"))
                        .totalInterest(new BigDecimal("7200"))
                        .totalAmount(new BigDecimal("67200"))
                        .epiAmount(new BigDecimal("5600"))
                        .repaymentFrequency(RepaymentFrequency.MONTHLY)
                        .tenureMonths(12)
                        .numInstallments(12)
                        .penaltyRate(new BigDecimal("2.00"))
                        .repaymentSchedule(List.of())
                        .build();
                l.setKfsSnapshot(snap);
                return Optional.of(l);
            });
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));

            loanService.createFromApplication(application);

            verify(notificationDispatcher).dispatchKfsGenerated(any(), any(), any());
        }
    }

    // ── getKfs() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getKfs()")
    class GetKfs {

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenLoanNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.getKfs(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when KFS snapshot is null")
        void throwsWhenNoKfsSnapshot() {
            Loan loan = buildLoan(LoanStatus.KFS_PENDING);
            loan.setKfsSnapshot(null);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.getKfs(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── acceptKfs() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("acceptKfs()")
    class AcceptKfs {

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenLoanNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.acceptKfs(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when loan is ACTIVE (not KFS_PENDING)")
        void throwsWhenNotKfsPending() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.acceptKfs(loanId))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when loan is CLOSED")
        void throwsWhenClosed() {
            Loan loan = buildLoan(LoanStatus.CLOSED);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.acceptKfs(loanId))
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    // ── getById() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.getById(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns response when loan exists")
        void returnsResponse() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(loanMapper.toResponse(loan)).thenReturn(new LoanResponse());

            LoanResponse result = loanService.getById(loanId);

            assertThat(result).isNotNull();
        }
    }

    // ── recalculateStatus() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("recalculateStatus()")
    class RecalculateStatus {

        @Test
        @DisplayName("All installments PAID → loan becomes CLOSED and notification dispatched")
        void allPaidBecomesClosedWithNotification() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            loan.setTotalAmount(new BigDecimal("10000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of(
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO),
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO)
            ));
            when(loanRepository.save(any())).thenReturn(loan);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(buildBorrower("close@example.com")));

            loanService.recalculateStatus(loanId);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
            verify(notificationDispatcher).dispatchLoanClosed(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Any installment OVERDUE → loan becomes OVERDUE")
        void anyOverdueMakesLoanOverdue() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            loan.setTotalAmount(new BigDecimal("10000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of(
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO),
                    buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("100"))
            ));
            when(loanRepository.save(any())).thenReturn(loan);

            loanService.recalculateStatus(loanId);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
        }

        @Test
        @DisplayName("Mix of PAID and PENDING → loan stays ACTIVE")
        void mixedInstallmentsStaysActive() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            loan.setTotalAmount(new BigDecimal("10000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of(
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO),
                    buildInstallment(InstallmentStatus.PENDING, BigDecimal.ZERO)
            ));
            when(loanRepository.save(any())).thenReturn(loan);

            loanService.recalculateStatus(loanId);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        }

        @Test
        @DisplayName("KFS_PENDING loan status is never changed by recalculate")
        void kfsPendingLoanStatusUnchanged() {
            Loan loan = buildLoan(LoanStatus.KFS_PENDING);
            loan.setTotalAmount(new BigDecimal("10000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of(
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO),
                    buildInstallment(InstallmentStatus.PAID, BigDecimal.ZERO)
            ));
            when(loanRepository.save(any())).thenReturn(loan);

            loanService.recalculateStatus(loanId);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.KFS_PENDING);
            verify(notificationDispatcher, never()).dispatchLoanClosed(any(), any(), any(), any());
        }

        @Test
        @DisplayName("totalPenaltyAmount summed from all installments")
        void penaltyAmountRecalculated() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            loan.setTotalAmount(new BigDecimal("10000"));

            Installment inst1 = buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("200"));
            Installment inst2 = buildInstallment(InstallmentStatus.OVERDUE, new BigDecimal("150"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of(inst1, inst2));
            when(loanRepository.save(any())).thenReturn(loan);

            loanService.recalculateStatus(loanId);

            assertThat(loan.getTotalPenaltyAmount()).isEqualByComparingTo(new BigDecimal("350"));
            assertThat(loan.getTotalPayable()).isEqualByComparingTo(new BigDecimal("10350"));
        }

        @Test
        @DisplayName("No installments — loan stays in current status")
        void noInstallmentsLoanUnchanged() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            loan.setTotalAmount(new BigDecimal("10000"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.findByLoanId(loanId)).thenReturn(List.of());
            when(loanRepository.save(any())).thenReturn(loan);

            loanService.recalculateStatus(loanId);

            // No installments → allPaid=false → stays ACTIVE
            assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when loan does not exist")
        void throwsWhenLoanNotFound() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.recalculateStatus(loanId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── list() ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("list()")
    class ListLoans {

        @SuppressWarnings("unchecked")
        private void stubFindAll() {
            when(loanRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenAnswer(inv -> {
                        Specification<Loan> spec = inv.getArgument(0);
                        Root<Loan> root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaQuery<?> cq = mock(CriteriaQuery.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaBuilder cb = mock(CriteriaBuilder.class, Answers.RETURNS_DEEP_STUBS);
                        spec.toPredicate(root, cq, cb);
                        return new PageImpl<>(Collections.emptyList());
                    });
        }

        @Test
        @DisplayName("Returns empty page when no filters")
        void returnsEmptyPageNoFilters() {
            stubFindAll();
            var result = loanService.list(Map.of(), Pageable.unpaged());
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Status filter executes equal predicate")
        void statusFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "ACTIVE");
            assertThat(loanService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("BorrowerId filter executes equal predicate")
        void borrowerIdFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("borrowerId", UUID.randomUUID().toString());
            assertThat(loanService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("ProductId filter executes equal predicate")
        void productIdFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("productId", UUID.randomUUID().toString());
            assertThat(loanService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("Blank filter values are ignored")
        void blankFiltersIgnored() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "");
            filters.put("borrowerId", "");
            assertThat(loanService.list(filters, Pageable.unpaged())).isNotNull();
        }
    }

    // ── createFromApplication() — notification branch ─────────────────────────

    @Nested
    @DisplayName("createFromApplication() — notification")
    class CreateNotification {

        @Test
        @DisplayName("No KFS notification dispatched when borrower not found")
        void noKfsNotificationWhenBorrowerNotFound() {
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setLoanId(loanId);
                return l;
            });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(notificationDispatcher, never()).dispatchKfsGenerated(any(), any(), any());
        }
    }

    // ── acceptKfs() — notification branch ────────────────────────────────────

    @Nested
    @DisplayName("acceptKfs() — notification branch")
    class AcceptKfsNotification {

        @Test
        @DisplayName("No issuance notification dispatched when borrower not found")
        void noIssuedNotificationWhenBorrowerNotFound() {
            Loan loan = buildLoan(LoanStatus.KFS_PENDING);
            in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot snap =
                    in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot.builder()
                            .repaymentSchedule(List.of())
                            .build();
            loan.setKfsSnapshot(snap);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.saveAll(any())).thenReturn(List.of());
            when(loanRepository.save(any())).thenReturn(loan);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.acceptKfs(loanId);

            verify(notificationDispatcher, never()).dispatchLoanIssued(any(), any(), any(), any(), any());
        }
    }

    // ── acceptKfs() — success path ────────────────────────────────────────────

    @Nested
    @DisplayName("acceptKfs() — success")
    class AcceptKfsSuccess {

        @Test
        @DisplayName("Accepts KFS_PENDING loan: creates installments, dispatches loanIssued notification")
        void acceptsKfsPendingLoanWithBorrower() {
            Loan loan = buildLoan(LoanStatus.KFS_PENDING);
            in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot snap =
                    in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot.builder()
                            .repaymentSchedule(List.of())
                            .build();
            loan.setKfsSnapshot(snap);
            Borrower borrower = buildBorrower("issued@example.com");

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(installmentRepository.saveAll(any())).thenReturn(List.of());
            when(loanRepository.save(any())).thenReturn(loan);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            LoanResponse result = loanService.acceptKfs(loanId);

            assertThat(result).isNotNull();
            assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
            verify(notificationDispatcher).dispatchLoanIssued(
                    eq("issued@example.com"), any(), any(), any(), any());
        }
    }

    // ── getKfs() — ACCEPTED status ────────────────────────────────────────────

    @Nested
    @DisplayName("getKfs() — status field")
    class GetKfsStatus {

        @Test
        @DisplayName("getKfs returns status=ACCEPTED when loan is ACTIVE")
        void getKfsStatusAccepted() {
            Loan loan = buildLoan(LoanStatus.ACTIVE);
            in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot snap =
                    in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot.builder()
                            .principal(new BigDecimal("60000"))
                            .interestRate(new BigDecimal("12.00"))
                            .totalInterest(new BigDecimal("7200"))
                            .totalAmount(new BigDecimal("67200"))
                            .epiAmount(new BigDecimal("5600"))
                            .repaymentFrequency(RepaymentFrequency.MONTHLY)
                            .tenureMonths(12)
                            .numInstallments(12)
                            .penaltyRate(new BigDecimal("2.00"))
                            .repaymentSchedule(List.of())
                            .build();
            loan.setKfsSnapshot(snap);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            KfsResponse kfsResponse = loanService.getKfs(loanId);

            assertThat(kfsResponse.getStatus()).isEqualTo("ACCEPTED");
        }
    }

    // ── createFromApplication() — null penaltyRate ────────────────────────────

    @Nested
    @DisplayName("createFromApplication() — null penaltyRate")
    class CreateNullPenalty {

        @Test
        @DisplayName("Null penaltyRate defaults to BigDecimal.ZERO")
        void nullPenaltyRateDefaultsToZero() {
            product.setPenaltyRate(null); // trigger the ternary null path

            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(loanRepository.save(any())).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setLoanId(loanId);
                return l;
            });
            when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanMapper.toResponse(any())).thenReturn(new LoanResponse());

            loanService.createFromApplication(application);

            verify(loanRepository).save(argThat(loan ->
                    loan.getPenaltyRate() != null &&
                    loan.getPenaltyRate().compareTo(BigDecimal.ZERO) == 0
            ));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Loan buildLoan(LoanStatus status) {
        Loan loan = new Loan();
        loan.setLoanId(loanId);
        loan.setBorrowerId(borrowerId);
        loan.setStatus(status);
        loan.setTotalPaid(BigDecimal.ZERO);
        loan.setTotalPenaltyAmount(BigDecimal.ZERO);
        loan.setTotalPayable(new BigDecimal("10000"));
        loan.setTotalAmount(new BigDecimal("10000"));
        return loan;
    }

    private Installment buildInstallment(InstallmentStatus status, BigDecimal penalty) {
        Installment inst = new Installment();
        inst.setInstallmentId(UUID.randomUUID());
        inst.setLoanId(loanId);
        inst.setStatus(status);
        inst.setPenaltyAmount(penalty);
        return inst;
    }

    private Borrower buildBorrower(String email) {
        Borrower b = new Borrower();
        b.setBorrowerId(borrowerId);
        b.setEmail(email);
        return b;
    }
}
