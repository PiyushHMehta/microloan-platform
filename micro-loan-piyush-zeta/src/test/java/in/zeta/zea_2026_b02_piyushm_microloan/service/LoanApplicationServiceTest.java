package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.ApproveRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.RejectRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProduct;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProductFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.LoanApplicationMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanApplicationRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductFrequencyRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import org.mockito.Answers;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanApplicationService Tests")
class LoanApplicationServiceTest {

    @Mock private BorrowerRepository borrowerRepository;
    @Mock private LoanProductRepository loanProductRepository;
    @Mock private LoanProductFrequencyRepository frequencyRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanService loanService;
    @Mock private LoanApplicationMapper loanApplicationMapper;
    @Mock private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private UUID borrowerId;
    private UUID productId;
    private Borrower borrower;
    private LoanProduct product;
    private LoanApplicationCreateRequest request;

    @BeforeEach
    void setUp() {
        borrowerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        ReflectionTestUtils.setField(loanApplicationService, "maxEmiToIncomeRatio", new BigDecimal("0.50"));

        borrower = new Borrower();
        borrower.setBorrowerId(borrowerId);
        borrower.setIsActive(true);
        borrower.setMonthlyIncome(new BigDecimal("50000"));
        borrower.setAnnualHouseholdIncome(new BigDecimal("200000"));
        borrower.setKycLevel(KycLevel.MIN_KYC);
        borrower.setEmail("borrower@example.com");

        product = new LoanProduct();
        product.setProductId(productId);
        product.setIsActive(true);
        product.setMinPrincipal(new BigDecimal("5000"));
        product.setMaxPrincipal(new BigDecimal("100000"));
        product.setMinTenureMonths(3);
        product.setMaxTenureMonths(24);
        product.setInterestRate(new BigDecimal("12.00"));
        product.setMinKycLevel(KycLevel.MIN_KYC);

        request = new LoanApplicationCreateRequest();
        request.setBorrowerId(borrowerId);
        request.setProductId(productId);
        request.setRequestedAmount(new BigDecimal("20000"));
        request.setRequestedTenureMonths(12);
        request.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
    }

    // ── apply() — Borrower validations ────────────────────────────────────────

    @Nested
    @DisplayName("apply() — borrower validations")
    class BorrowerValidations {

        @Test
        @DisplayName("Throws ResourceNotFoundException when borrower does not exist")
        void throwsWhenBorrowerNotFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when product does not exist")
        void throwsWhenProductNotFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when borrower is inactive")
        void throwsWhenBorrowerInactive() {
            borrower.setIsActive(false);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when annual income exceeds RBI limit (>3L)")
        void throwsWhenIncomeExceedsRbiLimit() {
            borrower.setAnnualHouseholdIncome(new BigDecimal("400000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when borrower already has an active loan")
        void throwsWhenActiveLoanExists() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(frequencyRepository.findByLoanProductProductId(productId))
                    .thenReturn(List.of(buildFreq(RepaymentFrequency.MONTHLY)));
            when(loanRepository.existsByBorrowerIdAndStatusIn(borrowerId,
                    List.of(LoanStatus.KFS_PENDING, LoanStatus.ACTIVE, LoanStatus.OVERDUE)))
                    .thenReturn(true);

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── apply() — Product validations ─────────────────────────────────────────

    @Nested
    @DisplayName("apply() — product validations")
    class ProductValidations {

        @Test
        @DisplayName("Throws BusinessException when product is inactive")
        void throwsWhenProductInactive() {
            product.setIsActive(false);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when KYC level is insufficient")
        void throwsWhenKycInsufficient() {
            borrower.setKycLevel(KycLevel.NO_KYC);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when amount is below product minimum")
        void throwsWhenAmountBelowMin() {
            request.setRequestedAmount(new BigDecimal("1000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when amount exceeds product maximum")
        void throwsWhenAmountAboveMax() {
            request.setRequestedAmount(new BigDecimal("200000"));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when tenure is below product minimum")
        void throwsWhenTenureBelowMin() {
            request.setRequestedTenureMonths(1);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when tenure exceeds product maximum")
        void throwsWhenTenureAboveMax() {
            request.setRequestedTenureMonths(36);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws BusinessException when repayment frequency not allowed by product")
        void throwsWhenFrequencyNotAllowed() {
            request.setRepaymentFrequency(RepaymentFrequency.WEEKLY);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(frequencyRepository.findByLoanProductProductId(productId))
                    .thenReturn(List.of(buildFreq(RepaymentFrequency.MONTHLY)));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── apply() — EPI affordability ───────────────────────────────────────────

    @Nested
    @DisplayName("apply() — EPI affordability (RBI 50% rule)")
    class EpiAffordability {

        @Test
        @DisplayName("Throws when monthly EMI burden exceeds 50% of income")
        void throwsWhenEmiExceedsAffordabilityLimit() {
            request.setRequestedAmount(new BigDecimal("90000"));
            request.setRequestedTenureMonths(3);
            product.setMinTenureMonths(1);

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(frequencyRepository.findByLoanProductProductId(productId))
                    .thenReturn(List.of(buildFreq(RepaymentFrequency.MONTHLY)));

            assertThatThrownBy(() -> loanApplicationService.apply(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── apply() — Success ─────────────────────────────────────────────────────

    @Test
    @DisplayName("apply() creates application successfully when all validations pass")
    void applySuccessfully() {
        LoanApplication savedApp = buildSavedApplication();

        when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
        when(frequencyRepository.findByLoanProductProductId(productId))
                .thenReturn(List.of(buildFreq(RepaymentFrequency.MONTHLY)));
        when(loanRepository.existsByBorrowerIdAndStatusIn(any(), any())).thenReturn(false);
        when(loanApplicationMapper.toEntity(request)).thenReturn(savedApp);
        when(loanApplicationRepository.save(any())).thenReturn(savedApp);
        when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

        loanApplicationService.apply(request);

        verify(loanApplicationRepository).save(any());
    }

    @Test
    @DisplayName("apply() with BIWEEKLY frequency covers monthlyBurden × 2 path")
    void applySuccessfullyBiweekly() {
        request.setRepaymentFrequency(RepaymentFrequency.BIWEEKLY);
        LoanApplication savedApp = buildSavedApplication();

        when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
        when(frequencyRepository.findByLoanProductProductId(productId))
                .thenReturn(List.of(buildFreq(RepaymentFrequency.BIWEEKLY)));
        when(loanRepository.existsByBorrowerIdAndStatusIn(any(), any())).thenReturn(false);
        when(loanApplicationMapper.toEntity(request)).thenReturn(savedApp);
        when(loanApplicationRepository.save(any())).thenReturn(savedApp);
        when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

        loanApplicationService.apply(request);

        verify(loanApplicationRepository).save(any());
    }

    @Test
    @DisplayName("apply() with WEEKLY frequency covers monthlyBurden × 4 path")
    void applySuccessfullyWeekly() {
        request.setRepaymentFrequency(RepaymentFrequency.WEEKLY);
        LoanApplication savedApp = buildSavedApplication();

        when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
        when(frequencyRepository.findByLoanProductProductId(productId))
                .thenReturn(List.of(buildFreq(RepaymentFrequency.WEEKLY)));
        when(loanRepository.existsByBorrowerIdAndStatusIn(any(), any())).thenReturn(false);
        when(loanApplicationMapper.toEntity(request)).thenReturn(savedApp);
        when(loanApplicationRepository.save(any())).thenReturn(savedApp);
        when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

        loanApplicationService.apply(request);

        verify(loanApplicationRepository).save(any());
    }

    // ── approve() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approve()")
    class Approve {

        @Test
        @DisplayName("Throws ResourceNotFoundException when application does not exist")
        void throwsWhenApplicationNotFound() {
            UUID appId = UUID.randomUUID();
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.empty());
            var approveRequest = buildApproveRequest();

            assertThatThrownBy(() -> loanApplicationService.approve(appId, approveRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when application is already APPROVED")
        void throwsWhenAlreadyApproved() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setStatus(ApplicationStatus.APPROVED);
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            var approveRequest = buildApproveRequest();

            assertThatThrownBy(() -> loanApplicationService.approve(appId, approveRequest))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("Throws DuplicateResourceException when loan already exists for this application")
        void throwsWhenLoanAlreadyExists() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.of(new Loan()));
            var approveRequest = buildApproveRequest();

            assertThatThrownBy(() -> loanApplicationService.approve(appId, approveRequest))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("Success: status set to APPROVED and loan creation triggered")
        void approvesSuccessfully() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);
            LoanResponse loanResponse = new LoanResponse();
            loanResponse.setLoanId(UUID.randomUUID());

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.empty());
            when(loanApplicationRepository.saveAndFlush(any())).thenReturn(app);
            when(loanService.createFromApplication(any())).thenReturn(loanResponse);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(buildBorrowerEntity()));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.approve(appId, buildApproveRequest());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            verify(loanService).createFromApplication(any());
        }
    }

    // ── reject() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("Throws ResourceNotFoundException when application does not exist")
        void throwsWhenApplicationNotFound() {
            UUID appId = UUID.randomUUID();
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.empty());
            var rejectRequest = buildRejectRequest();

            assertThatThrownBy(() -> loanApplicationService.reject(appId, rejectRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws InvalidStateException when application is not PENDING")
        void throwsWhenNotPending() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setStatus(ApplicationStatus.REJECTED);
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            var rejectRequest = buildRejectRequest();

            assertThatThrownBy(() -> loanApplicationService.reject(appId, rejectRequest))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("Success: status set to REJECTED with reason and reviewer")
        void rejectsSuccessfully() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(buildBorrowerEntity()));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.reject(appId, buildRejectRequest());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(app.getRejectionReason()).isEqualTo("Low credit score");
        }

        @Test
        @DisplayName("Notification dispatched after rejection when email is present")
        void rejectionNotificationDispatched() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(buildBorrowerEntity()));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.reject(appId, buildRejectRequest());

            verify(notificationDispatcher).dispatchLoanApplicationRejected(
                    eq("borrower@example.com"), eq(appId), any(), any());
        }
    }

    // ── getById() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Throws ResourceNotFoundException when application does not exist")
        void throwsWhenNotFound() {
            UUID appId = UUID.randomUUID();
            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanApplicationService.getById(appId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns response with loanId when a loan exists")
        void returnsResponseWithLoanId() {
            UUID appId = UUID.randomUUID();
            UUID loanId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            Loan loan = new Loan();
            loan.setLoanId(loanId);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.of(loan));
            when(loanApplicationMapper.toResponse(any(), eq(loanId))).thenReturn(null);

            loanApplicationService.getById(appId);

            verify(loanApplicationMapper).toResponse(any(), eq(loanId));
        }

        @Test
        @DisplayName("Returns response with null loanId when no loan exists yet")
        void returnsResponseWithNullLoanId() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.empty());
            when(loanApplicationMapper.toResponse(any(), isNull())).thenReturn(null);

            loanApplicationService.getById(appId);

            verify(loanApplicationMapper).toResponse(any(), isNull());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoanProductFrequency buildFreq(RepaymentFrequency frequency) {
        LoanProductFrequency f = new LoanProductFrequency();
        f.setFrequency(frequency);
        return f;
    }

    private LoanApplication buildSavedApplication() {
        LoanApplication app = new LoanApplication();
        app.setApplicationId(UUID.randomUUID());
        app.setBorrowerId(borrowerId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.PENDING);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setRequestedTenureMonths(12);
        app.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
        return app;
    }

    private Borrower buildBorrowerEntity() {
        Borrower b = new Borrower();
        b.setBorrowerId(borrowerId);
        b.setEmail("borrower@example.com");
        b.setFullName("Test Borrower");
        return b;
    }

    private ApproveRequest buildApproveRequest() {
        ApproveRequest req = new ApproveRequest();
        req.setReviewedBy("admin");
        return req;
    }

    private RejectRequest buildRejectRequest() {
        RejectRequest req = new RejectRequest();
        req.setReviewedBy("admin");
        req.setRejectionReason("Low credit score");
        return req;
    }

    // ── approve() — email null/blank notification skip ────────────────────────

    @Nested
    @DisplayName("approve() — notification branch")
    class ApproveNotification {

        @Test
        @DisplayName("No approval notification when borrower email is null")
        void noApprovalNotificationWhenEmailNull() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);
            LoanResponse loanResponse = new LoanResponse();
            loanResponse.setLoanId(UUID.randomUUID());

            Borrower noEmail = buildBorrowerEntity();
            noEmail.setEmail(null);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.empty());
            when(loanApplicationRepository.saveAndFlush(any())).thenReturn(app);
            when(loanService.createFromApplication(any())).thenReturn(loanResponse);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(noEmail));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.approve(appId, buildApproveRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationApproved(any(), any(), any());
        }

        @Test
        @DisplayName("No approval notification when borrower email is blank")
        void noApprovalNotificationWhenEmailBlank() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);
            LoanResponse loanResponse = new LoanResponse();
            loanResponse.setLoanId(UUID.randomUUID());

            Borrower blankEmail = buildBorrowerEntity();
            blankEmail.setEmail("   ");

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.empty());
            when(loanApplicationRepository.saveAndFlush(any())).thenReturn(app);
            when(loanService.createFromApplication(any())).thenReturn(loanResponse);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(blankEmail));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.approve(appId, buildApproveRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationApproved(any(), any(), any());
        }

        @Test
        @DisplayName("No approval notification when borrower not found")
        void noApprovalNotificationWhenBorrowerNotFound() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);
            LoanResponse loanResponse = new LoanResponse();
            loanResponse.setLoanId(UUID.randomUUID());

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(loanRepository.findByApplicationId(appId)).thenReturn(Optional.empty());
            when(loanApplicationRepository.saveAndFlush(any())).thenReturn(app);
            when(loanService.createFromApplication(any())).thenReturn(loanResponse);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.approve(appId, buildApproveRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationApproved(any(), any(), any());
        }
    }

    // ── reject() — email null/blank notification skip ─────────────────────────

    @Nested
    @DisplayName("reject() — notification branch")
    class RejectNotification {

        @Test
        @DisplayName("No rejection notification when borrower email is null")
        void noRejectionNotificationWhenEmailNull() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);

            Borrower noEmail = buildBorrowerEntity();
            noEmail.setEmail(null);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(noEmail));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.reject(appId, buildRejectRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationRejected(any(), any(), any(), any());
        }

        @Test
        @DisplayName("No rejection notification when borrower email is blank")
        void noRejectionNotificationWhenEmailBlank() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);

            Borrower blankEmail = buildBorrowerEntity();
            blankEmail.setEmail("");

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(blankEmail));
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.reject(appId, buildRejectRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationRejected(any(), any(), any(), any());
        }

        @Test
        @DisplayName("No rejection notification when borrower not found")
        void noRejectionNotificationWhenBorrowerNotFound() {
            UUID appId = UUID.randomUUID();
            LoanApplication app = buildSavedApplication();
            app.setApplicationId(appId);

            when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            when(loanApplicationMapper.toResponse(any(), any())).thenReturn(null);

            loanApplicationService.reject(appId, buildRejectRequest());

            verify(notificationDispatcher, never()).dispatchLoanApplicationRejected(any(), any(), any(), any());
        }
    }

    // ── list() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("list()")
    class ListApplications {

        @SuppressWarnings("unchecked")
        private void stubFindAll() {
            when(loanApplicationRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenAnswer(inv -> {
                        Specification<LoanApplication> spec = inv.getArgument(0);
                        Root<LoanApplication> root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaQuery<?> cq = mock(CriteriaQuery.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaBuilder cb = mock(CriteriaBuilder.class, Answers.RETURNS_DEEP_STUBS);
                        spec.toPredicate(root, cq, cb);
                        return new PageImpl<>(Collections.emptyList());
                    });
        }

        @Test
        @DisplayName("Returns empty page with no filters")
        void returnsEmptyPageNoFilters() {
            stubFindAll();
            var result = loanApplicationService.list(Map.of(), Pageable.unpaged());
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Status filter executes equal predicate")
        void statusFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "PENDING");
            assertThat(loanApplicationService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("BorrowerId filter executes equal predicate")
        void borrowerIdFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("borrowerId", UUID.randomUUID().toString());
            assertThat(loanApplicationService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("ProductId filter executes equal predicate")
        void productIdFilterExecuted() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("productId", UUID.randomUUID().toString());
            assertThat(loanApplicationService.list(filters, Pageable.unpaged())).isNotNull();
        }

        @Test
        @DisplayName("Blank filter values are ignored")
        void blankFiltersIgnored() {
            stubFindAll();
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "");
            assertThat(loanApplicationService.list(filters, Pageable.unpaged())).isNotNull();
        }
    }
}
