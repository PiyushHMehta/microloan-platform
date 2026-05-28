package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentMode;
import in.zeta.zea_2026_b02_piyushm_microloan.service.RepaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepaymentController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({RepaymentController.class, GlobalExceptionHandler.class})
@DisplayName("RepaymentController")
class RepaymentControllerTest {

    private static final String REPAYMENTS_URL = "/api/v1/repayments";
    private static final String LOAN_REPAYMENTS_URL = "/api/v1/loans/{loanId}/repayments";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private RepaymentService repaymentService;

    private UUID loanId;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
    }

    // ── POST /api/v1/repayments ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/repayments")
    class CreateRepayment {

        @Test
        @DisplayName("Returns 201 CREATED with repayment response on success")
        void returns201OnSuccess() throws Exception {
            RepaymentRequest request = RepaymentRequest.builder()
                    .loanId(loanId)
                    .amount(new BigDecimal("1000"))
                    .paymentMode(PaymentMode.UPI)
                    .build();

            UUID repaymentId = UUID.randomUUID();
            RepaymentResponse response = RepaymentResponse.builder()
                    .repaymentId(repaymentId)
                    .loanId(loanId)
                    .amount(new BigDecimal("1000"))
                    .build();

            when(repaymentService.processPayment(any())).thenReturn(response);

            mockMvc.perform(post(REPAYMENTS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.repaymentId").value(repaymentId.toString()))
                    .andExpect(jsonPath("$.loanId").value(loanId.toString()))
                    .andExpect(jsonPath("$.amount").value(1000));

            verify(repaymentService).processPayment(any());
        }

        @Test
        @DisplayName("Returns 400 when required fields are missing")
        void returns400OnValidationFailure() throws Exception {
            mockMvc.perform(post(REPAYMENTS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 4xx on overpayment (BusinessException)")
        void returns4xxOnOverpayment() throws Exception {
            RepaymentRequest request = RepaymentRequest.builder()
                    .loanId(loanId)
                    .amount(new BigDecimal("9999"))
                    .paymentMode(PaymentMode.UPI)
                    .build();

            when(repaymentService.processPayment(any()))
                    .thenThrow(new BusinessException(ErrorCode.REP_003));

            mockMvc.perform(post(REPAYMENTS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when loan not found")
        void returns404WhenLoanNotFound() throws Exception {
            RepaymentRequest request = RepaymentRequest.builder()
                    .loanId(loanId)
                    .amount(new BigDecimal("500"))
                    .paymentMode(PaymentMode.UPI)
                    .build();

            when(repaymentService.processPayment(any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(post(REPAYMENTS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loans/{loanId}/repayments ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loans/{loanId}/repayments")
    class GetRepaymentsByLoanId {

        @Test
        @DisplayName("Returns 200 OK with list of repayments")
        void returns200WithList() throws Exception {
            UUID repaymentId = UUID.randomUUID();
            RepaymentResponse repayment = RepaymentResponse.builder()
                    .repaymentId(repaymentId)
                    .loanId(loanId)
                    .amount(new BigDecimal("500"))
                    .build();

            when(repaymentService.getByLoanId(loanId)).thenReturn(List.of(repayment));

            mockMvc.perform(get(LOAN_REPAYMENTS_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].repaymentId").value(repaymentId.toString()))
                    .andExpect(jsonPath("$[0].amount").value(500));

            verify(repaymentService).getByLoanId(loanId);
        }

        @Test
        @DisplayName("Returns 200 OK with empty list when no repayments exist")
        void returnsEmptyList() throws Exception {
            when(repaymentService.getByLoanId(loanId)).thenReturn(List.of());

            mockMvc.perform(get(LOAN_REPAYMENTS_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Returns 404 when loan not found")
        void returns404WhenLoanNotFound() throws Exception {
            when(repaymentService.getByLoanId(loanId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(get(LOAN_REPAYMENTS_URL, loanId))
                    .andExpect(status().isNotFound());
        }
    }
}
