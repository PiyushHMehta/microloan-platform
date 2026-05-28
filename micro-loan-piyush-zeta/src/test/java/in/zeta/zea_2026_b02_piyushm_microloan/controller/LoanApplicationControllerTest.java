package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.ApproveRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.RejectRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanApplicationService;
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

@WebMvcTest(LoanApplicationController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({LoanApplicationController.class, GlobalExceptionHandler.class})
@DisplayName("LoanApplicationController")
class LoanApplicationControllerTest {

    private static final String BASE_URL       = "/api/v1/loan-applications";
    private static final String APP_URL        = BASE_URL + "/{applicationId}";
    private static final String APPROVE_URL    = APP_URL + "/approval";
    private static final String REJECT_URL     = APP_URL + "/rejection";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private LoanApplicationService loanApplicationService;

    private UUID applicationId;
    private UUID borrowerId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        borrowerId    = UUID.randomUUID();
        productId     = UUID.randomUUID();
    }

    // ── POST /api/v1/loan-applications ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/loan-applications")
    class Apply {

        @Test
        @DisplayName("Returns 201 CREATED on successful application")
        void returns201OnSuccess() throws Exception {
            LoanApplicationCreateRequest request = LoanApplicationCreateRequest.builder()
                    .borrowerId(borrowerId)
                    .productId(productId)
                    .requestedAmount(new BigDecimal("50000"))
                    .requestedTenureMonths(12)
                    .repaymentFrequency(RepaymentFrequency.MONTHLY)
                    .build();

            LoanApplicationResponse response = LoanApplicationResponse.builder()
                    .applicationId(applicationId)
                    .borrowerId(borrowerId)
                    .status(ApplicationStatus.PENDING)
                    .build();

            when(loanApplicationService.apply(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(loanApplicationService).apply(any());
        }

        @Test
        @DisplayName("Returns 400 when required fields are missing")
        void returns400OnValidationFailure() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when borrower or product not found")
        void returns404WhenNotFound() throws Exception {
            LoanApplicationCreateRequest request = LoanApplicationCreateRequest.builder()
                    .borrowerId(borrowerId)
                    .productId(productId)
                    .requestedAmount(new BigDecimal("50000"))
                    .requestedTenureMonths(12)
                    .repaymentFrequency(RepaymentFrequency.MONTHLY)
                    .build();

            when(loanApplicationService.apply(any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.BRW_003));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /api/v1/loan-applications/{applicationId}/approval ───────────────

        @Nested
        @DisplayName("PUT /api/v1/loan-applications/{applicationId}/approval")
    class Approve {

        @Test
        @DisplayName("Returns 200 OK on successful approval")
        void returns200OnSuccess() throws Exception {
            ApproveRequest request = ApproveRequest.builder().reviewedBy("admin").build();

            LoanApplicationResponse response = LoanApplicationResponse.builder()
                    .applicationId(applicationId)
                    .status(ApplicationStatus.APPROVED)
                    .reviewedBy("admin")
                    .build();

            when(loanApplicationService.approve(eq(applicationId), any())).thenReturn(response);

            mockMvc.perform(put(APPROVE_URL, applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            verify(loanApplicationService).approve(eq(applicationId), any());
        }

        @Test
        @DisplayName("Returns 409 when application is not in PENDING status")
        void returns409WhenNotPending() throws Exception {
            ApproveRequest request = ApproveRequest.builder().reviewedBy("admin").build();

            when(loanApplicationService.approve(eq(applicationId), any()))
                    .thenThrow(new InvalidStateException(ErrorCode.APP_007));

            mockMvc.perform(put(APPROVE_URL, applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Returns 404 when application not found")
        void returns404WhenNotFound() throws Exception {
            ApproveRequest request = ApproveRequest.builder().reviewedBy("admin").build();

            when(loanApplicationService.approve(eq(applicationId), any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.APP_006));

                        mockMvc.perform(put(APPROVE_URL, applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /api/v1/loan-applications/{applicationId}/rejection ───────────────

        @Nested
        @DisplayName("PUT /api/v1/loan-applications/{applicationId}/rejection")
    class Reject {

        @Test
        @DisplayName("Returns 200 OK on successful rejection")
        void returns200OnSuccess() throws Exception {
            RejectRequest request = RejectRequest.builder()
                    .reviewedBy("admin")
                    .rejectionReason("Low income")
                    .build();

            LoanApplicationResponse response = LoanApplicationResponse.builder()
                    .applicationId(applicationId)
                    .status(ApplicationStatus.REJECTED)
                    .rejectionReason("Low income")
                    .build();

            when(loanApplicationService.reject(eq(applicationId), any())).thenReturn(response);

            mockMvc.perform(put(REJECT_URL, applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Low income"));

            verify(loanApplicationService).reject(eq(applicationId), any());
        }

        @Test
        @DisplayName("Returns 409 when application is not in PENDING status")
        void returns409WhenNotPending() throws Exception {
            RejectRequest request = RejectRequest.builder()
                    .reviewedBy("admin")
                    .rejectionReason("Low income")
                    .build();

            when(loanApplicationService.reject(eq(applicationId), any()))
                    .thenThrow(new InvalidStateException(ErrorCode.APP_007));

            mockMvc.perform(put(REJECT_URL, applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ── GET /api/v1/loan-applications/{applicationId} ─────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loan-applications/{applicationId}")
    class GetApplication {

        @Test
        @DisplayName("Returns 200 OK with application response")
        void returns200OnSuccess() throws Exception {
            LoanApplicationResponse response = LoanApplicationResponse.builder()
                    .applicationId(applicationId)
                    .status(ApplicationStatus.PENDING)
                    .build();

            when(loanApplicationService.getById(applicationId)).thenReturn(response);

            mockMvc.perform(get(APP_URL, applicationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(loanApplicationService).getById(applicationId);
        }

        @Test
        @DisplayName("Returns 404 when application not found")
        void returns404WhenNotFound() throws Exception {
            when(loanApplicationService.getById(applicationId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.APP_006));

            mockMvc.perform(get(APP_URL, applicationId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loan-applications ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loan-applications")
    class ListApplications {

        @Test
        @DisplayName("Returns 200 OK with paged response")
        void returns200WithPage() throws Exception {
            LoanApplicationResponse app = LoanApplicationResponse.builder()
                    .applicationId(applicationId)
                    .status(ApplicationStatus.PENDING)
                    .build();
            PagedResponse<LoanApplicationResponse> page = new PagedResponse<>(List.of(app), 0, 20, 1, 1);

            when(loanApplicationService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].applicationId").value(applicationId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(loanApplicationService).list(any(), any());
        }

        @Test
        @DisplayName("Returns 200 OK with filtered results when query params are present")
        void returns200WithFilters() throws Exception {
            PagedResponse<LoanApplicationResponse> page = new PagedResponse<>(List.of(), 0, 20, 0, 1);
            when(loanApplicationService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("status", "PENDING"))
                    .andExpect(status().isOk());

            verify(loanApplicationService).list(any(), any());
        }
    }
}
