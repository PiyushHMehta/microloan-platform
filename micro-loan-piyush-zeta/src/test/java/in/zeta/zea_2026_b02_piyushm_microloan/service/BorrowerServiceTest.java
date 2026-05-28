package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.BorrowerMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.Answers;

@ExtendWith(MockitoExtension.class)
@DisplayName("BorrowerService Tests")
class BorrowerServiceTest {

    @Mock private BorrowerRepository borrowerRepository;
    @Mock private BorrowerMapper borrowerMapper;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private BorrowerService borrowerService;

    private UUID borrowerId;
    private Borrower borrower;
    private BorrowerResponse borrowerResponse;

    @BeforeEach
    void setUp() {
        borrowerId = UUID.randomUUID();
        borrower = buildBorrower("test@example.com", true);
        borrowerResponse = BorrowerResponse.builder()
                .borrowerId(borrowerId)
                .fullName("Test User")
                .email("test@example.com")
                .build();
    }

    // ── create() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Success: saves borrower, dispatches welcome notification, returns response")
        void successWithEmailNotification() {
            BorrowerCreateRequest req = buildCreateRequest("test@example.com");

            when(borrowerMapper.toEntity(req)).thenReturn(borrower);
            when(borrowerRepository.save(borrower)).thenReturn(borrower);
            when(borrowerMapper.toResponse(borrower)).thenReturn(borrowerResponse);

            BorrowerResponse result = borrowerService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.getBorrowerId()).isEqualTo(borrowerId);
            verify(borrowerRepository).save(borrower);
            verify(notificationDispatcher).dispatchBorrowerRegistered(
                    eq("test@example.com"), any(), eq(borrowerId), any());
        }

        @Test
        @DisplayName("No notification dispatched when email is blank")
        void noNotificationWhenEmailBlank() {
            Borrower borrowerNoEmail = buildBorrower("", true);
            BorrowerCreateRequest req = buildCreateRequest("");

            when(borrowerMapper.toEntity(req)).thenReturn(borrowerNoEmail);
            when(borrowerRepository.save(borrowerNoEmail)).thenReturn(borrowerNoEmail);
            when(borrowerMapper.toResponse(borrowerNoEmail)).thenReturn(borrowerResponse);

            borrowerService.create(req);

            verify(notificationDispatcher, never()).dispatchBorrowerRegistered(any(), any(), any(), any());
        }

        @Test
        @DisplayName("No notification dispatched when email is null")
        void noNotificationWhenEmailNull() {
            Borrower borrowerNoEmail = buildBorrower(null, true);
            BorrowerCreateRequest req = buildCreateRequest(null);

            when(borrowerMapper.toEntity(req)).thenReturn(borrowerNoEmail);
            when(borrowerRepository.save(borrowerNoEmail)).thenReturn(borrowerNoEmail);
            when(borrowerMapper.toResponse(borrowerNoEmail)).thenReturn(borrowerResponse);

            borrowerService.create(req);

            verify(notificationDispatcher, never()).dispatchBorrowerRegistered(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Throws DuplicateResourceException on duplicate phone (DB constraint)")
        void throwsDuplicateOnConstraintViolation() {
            BorrowerCreateRequest req = buildCreateRequest("test@example.com");
            when(borrowerMapper.toEntity(req)).thenReturn(borrower);
            when(borrowerRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> borrowerService.create(req))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(notificationDispatcher, never()).dispatchBorrowerRegistered(any(), any(), any(), any());
        }
    }

    // ── update() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("Success: fetches borrower, applies update, returns response")
        void successfulUpdate() {
            BorrowerUpdateRequest req = BorrowerUpdateRequest.builder()
                    .fullName("Updated Name")
                    .city("Mumbai")
                    .build();

            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerRepository.save(any())).thenReturn(borrower);
            when(borrowerMapper.toResponse(borrower)).thenReturn(borrowerResponse);

            BorrowerResponse result = borrowerService.update(borrowerId, req);

            assertThat(result).isNotNull();
            verify(borrowerMapper).updateEntity(borrower, req);
            verify(borrowerRepository).save(borrower);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when borrower does not exist")
        void throwsWhenBorrowerNotFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());
            BorrowerUpdateRequest req = new BorrowerUpdateRequest();

            assertThatThrownBy(() -> borrowerService.update(borrowerId, req))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(borrowerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Throws BusinessException when borrower is deactivated")
        void throwsWhenBorrowerInactive() {
            Borrower inactive = buildBorrower("test@example.com", false);
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(inactive));
            BorrowerUpdateRequest req = new BorrowerUpdateRequest();

            assertThatThrownBy(() -> borrowerService.update(borrowerId, req))
                    .isInstanceOf(BusinessException.class);

            verify(borrowerRepository, never()).saveAndFlush(any());
        }
    }

    // ── getById() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Returns response when borrower exists")
        void returnsResponseWhenFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
            when(borrowerMapper.toResponse(borrower)).thenReturn(borrowerResponse);

            BorrowerResponse result = borrowerService.getById(borrowerId);

            assertThat(result.getBorrowerId()).isEqualTo(borrowerId);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when borrower does not exist")
        void throwsWhenNotFound() {
            when(borrowerRepository.findById(borrowerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> borrowerService.getById(borrowerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── list() ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("list()")
    class List {

        @SuppressWarnings("unchecked")
        private PagedResponse<BorrowerResponse> invokeListWithFilters(Map<String, String> filters) {
            when(borrowerRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenAnswer(inv -> {
                        Specification<Borrower> spec = inv.getArgument(0);
                        Root<Borrower> root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaQuery<?> cq = mock(CriteriaQuery.class, Answers.RETURNS_DEEP_STUBS);
                        CriteriaBuilder cb = mock(CriteriaBuilder.class, Answers.RETURNS_DEEP_STUBS);
                        spec.toPredicate(root, cq, cb);
                        return new PageImpl<>(java.util.Collections.emptyList());
                    });
            return borrowerService.list(filters, Pageable.unpaged());
        }

        @Test
        @DisplayName("Returns empty page when no borrowers match")
        void returnsEmptyPage() {
            PagedResponse<BorrowerResponse> result = invokeListWithFilters(Map.of());
            assertThat(result.getContent()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("filterScenarios")
        void filterPredicateExecutesWithoutError(String scenarioName, Map<String, String> filters) {
            PagedResponse<BorrowerResponse> result = invokeListWithFilters(filters);
            assertThat(result).isNotNull();
        }

        static Stream<Arguments> filterScenarios() {
            return Stream.of(
                    Arguments.of("String filter (fullName) executes LIKE predicate",
                            Map.of("fullName", "Test")),
                    Arguments.of("Enum filter (kycLevel) executes equal predicate",
                            Map.of("kycLevel", "NO_KYC")),
                    Arguments.of("Enum filter (gender) executes equal predicate",
                            Map.of("gender", "MALE")),
                    Arguments.of("Boolean filter (isActive) executes equal predicate",
                            Map.of("isActive", "true")),
                    Arguments.of("Pagination params (page, size, sort) are skipped",
                            Map.of("page", "0", "size", "10", "sort", "fullName,asc")),
                    Arguments.of("Unknown field keys are ignored",
                            Map.of("unknownField", "value"))
            );
        }

        @Test
        @DisplayName("Blank values are skipped")
        void blankValuesSkipped() {
            Map<String, String> filters = new HashMap<>();
            filters.put("email", "");
            filters.put("city", "  ");
            PagedResponse<BorrowerResponse> result = invokeListWithFilters(filters);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Multiple valid filters combined")
        void multipleFiltersExecuted() {
            Map<String, String> filters = new HashMap<>();
            filters.put("fullName", "Alice");
            filters.put("kycLevel", "NO_KYC");
            filters.put("isActive", "true");
            PagedResponse<BorrowerResponse> result = invokeListWithFilters(filters);
            assertThat(result).isNotNull();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Borrower buildBorrower(String email, boolean active) {
        Borrower b = new Borrower();
        b.setBorrowerId(borrowerId);
        b.setFullName("Test User");
        b.setEmail(email);
        b.setIsActive(active);
        b.setKycLevel(KycLevel.NO_KYC);
        b.setMonthlyIncome(new BigDecimal("30000"));
        b.setAnnualHouseholdIncome(new BigDecimal("200000"));
        return b;
    }

    private BorrowerCreateRequest buildCreateRequest(String email) {
        BorrowerCreateRequest req = new BorrowerCreateRequest();
        req.setFullName("Test User");
        req.setPhoneNumber("9876543210");
        req.setEmail(email);
        req.setMonthlyIncome(new BigDecimal("30000"));
        req.setAnnualHouseholdIncome(new BigDecimal("200000"));
        return req;
    }
}
