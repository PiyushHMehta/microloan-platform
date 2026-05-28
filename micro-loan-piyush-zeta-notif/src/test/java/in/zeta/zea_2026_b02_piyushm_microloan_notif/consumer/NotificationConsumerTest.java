package in.zeta.zea_2026_b02_piyushm_microloan_notif.consumer;

import in.zeta.zea_2026_b02_piyushm_microloan_notif.notification.KfsPdfGenerator;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
class NotificationConsumerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private KfsPdfGenerator kfsPdfGenerator;

    private static final String WEBHOOK_URL = "/api/notifications/webhook";
    private static final String EMAIL = "john@example.com";
    private static final String BORROWER_ID = UUID.randomUUID().toString();
    private static final String LOAN_ID = UUID.randomUUID().toString();
    private static final String APP_ID = UUID.randomUUID().toString();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String eventWith(String eventType, String extraFields) {
        return """
                {
                  "eventId": "evt-001",
                  "eventType": "%s",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {
                    "email": "%s",
                    %s
                  }
                }
                """.formatted(eventType, EMAIL, extraFields);
    }

    private String commonExtraFields() {
        return """
                "firstName": "John",
                "borrowerId": "%s",
                "applicationId": "%s",
                "loanId": "%s",
                "loanAmount": "50000",
                "disbursedAt": "2026-05-12",
                "paymentAmount": "5000",
                "paymentReference": "REF123",
                "installmentNo": "2",
                "dueDate": "2026-06-01",
                "totalPaid": "50000",
                "rejectionReason": "Low credit score",
                "newLevel": "FULL_KYC",
                "oldLevel": "MIN_KYC"
                """.formatted(BORROWER_ID, APP_ID, LOAN_ID);
    }

    // ── 1. All known simple event types return 200 and dispatch email ─────────

    @ParameterizedTest(name = "eventType={0}")
    @ValueSource(strings = {
            "BorrowerRegistered", "KycStatusUpdated",
            "LoanApplicationApproved", "LoanRejected",
            "LoanIssued", "RepaymentMade",
            "LoanOverdue", "LoanClosed"
    })
    void webhook_knownEventTypes_return200AndSendEmail(String eventType) throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventWith(eventType, commonExtraFields())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(notificationService, atLeastOnce()).sendEmail(eq(EMAIL), anyString(), anyString());
    }

    // ── 2. KfsGenerated → PDF generated and sent as attachment ───────────────

    @Test
    void webhook_kfsGenerated_sendsPdfAttachment() throws Exception {
        when(kfsPdfGenerator.generate(any())).thenReturn(new byte[]{1, 2, 3});

        String body = """
                {
                  "eventId": "evt-kfs",
                  "eventType": "KfsGenerated",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {
                    "email": "%s",
                    "loanId": "%s",
                    "principal": "50000",
                    "interestRate": "12.0",
                    "totalInterest": "6000",
                    "totalAmount": "56000",
                    "epiAmount": "4667",
                    "repaymentFrequency": "MONTHLY",
                    "tenureMonths": "12",
                    "numInstallments": "12",
                    "penaltyRate": "2.0",
                    "repaymentSchedule": [
                      {"installmentNo": 1, "dueDate": "2026-06-01", "epiAmount": "4667"}
                    ]
                  }
                }
                """.formatted(EMAIL, LOAN_ID);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(kfsPdfGenerator).generate(any());
        verify(notificationService).sendEmailWithAttachment(
                eq(EMAIL), anyString(), anyString(), any(byte[].class), contains(LOAN_ID));
    }

    // ── 3. KfsGenerated → PDF failure falls back to plain email ──────────────

    @Test
    void webhook_kfsGenerated_pdfFailure_fallsBackToPlainEmail() throws Exception {
        when(kfsPdfGenerator.generate(any())).thenThrow(new RuntimeException("PDF error"));

        String body = """
                {
                  "eventId": "evt-kfs-fail",
                  "eventType": "KfsGenerated",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {
                    "email": "%s",
                    "loanId": "%s",
                    "principal": "50000",
                    "interestRate": "12.0",
                    "totalInterest": "6000",
                    "totalAmount": "56000",
                    "epiAmount": "4667",
                    "repaymentFrequency": "MONTHLY",
                    "tenureMonths": "12",
                    "numInstallments": "12",
                    "penaltyRate": "2.0",
                    "repaymentSchedule": []
                  }
                }
                """.formatted(EMAIL, LOAN_ID);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(notificationService).sendEmail(eq(EMAIL), anyString(), anyString());
        verify(notificationService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
    }

    // ── 4. Missing email in payload → notification skipped ───────────────────

    @Test
    void webhook_missingEmail_skipsNotification() throws Exception {
        String body = """
                {
                  "eventId": "evt-noemail",
                  "eventType": "BorrowerRegistered",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {
                    "borrowerId": "%s",
                    "firstName": "John"
                  }
                }
                """.formatted(BORROWER_ID);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }

    // ── 5. Blank email in payload → notification skipped ─────────────────────

    @Test
    void webhook_blankEmail_skipsNotification() throws Exception {
        String body = """
                {
                  "eventId": "evt-blankemail",
                  "eventType": "LoanIssued",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {
                    "email": "   ",
                    "loanId": "%s",
                    "loanAmount": "50000",
                    "disbursedAt": "2026-05-12"
                  }
                }
                """.formatted(LOAN_ID);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }

    // ── 6. Null eventType → swallowed, returns 200 ───────────────────────────

    @Test
    void webhook_nullEventType_returns200() throws Exception {
        String body = """
                {
                  "eventId": "evt-nulltype",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": { "email": "%s" }
                }
                """.formatted(EMAIL);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }

    // ── 7. Unknown eventType → logged and skipped ────────────────────────────

    @Test
    void webhook_unknownEventType_skipsAndReturns200() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventWith("UnhandledEvent", "\"foo\": \"bar\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }

    // ── 8. Malformed JSON → swallowed, returns 200 (Atropos must not get 5xx) ─

    @Test
    void webhook_malformedJson_returns200() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("this is not json at all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }

    // ── 9. Empty payload object → does not crash ─────────────────────────────

    @Test
    void webhook_emptyPayloadObject_returns200() throws Exception {
        String body = """
                {
                  "eventId": "evt-empty",
                  "eventType": "BorrowerRegistered",
                  "occurredAt": "2026-05-12T10:00:00",
                  "payload": {}
                }
                """;

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verifyNoInteractions(notificationService);
    }
}
