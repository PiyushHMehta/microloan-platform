package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.InstallmentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.service.InstallmentService;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({LoanController.class, GlobalExceptionHandler.class})
@DisplayName("LoanController")
class LoanControllerTest {

    private static final String BASE_URL           = "/api/v1/loans";
    private static final String LOAN_URL           = BASE_URL + "/{loanId}";
    private static final String KFS_URL            = LOAN_URL + "/kfs";
    private static final String KFS_ACCEPT_URL     = KFS_URL + "/acceptance";
    private static final String INSTALLMENTS_URL   = LOAN_URL + "/installments";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LoanService loanService;
    @MockitoBean private InstallmentService installmentService;

    private UUID loanId;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
    }

    // ── GET /api/v1/loans/{loanId} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loans/{loanId}")
    class GetLoan {

        @Test
        @DisplayName("Returns 200 OK with loan response")
        void returns200OnSuccess() throws Exception {
            LoanResponse response = LoanResponse.builder()
                    .loanId(loanId)
                    .status(LoanStatus.ACTIVE)
                    .principalAmount(new BigDecimal("50000"))
                    .build();

            when(loanService.getById(loanId)).thenReturn(response);

            mockMvc.perform(get(LOAN_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.loanId").value(loanId.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(loanService).getById(loanId);
        }

        @Test
        @DisplayName("Returns 404 when loan not found")
        void returns404WhenNotFound() throws Exception {
            when(loanService.getById(loanId)).thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(get(LOAN_URL, loanId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loans ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loans")
    class ListLoans {

        @Test
        @DisplayName("Returns 200 OK with paged response")
        void returns200WithPage() throws Exception {
            LoanResponse loan = LoanResponse.builder().loanId(loanId).build();
            PagedResponse<LoanResponse> page = new PagedResponse<>(List.of(loan), 0, 20, 1, 1);

            when(loanService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].loanId").value(loanId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(loanService).list(any(), any());
        }

        @Test
        @DisplayName("Returns 200 OK with filtered results when query params are present")
        void returns200WithFilters() throws Exception {
            PagedResponse<LoanResponse> page = new PagedResponse<>(List.of(), 0, 20, 0, 1);
            when(loanService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("status", "ACTIVE"))
                    .andExpect(status().isOk());

            verify(loanService).list(any(), any());
        }
    }

    // ── GET /api/v1/loans/{loanId}/kfs ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loans/{loanId}/kfs")
    class GetKfs {

        @Test
        @DisplayName("Returns 200 OK with KFS response")
        void returns200OnSuccess() throws Exception {
            KfsResponse kfs = new KfsResponse();
            when(loanService.getKfs(loanId)).thenReturn(kfs);

            mockMvc.perform(get(KFS_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

            verify(loanService).getKfs(loanId);
        }

        @Test
        @DisplayName("Returns 404 when loan or KFS snapshot not found")
        void returns404WhenNotFound() throws Exception {
            when(loanService.getKfs(loanId)).thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(get(KFS_URL, loanId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /api/v1/loans/{loanId}/kfs/acceptance ──────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/loans/{loanId}/kfs/acceptance")
    class AcceptKfs {

        @Test
        @DisplayName("Returns 200 OK with loan response after KFS acceptance")
        void returns200OnSuccess() throws Exception {
            LoanResponse response = LoanResponse.builder()
                    .loanId(loanId)
                    .status(LoanStatus.ACTIVE)
                    .build();

            when(loanService.acceptKfs(loanId)).thenReturn(response);

            mockMvc.perform(post(KFS_ACCEPT_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loanId").value(loanId.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(loanService).acceptKfs(loanId);
        }

        @Test
        @DisplayName("Returns 409 when loan is not in KFS_PENDING status")
        void returns409WhenNotKfsPending() throws Exception {
            when(loanService.acceptKfs(loanId)).thenThrow(new InvalidStateException(ErrorCode.LOAN_002));

            mockMvc.perform(post(KFS_ACCEPT_URL, loanId))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Returns 404 when loan not found")
        void returns404WhenNotFound() throws Exception {
            when(loanService.acceptKfs(loanId)).thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(post(KFS_ACCEPT_URL, loanId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/loans/{loanId}/installments ────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/loans/{loanId}/installments")
    class GetInstallments {

        @Test
        @DisplayName("Returns 200 OK with installments list")
        void returns200WithList() throws Exception {
            UUID installmentId = UUID.randomUUID();
            InstallmentResponse inst = InstallmentResponse.builder()
                    .installmentId(installmentId)
                    .installmentNo(1)
                    .build();

            when(installmentService.getByLoanId(loanId)).thenReturn(List.of(inst));

            mockMvc.perform(get(INSTALLMENTS_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$[0].installmentId").value(installmentId.toString()))
                    .andExpect(jsonPath("$[0].installmentNo").value(1));

            verify(installmentService).getByLoanId(loanId);
        }

        @Test
        @DisplayName("Returns 200 OK with empty list when no installments exist")
        void returnsEmptyList() throws Exception {
            when(installmentService.getByLoanId(loanId)).thenReturn(List.of());

            mockMvc.perform(get(INSTALLMENTS_URL, loanId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Returns 404 when loan not found")
        void returns404WhenNotFound() throws Exception {
            when(installmentService.getByLoanId(loanId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.LOAN_001));

            mockMvc.perform(get(INSTALLMENTS_URL, loanId))
                    .andExpect(status().isNotFound());
        }
    }
}
