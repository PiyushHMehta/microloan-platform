package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.zea_2026_b02_piyushm_microloan.common.handler.GlobalExceptionHandler;
import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.service.OverdueDetectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalAdminController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import({InternalAdminController.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.internal.endpoints.enabled=true")
@DisplayName("InternalAdminController")
class InternalAdminControllerTest {

    private static final String TRIGGER_URL = "/api/v1/internal/trigger-overdue-check";

    @Autowired private MockMvc mockMvc;
    @Autowired private InternalAdminController controller;
    @MockitoBean private OverdueDetectionService overdueDetectionService;

    // ── POST /api/v1/internal/trigger-overdue-check ─────────────────────────

    @Nested
    @DisplayName("POST /api/v1/internal/trigger-overdue-check")
    class TriggerOverdueCheck {

        @AfterEach
        void resetFlag() {
            ReflectionTestUtils.setField(controller, "internalEndpointsEnabled", true);
        }

        @Test
        @DisplayName("Returns 403 FORBIDDEN when internal endpoints are disabled")
        void returns403WhenDisabled() throws Exception {
            ReflectionTestUtils.setField(controller, "internalEndpointsEnabled", false);

            mockMvc.perform(post(TRIGGER_URL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").exists());

            verify(overdueDetectionService, never()).detectAndMarkOverdue();
        }

        @Test
        @DisplayName("Returns 200 OK and triggers overdue detection when enabled")
        void returns200WhenEnabled() throws Exception {
            doNothing().when(overdueDetectionService).detectAndMarkOverdue();

            mockMvc.perform(post(TRIGGER_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists());

            verify(overdueDetectionService).detectAndMarkOverdue();
        }
    }
}
