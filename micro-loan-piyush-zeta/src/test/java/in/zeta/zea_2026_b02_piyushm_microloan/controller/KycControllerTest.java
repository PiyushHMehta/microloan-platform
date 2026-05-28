package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycVerifyOtpRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.service.KycService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({KycController.class, GlobalExceptionHandler.class})
@DisplayName("KycController")
class KycControllerTest {

    private static final String BASE_URL = "/api/v1/borrowers/{borrowerId}/kyc";
    private static final String OTP_URL  = BASE_URL + "/otp-verification";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private KycService kycService;

    private UUID borrowerId;

    @BeforeEach
    void setUp() {
        borrowerId = UUID.randomUUID();
    }

    // ── POST /api/v1/borrowers/{borrowerId}/kyc ───────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/borrowers/{borrowerId}/kyc")
    class InitiateKyc {

        @Test
        @DisplayName("Returns 200 OK with OTP initiation response")
        void returns200OnSuccess() throws Exception {
            KycInitiateRequest request = KycInitiateRequest.builder()
                    .documentType("PAN")
                    .documentValue("ABCDE1234F")
                    .build();

            KycInitiateResponse response = KycInitiateResponse.builder()
                    .message("OTP sent")
                    .documentType("PAN")
                    .expiresInSeconds(600)
                    .otp("123456")
                    .build();

            when(kycService.initiateKyc(eq(borrowerId), any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("OTP sent"))
                    .andExpect(jsonPath("$.documentType").value("PAN"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(600));

            verify(kycService).initiateKyc(eq(borrowerId), any());
        }

        @Test
        @DisplayName("Returns 400 when required fields are missing")
        void returns400OnValidationFailure() throws Exception {
            mockMvc.perform(post(BASE_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 4xx on invalid document (BusinessException)")
        void returns4xxOnInvalidDocument() throws Exception {
            KycInitiateRequest request = KycInitiateRequest.builder()
                    .documentType("PAN")
                    .documentValue("INVALID")
                    .build();

            when(kycService.initiateKyc(eq(borrowerId), any()))
                    .thenThrow(new BusinessException(ErrorCode.KYC_001));

            mockMvc.perform(post(BASE_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when borrower not found")
        void returns404WhenBorrowerNotFound() throws Exception {
            KycInitiateRequest request = KycInitiateRequest.builder()
                    .documentType("PAN")
                    .documentValue("ABCDE1234F")
                    .build();

            when(kycService.initiateKyc(eq(borrowerId), any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.BRW_003));

            mockMvc.perform(post(BASE_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /api/v1/borrowers/{borrowerId}/kyc/otp-verification ──────────────

    @Nested
    @DisplayName("POST /api/v1/borrowers/{borrowerId}/kyc/otp-verification")
    class VerifyOtp {

        @Test
        @DisplayName("Returns 200 OK with KYC response on correct OTP")
        void returns200OnSuccess() throws Exception {
            KycVerifyOtpRequest request = new KycVerifyOtpRequest();
            request.setDocumentType("PAN");
            request.setOtp("123456");
            UUID kycId = UUID.randomUUID();
            KycResponse response = KycResponse.builder()
                    .kycId(kycId)
                    .borrowerId(borrowerId)
                    .build();

            when(kycService.verifyOtp(eq(borrowerId), any())).thenReturn(response);

            mockMvc.perform(post(OTP_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kycId").value(kycId.toString()))
                    .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()));

            verify(kycService).verifyOtp(eq(borrowerId), any());
        }

        @Test
        @DisplayName("Returns 4xx on wrong or expired OTP (BusinessException)")
        void returns4xxOnWrongOtp() throws Exception {
            KycVerifyOtpRequest request = new KycVerifyOtpRequest();
            request.setDocumentType("PAN");
            request.setOtp("000000");

            when(kycService.verifyOtp(eq(borrowerId), any()))
                    .thenThrow(new BusinessException(ErrorCode.KYC_009));

            mockMvc.perform(post(OTP_URL, borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/v1/borrowers/{borrowerId}/kyc ────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/borrowers/{borrowerId}/kyc")
    class GetKyc {

        @Test
        @DisplayName("Returns 200 OK with KYC response")
        void returns200OnSuccess() throws Exception {
            UUID kycId = UUID.randomUUID();
            KycResponse response = KycResponse.builder()
                    .kycId(kycId)
                    .borrowerId(borrowerId)
                    .build();

            when(kycService.getByBorrowerId(borrowerId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL, borrowerId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.kycId").value(kycId.toString()))
                    .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()));

            verify(kycService).getByBorrowerId(borrowerId);
        }

        @Test
        @DisplayName("Returns 404 when KYC not found for borrower")
        void returns404WhenNotFound() throws Exception {
            when(kycService.getByBorrowerId(borrowerId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.KYC_006));

            mockMvc.perform(get(BASE_URL, borrowerId))
                    .andExpect(status().isNotFound());
        }
    }
}
