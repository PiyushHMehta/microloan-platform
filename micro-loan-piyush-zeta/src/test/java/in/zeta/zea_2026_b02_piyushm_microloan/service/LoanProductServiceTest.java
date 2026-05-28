package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProduct;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.LoanProductMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductFrequencyRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;
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
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanProductService Tests")
class LoanProductServiceTest {

	@Mock
	private LoanProductRepository loanProductRepository;
	@Mock
	private LoanProductFrequencyRepository frequencyRepository;
	@Mock
	private LoanProductMapper loanProductMapper;

	@InjectMocks
	private LoanProductService loanProductService;

	private UUID productId;
	private LoanProduct product;
	private LoanProductResponse response;

	@BeforeEach
	void setUp() {
		productId = UUID.randomUUID();

		product = LoanProduct.builder()
				.productId(productId)
				.name("Test Product")
				.minPrincipal(new BigDecimal("5000"))
				.maxPrincipal(new BigDecimal("100000"))
				.minTenureMonths(3)
				.maxTenureMonths(24)
				.interestRate(new BigDecimal("12.00"))
				.penaltyRate(new BigDecimal("2.00"))
				.minKycLevel(KycLevel.MIN_KYC)
				.frequencies(new ArrayList<>())
				.build();

		response = new LoanProductResponse();
	}

	// ── create() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("create()")
	class Create {

		@ParameterizedTest(name = "{0}")
		@MethodSource("invalidCreateRequests")
		void throwsBusinessExceptionOnInvalidRequest(String scenarioName, LoanProductCreateRequest req) {
			assertThatThrownBy(() -> loanProductService.create(req))
					.isInstanceOf(BusinessException.class);

			verify(loanProductRepository, never()).save(any());
		}

		static Stream<Arguments> invalidCreateRequests() {
			LoanProductCreateRequest nullFrequencies = LoanProductCreateRequest.builder()
					.name("Test Product").minPrincipal(new BigDecimal("5000")).maxPrincipal(new BigDecimal("100000"))
					.minTenureMonths(3).maxTenureMonths(24).interestRate(new BigDecimal("12.00"))
					.minKycLevel(KycLevel.MIN_KYC).frequencies(null).build();

			LoanProductCreateRequest emptyFrequencies = LoanProductCreateRequest.builder()
					.name("Test Product").minPrincipal(new BigDecimal("5000")).maxPrincipal(new BigDecimal("100000"))
					.minTenureMonths(3).maxTenureMonths(24).interestRate(new BigDecimal("12.00"))
					.minKycLevel(KycLevel.MIN_KYC).frequencies(List.of()).build();

			LoanProductCreateRequest invalidPrincipal = LoanProductCreateRequest.builder()
					.name("Test Product").minPrincipal(new BigDecimal("200000")).maxPrincipal(new BigDecimal("5000"))
					.minTenureMonths(3).maxTenureMonths(24).interestRate(new BigDecimal("12.00"))
					.minKycLevel(KycLevel.MIN_KYC).frequencies(List.of(RepaymentFrequency.MONTHLY)).build();

			LoanProductCreateRequest invalidTenure = LoanProductCreateRequest.builder()
					.name("Test Product").minPrincipal(new BigDecimal("5000")).maxPrincipal(new BigDecimal("100000"))
					.minTenureMonths(24).maxTenureMonths(3).interestRate(new BigDecimal("12.00"))
					.minKycLevel(KycLevel.MIN_KYC).frequencies(List.of(RepaymentFrequency.MONTHLY)).build();

			return Stream.of(
					Arguments.of("Throws when frequencies is null", nullFrequencies),
					Arguments.of("Throws when frequencies is empty", emptyFrequencies),
					Arguments.of("Throws when minPrincipal > maxPrincipal", invalidPrincipal),
					Arguments.of("Throws when minTenure > maxTenure", invalidTenure));
		}

		@Test
		@DisplayName("Success: saves product, saves frequencies, returns response")
		void createsSuccessfully() {
			LoanProductCreateRequest req = buildCreateRequest(
					new BigDecimal("5000"), new BigDecimal("100000"), 3, 24);
			req.setFrequencies(List.of(RepaymentFrequency.MONTHLY, RepaymentFrequency.BIWEEKLY));

			when(loanProductMapper.toEntity(req)).thenReturn(product);
			when(loanProductRepository.save(product)).thenReturn(product);
			when(frequencyRepository.saveAll(any())).thenReturn(List.of());
			when(loanProductMapper.toResponse(product)).thenReturn(response);

			LoanProductResponse result = loanProductService.create(req);

			assertThat(result).isSameAs(response);
			verify(loanProductRepository).save(product);
			verify(frequencyRepository).saveAll(any());
		}
	}

	// ── update() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("Throws ResourceNotFoundException when product not found")
		void throwsWhenProductNotFound() {
			when(loanProductRepository.findById(productId)).thenReturn(Optional.empty());
			var req = new LoanProductUpdateRequest();

			assertThatThrownBy(() -> loanProductService.update(productId, req))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("Throws BusinessException when updated minPrincipal > maxPrincipal")
		void throwsWhenPrincipalRangeInvalid() {
			product.setMinPrincipal(new BigDecimal("200000")); // min > max after mapper applies
			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			doNothing().when(loanProductMapper).updateEntity(eq(product), any());
			var req = new LoanProductUpdateRequest();

			assertThatThrownBy(() -> loanProductService.update(productId, req))
					.isInstanceOf(BusinessException.class);

			verify(loanProductRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("Throws BusinessException when updated minTenure > maxTenure")
		void throwsWhenTenureRangeInvalid() {
			product.setMinTenureMonths(36);
			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			doNothing().when(loanProductMapper).updateEntity(eq(product), any());
			var req = new LoanProductUpdateRequest();

			assertThatThrownBy(() -> loanProductService.update(productId, req))
					.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("Success without frequencies update (null frequencies in request)")
		void updatesSuccessfullyWithoutFrequencies() {
			LoanProductUpdateRequest req = new LoanProductUpdateRequest();
			req.setFrequencies(null);

			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			doNothing().when(loanProductMapper).updateEntity(product, req);
			when(loanProductRepository.saveAndFlush(product)).thenReturn(product);
			when(loanProductMapper.toResponse(product)).thenReturn(response);

			LoanProductResponse result = loanProductService.update(productId, req);

			assertThat(result).isSameAs(response);
			verify(frequencyRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("Success with frequencies update — replaces existing frequencies")
		void updatesSuccessfullyWithFrequencies() {
			LoanProductUpdateRequest req = LoanProductUpdateRequest.builder()
					.frequencies(List.of(RepaymentFrequency.WEEKLY))
					.build();

			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			doNothing().when(loanProductMapper).updateEntity(product, req);
			when(loanProductRepository.saveAndFlush(product)).thenReturn(product);
			when(loanProductMapper.toResponse(product)).thenReturn(response);

			LoanProductResponse result = loanProductService.update(productId, req);

			assertThat(result).isSameAs(response);
			// frequencies were added to product's collection
			assertThat(product.getFrequencies()).isNotEmpty();
		}

		@Test
		@DisplayName("Empty frequencies list in request throws BusinessException (PRD_004)")
		void emptyFrequenciesDoesNotReplace() {
			LoanProductUpdateRequest req = LoanProductUpdateRequest.builder()
					.frequencies(List.of())
					.build();

			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));

			assertThatThrownBy(() -> loanProductService.update(productId, req))
					.isInstanceOf(BusinessException.class);

			verify(loanProductRepository, never()).saveAndFlush(any());
		}
	}

	// ── deactivate() ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("deactivate()")
	class Deactivate {

		@Test
		@DisplayName("Throws ResourceNotFoundException when product not found")
		void throwsWhenNotFound() {
			when(loanProductRepository.findById(productId)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> loanProductService.deactivate(productId))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("Sets isActive=false and saves")
		void deactivatesSuccessfully() {
			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			when(loanProductRepository.save(product)).thenReturn(product);

			loanProductService.deactivate(productId);

			assertThat(product.getIsActive()).isFalse();
			verify(loanProductRepository).save(product);
		}
	}

	// ── getById() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("getById()")
	class GetById {

		@Test
		@DisplayName("Returns response when product found")
		void returnsResponse() {
			when(loanProductRepository.findById(productId)).thenReturn(Optional.of(product));
			when(loanProductMapper.toResponse(product)).thenReturn(response);

			LoanProductResponse result = loanProductService.getById(productId);

			assertThat(result).isSameAs(response);
		}

		@Test
		@DisplayName("Throws ResourceNotFoundException when product not found")
		void throwsWhenNotFound() {
			when(loanProductRepository.findById(productId)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> loanProductService.getById(productId))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ── list() ───────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("list()")
	class ListProducts {

		@SuppressWarnings("unchecked")
		private PagedResponse<LoanProductResponse> invokeList(Map<String, String> filters) {
			when(loanProductRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenAnswer(inv -> {
						Specification<LoanProduct> spec = inv.getArgument(0);
						Root<LoanProduct> root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
						CriteriaQuery<?> cq = mock(CriteriaQuery.class, Answers.RETURNS_DEEP_STUBS);
						CriteriaBuilder cb = mock(CriteriaBuilder.class, Answers.RETURNS_DEEP_STUBS);
						spec.toPredicate(root, cq, cb);
						return new PageImpl<>(Collections.emptyList());
					});
			return loanProductService.list(filters, Pageable.unpaged());
		}

		@Test
		@DisplayName("Returns empty page with no filters")
		void returnsEmptyPageNoFilters() {
			assertThat(invokeList(Map.of()).getContent()).isEmpty();
		}

		@ParameterizedTest(name = "{0}")
		@MethodSource("filterScenarios")
		void filterPredicateExecutesWithoutError(String scenarioName, Map<String, String> filters) {
			assertThat(invokeList(filters)).isNotNull();
		}

		static Stream<Arguments> filterScenarios() {
			return Stream.of(
					Arguments.of("String filter (name) executes LIKE predicate",
							Map.of("name", "Basic")),
					Arguments.of("String filter (description) executes LIKE predicate",
							Map.of("description", "micro")),
					Arguments.of("Enum filter (minKycLevel) executes equal predicate",
							Map.of("minKycLevel", "MIN_KYC")),
					Arguments.of("Boolean filter (isActive) executes equal predicate",
							Map.of("isActive", "true")),
					Arguments.of("Pagination params (page, size) are skipped",
							Map.of("page", "0", "size", "20")),
					Arguments.of("Unknown field keys are ignored",
							Map.of("unknownField", "value")),
					Arguments.of("Blank values are skipped",
							Map.of("name", "", "isActive", "  ")),
					Arguments.of("Multiple valid filters combined",
							Map.of("name", "Basic", "minKycLevel", "NO_KYC", "isActive", "true")));
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private LoanProductCreateRequest buildCreateRequest(
			BigDecimal minPrincipal, BigDecimal maxPrincipal,
			int minTenure, int maxTenure) {
		return LoanProductCreateRequest.builder()
				.name("Test Product")
				.minPrincipal(minPrincipal)
				.maxPrincipal(maxPrincipal)
				.minTenureMonths(minTenure)
				.maxTenureMonths(maxTenure)
				.interestRate(new BigDecimal("12.00"))
				.minKycLevel(KycLevel.MIN_KYC)
				.frequencies(List.of(RepaymentFrequency.MONTHLY))
				.build();
	}
}
