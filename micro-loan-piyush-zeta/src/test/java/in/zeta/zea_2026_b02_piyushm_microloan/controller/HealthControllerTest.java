package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.zea_2026_b02_piyushm_microloan.config.WebMvcSliceTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@ContextConfiguration(classes = WebMvcSliceTestApplication.class)
@Import(HealthController.class)
@DisplayName("HealthController")
class HealthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /health returns 200 OK")
    void healthApiReturns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
