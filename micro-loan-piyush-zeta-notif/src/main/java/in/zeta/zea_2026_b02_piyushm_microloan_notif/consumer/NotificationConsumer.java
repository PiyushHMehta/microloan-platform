package in.zeta.zea_2026_b02_piyushm_microloan_notif.consumer;

import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.event.NotificationEvent;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan.RepaymentScheduleEntry;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.notification.KfsPdfGenerator;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.notification.NotificationService;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final KfsPdfGenerator kfsPdfGenerator;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> onEvent(@RequestBody String rawPayload) {
        try {
            NotificationEvent event = JsonUtil.parseNotificationEvent(rawPayload);

            logger.info("Webhook received")
                    .attr("eventType", event.getEventType())
                    .attr("eventId", event.getEventId())
                    .log();

            dispatch(event);

        } catch (Exception e) {
            logger.error("Webhook processing failed", e)
                    .attr("error", e.getMessage())
                    .log();
        }

        // Always return 200 — Atropos retries on non-2xx
        return ResponseEntity.ok(Map.of("status", "RECEIVED"));
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(NotificationEvent event) {
        if (event == null || event.getEventType() == null) {
            logger.warn("Invalid event received — null eventType").log();
            return;
        }

        Map<String, Object> payload = event.getPayload();
        String to = payload.get("email") != null ? payload.get("email").toString() : null;

        if (to == null || to.isBlank()) {
            logger.warn("No email in payload — skipping notification")
                    .attr("eventType", event.getEventType())
                    .log();
            return;
        }

        String subject;
        String body;

        switch (event.getEventType()) {

            case "BorrowerRegistered" -> {
                subject = "Welcome to Microloan \uD83C\uDF89";
                body = "Hi " + val(payload, "firstName") + ",\n\n"
                        + "Your Microloan account has been created successfully.\n\n"
                        + "Borrower ID: " + val(payload, "borrowerId") + "\n\n"
                        + "MicroLoan Team";
            }

            case "KycStatusUpdated" -> {
                subject = "KYC Status Updated";
                body = "Hi,\n\nYour KYC level has been upgraded to: " + val(payload, "newLevel") + "\n\n"
                        + "You may now apply for loans up to your eligible limit.\n\n"
                        + "MicroLoan Team";
            }

            case "LoanApplicationApproved" -> {
                subject = "Your Loan Application is Approved \u2705";
                body = "Hi,\n\nYour loan application has been approved!\n\n"
                        + "Application ID: " + val(payload, "applicationId") + "\n"
                        + "Your Key Fact Statement (KFS) is now ready. Please log in to review and accept it.\n\n"
                        + "MicroLoan Team";
            }

            case "LoanRejected" -> {
                subject = "Loan Application Rejected \u274C";
                body = "Hi,\n\nUnfortunately your loan application has been rejected.\n\n"
                        + "Application ID: " + val(payload, "applicationId") + "\n"
                        + "Reason: " + val(payload, "rejectionReason") + "\n\n"
                        + "MicroLoan Team";
            }

            case "KfsGenerated" -> {
                subject = "Your Key Fact Statement is Ready \uD83D\uDCC4";
                body = "Hi,\n\nYour Key Fact Statement (KFS) is ready for review.\n\n"
                        + "Loan ID: " + val(payload, "loanId") + "\n"
                        + "Principal: \u20B9" + val(payload, "principal") + "\n"
                        + "Please find the attached KFS document. Review it and log in to accept it to receive your disbursement.\n\n"
                        + "MicroLoan Team";

                try {
                    KfsResponse kfs = buildKfsFromPayload(payload);
                    byte[] pdfBytes = kfsPdfGenerator.generate(kfs);
                    notificationService.sendEmailWithAttachment(
                            to, subject, body, pdfBytes, "KFS-" + val(payload, "loanId") + ".pdf");
                } catch (Exception e) {
                    logger.error("Failed to attach KFS PDF — sending plain email", e)
                            .attr("loanId", val(payload, "loanId")).log();
                    notificationService.sendEmail(to, subject, body);
                }
                return;
            }

            case "LoanIssued" -> {
                subject = "Loan Disbursed \uD83D\uDCB0";
                body = "Hi,\n\nYour loan has been disbursed successfully!\n\n"
                        + "Loan ID: " + val(payload, "loanId") + "\n"
                        + "Amount: \u20B9" + val(payload, "loanAmount") + "\n"
                        + "Disbursed At: " + val(payload, "disbursedAt") + "\n\n"
                        + "MicroLoan Team";
            }

            case "RepaymentMade" -> {
                subject = "Payment Received \uD83D\uDCB3";
                body = "Hi,\n\nWe have received your payment.\n\n"
                        + "Loan ID: " + val(payload, "loanId") + "\n"
                        + "Amount Paid: \u20B9" + val(payload, "paymentAmount") + "\n"
                        + "Reference: " + val(payload, "paymentReference") + "\n\n"
                        + "MicroLoan Team";
            }

            case "LoanOverdue" -> {
                subject = "Installment Overdue \u26A0\uFE0F";
                body = "Hi,\n\nAn installment on your loan is now overdue.\n\n"
                        + "Loan ID: " + val(payload, "loanId") + "\n"
                        + "Installment No: " + val(payload, "installmentNo") + "\n"
                        + "Due Date: " + val(payload, "dueDate") + "\n"
                        + "Please make the payment to avoid further penalties.\n\n"
                        + "MicroLoan Team";
            }

            case "LoanClosed" -> {
                subject = "Loan Fully Repaid \u2705";
                body = "Hi,\n\nCongratulations! Your loan has been fully repaid.\n\n"
                        + "Loan ID: " + val(payload, "loanId") + "\n"
                        + "Total Paid: \u20B9" + val(payload, "totalPaid") + "\n\n"
                        + "Thank you for choosing Microloan!\n\n"
                        + "MicroLoan Team";
            }

            default -> {
                logger.warn("Unknown eventType — skipping")
                        .attr("eventType", event.getEventType())
                        .log();
                return;
            }
        }

        notificationService.sendEmail(to, subject, body);
    }

    private String val(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : "N/A";
    }

    @SuppressWarnings("unchecked")
    private KfsResponse buildKfsFromPayload(Map<String, Object> payload) {
        List<RepaymentScheduleEntry> schedule = List.of();
        Object rawSchedule = payload.get("repaymentSchedule");
        if (rawSchedule instanceof List<?> list) {
            schedule = list.stream()
                    .filter(e -> e instanceof Map)
                    .map(e -> {
                        Map<String, Object> m = (Map<String, Object>) e;
                        return RepaymentScheduleEntry.builder()
                                .installmentNo(((Number) m.get("installmentNo")).intValue())
                                .dueDate(LocalDate.parse(m.get("dueDate").toString()))
                                .epiAmount(new BigDecimal(m.get("epiAmount").toString()))
                                .build();
                    })
                    .toList();
        }
        return KfsResponse.builder()
                .loanId(UUID.fromString(val(payload, "loanId")))
                .principal(new BigDecimal(val(payload, "principal")))
                .interestRate(new BigDecimal(val(payload, "interestRate")))
                .totalInterest(new BigDecimal(val(payload, "totalInterest")))
                .totalAmount(new BigDecimal(val(payload, "totalAmount")))
                .epiAmount(new BigDecimal(val(payload, "epiAmount")))
                .repaymentFrequency(RepaymentFrequency.valueOf(val(payload, "repaymentFrequency")))
                .tenureMonths(Integer.parseInt(val(payload, "tenureMonths")))
                .numInstallments(Integer.parseInt(val(payload, "numInstallments")))
                .penaltyRate(new BigDecimal(val(payload, "penaltyRate")))
                .repaymentSchedule(schedule)
                .build();
    }
}
