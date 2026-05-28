package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.service.BorrowerService;
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

@WebMvcTest(BorrowerController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({BorrowerController.class, GlobalExceptionHandler.class})
@DisplayName("BorrowerController")
class BorrowerControllerTest {

    private static final String BASE_URL = "/api/v1/borrowers";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BorrowerService borrowerService;

    private UUID borrowerId;

    @BeforeEach
    void setUp() {
        borrowerId = UUID.randomUUID();
    }

    // ── POST /api/v1/borrowers ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/borrowers")
    class CreateBorrower {

        @Test
        @DisplayName("Returns 201 CREATED with borrower response on success")
        void returns201OnSuccess() throws Exception {
            BorrowerCreateRequest request = BorrowerCreateRequest.builder()
                    .fullName("Piyush Mehta")
                    .phoneNumber("9876543210")
                    .email("piyush@example.com")
                    .monthlyIncome(new BigDecimal("50000"))
                    .annualHouseholdIncome(new BigDecimal("600000"))
                    .build();

            BorrowerResponse response = BorrowerResponse.builder()
                    .borrowerId(borrowerId)
                    .fullName("Piyush Mehta")
                    .email("piyush@example.com")
                    .build();

            when(borrowerService.create(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()))
                    .andExpect(jsonPath("$.fullName").value("Piyush Mehta"))
                    .andExpect(jsonPath("$.email").value("piyush@example.com"));

            verify(borrowerService).create(any());
        }

        @Test
        @DisplayName("Returns 400 when required fields are missing")
        void returns400OnValidationFailure() throws Exception {
            // Empty object — missing required fields (fullName, phoneNumber, monthlyIncome, annualHouseholdIncome)
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 409 CONFLICT when phone number already registered")
        void returns409OnDuplicate() throws Exception {
            BorrowerCreateRequest request = BorrowerCreateRequest.builder()
                    .fullName("Piyush Mehta")
                    .phoneNumber("9876543210")
                    .monthlyIncome(new BigDecimal("50000"))
                    .annualHouseholdIncome(new BigDecimal("600000"))
                    .build();

            when(borrowerService.create(any())).thenThrow(new DuplicateResourceException(ErrorCode.BRW_002));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ── PUT /api/v1/borrowers/{borrowerId} ────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/borrowers/{borrowerId}")
    class UpdateBorrower {

        @Test
        @DisplayName("Returns 200 OK with updated borrower response")
        void returns200OnSuccess() throws Exception {
            BorrowerUpdateRequest request = BorrowerUpdateRequest.builder()
                    .fullName("Updated Name")
                    .build();

            BorrowerResponse response = BorrowerResponse.builder()
                    .borrowerId(borrowerId)
                    .fullName("Updated Name")
                    .build();

            when(borrowerService.update(eq(borrowerId), any())).thenReturn(response);

            mockMvc.perform(put(BASE_URL + "/{borrowerId}", borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()))
                    .andExpect(jsonPath("$.fullName").value("Updated Name"));

            verify(borrowerService).update(eq(borrowerId), any());
        }

        @Test
        @DisplayName("Returns 404 when borrower not found")
        void returns404WhenNotFound() throws Exception {
            BorrowerUpdateRequest request = BorrowerUpdateRequest.builder().build();

            when(borrowerService.update(eq(borrowerId), any()))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.BRW_003));

            mockMvc.perform(put(BASE_URL + "/{borrowerId}", borrowerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/borrowers/{borrowerId} ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/borrowers/{borrowerId}")
    class GetBorrower {

        @Test
        @DisplayName("Returns 200 OK with borrower response")
        void returns200OnSuccess() throws Exception {
            BorrowerResponse response = BorrowerResponse.builder()
                    .borrowerId(borrowerId)
                    .fullName("Piyush Mehta")
                    .build();

            when(borrowerService.getById(borrowerId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{borrowerId}", borrowerId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()))
                    .andExpect(jsonPath("$.fullName").value("Piyush Mehta"));

            verify(borrowerService).getById(borrowerId);
        }

        @Test
        @DisplayName("Returns 404 when borrower not found")
        void returns404WhenNotFound() throws Exception {
            when(borrowerService.getById(borrowerId))
                    .thenThrow(new ResourceNotFoundException(ErrorCode.BRW_003));

            mockMvc.perform(get(BASE_URL + "/{borrowerId}", borrowerId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/borrowers ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/borrowers")
    class ListBorrowers {

        @Test
        @DisplayName("Returns 200 OK with paged response")
        void returns200WithPage() throws Exception {
            BorrowerResponse borrower = BorrowerResponse.builder()
                    .borrowerId(borrowerId)
                    .fullName("Piyush Mehta")
                    .build();
            PagedResponse<BorrowerResponse> page = new PagedResponse<>(List.of(borrower), 0, 20, 1, 1);

            when(borrowerService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content[0].fullName").value("Piyush Mehta"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(borrowerService).list(any(), any());
        }

        @Test
        @DisplayName("Returns 200 OK with filtered results when query params are present")
        void returns200WithFilters() throws Exception {
            PagedResponse<BorrowerResponse> page = new PagedResponse<>(List.of(), 0, 20, 0, 1);
            when(borrowerService.list(any(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("status", "ACTIVE"))
                    .andExpect(status().isOk());

            verify(borrowerService).list(any(), any());
        }
    }
}
