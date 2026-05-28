package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanProductController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({LoanProductController.class, GlobalExceptionHandler.class})
@DisplayName("LoanProductController")
class LoanProductControllerTest {

    private static final String BASE_URL    = "/api/v1/loan-products";
    private static final String PRODUCT_URL = BASE_URL + "/{productId}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private LoanProductService loanProductService;

    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
    }

    // ── POST /api/v1/loan-products ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/loan-products")
    class CreateProduct {

        @Test
        @DisplayName("Returns 201 CREATED with product response")
        void returns201OnSuccess() throws Exception {
            LoanProductCreateRequest request = LoanProductCreateRequest.builder()
                    .name("Personal Loan")
                    .minPrincipal(new BigDecimal("5000"))
                    .maxPrincipal(new BigDecimal("100000"))
                    .minTenureMonths(6)
                    .maxTenureMonths(36)
                    .interestRate(new BigDecimal("12.5"))
                    .penaltyRate(new BigDecimal("2.0"))
                    .minKycLevel(KycLevel.MIN_KYC)
                    .frequencies(List.of(RepaymentFrequency.MONTHLY))
                    .build();

            LoanProductResponse response = LoanProductResponse.builder()
                    .productId(productId)
                    .name("Personal Loan")
                    .isActive(true)
                    .build();

            when(loanProductService.create(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("Personal Loan"));

            verify(loanProductService).create(any());
        }

        @Test
        @DisplayName("Returns 400 when required fields are missing")
        void returns400OnValidationFailure() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /api/v1/loan-products/{productId} ───────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/loan-products/{productId}")
    class UpdateProduct {

        @Test
        @DisplayName("Returns 200 OK with updated product response")
        void returns200OnSuccess() throws Exception {
            LoanProductUpdateRequest request = LoanProductUpdateRequest.builder()
                    .name("Updated Loan")
                    .build();

            LoanProductResponse response = LoanProductResponse.builder()
                    .productId(productId)
                    .name("Updated Loan")
                    .build();

            when(loanProductService.update(eq(productId), any())).thenReturn(response);

            mockMvc.perform(put(PRODUCT_URL, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("Updated Loan"));

            verify(loanProductService).update(eq(productId), any());
        }

        @Test
        @DisplayName("Returns 404 when product not found")
        void returns404WhenNotFound() throws Exception {
            LoanProductUpdateRequest request = LoanProductUpdateRequest.builder().build();

            when(loanProductService.update(eq(productId), any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.PRD_001));

            mockMvc.perform(put(PRODUCT_URL, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /api/v1/loan-products/{productId} ────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/loan-products/{productId}")
    class DeactivateProduct {

        @Test
        @DisplayName("Returns 204 NO CONTENT on successful deactivation")
        void returns204OnSuccess() throws Exception {
            doNothing().when(loanProductService).deactivate(productId);

            mockMvc.perform(delete(PRODUCT_URL, productId))
                    .andExpect(status().isNoContent());

            verify(loanProductService).deactivate(productId);
        }

        @Test
        @DisplayName("Returns 404 when product not found")
        void returns404WhenNotFound() throws Exception {
            doThrow(new ResourceNotFoundException(ErrorCode.PRD_001))
                    .when(loanProductService).deactivate(productId);

            mockMvc.perform(delete(PRODUCT_URL, productId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loan-products/{productId} ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loan-products/{productId}")
    class GetProduct {

        @Test
        @DisplayName("Returns 200 OK with product response")
        void returns200OnSuccess() throws Exception {
            LoanProductResponse response = LoanProductResponse.builder()
                    .productId(productId)
                    .name("Personal Loan")
                    .isActive(true)
                    .build();

            when(loanProductService.getById(productId)).thenReturn(response);

            mockMvc.perform(get(PRODUCT_URL, productId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("Personal Loan"));

            verify(loanProductService).getById(productId);
        }

        @Test
        @DisplayName("Returns 404 when product not found")
        void returns404WhenNotFound() throws Exception {
            when(loanProductService.getById(productId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.PRD_001));

            mockMvc.perform(get(PRODUCT_URL, productId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loan-products ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loan-products")
    class ListProducts {

        @Test
        @DisplayName("Returns 200 OK with paged response")
        void returns200WithPage() throws Exception {
            LoanProductResponse product = LoanProductResponse.builder()
                    .productId(productId)
                    .name("Personal Loan")
                    .build();
            PagedResponse<LoanProductResponse> page = new PagedResponse<>(List.of(product), 0, 20, 1, 1);

            when(loanProductService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].name").value("Personal Loan"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(loanProductService).list(any(), any());
        }

        @Test
        @DisplayName("Returns 200 OK with filtered results when query params are present")
        void returns200WithFilters() throws Exception {
            PagedResponse<LoanProductResponse> page = new PagedResponse<>(List.of(), 0, 20, 0, 1);
            when(loanProductService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("isActive", "true"))
                    .andExpect(status().isOk());

            verify(loanProductService).list(any(), any());
        }
    }
}
