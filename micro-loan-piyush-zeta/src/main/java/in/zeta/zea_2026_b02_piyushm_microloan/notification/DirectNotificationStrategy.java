package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sends notifications immediately within the service process (no Atropos event).
 * Intended for local / dev environments ({@code notification.mode=direct}).
 */
@Component
@RequiredArgsConstructor
public class DirectNotificationStrategy implements NotificationStrategy {

    private static final SpectraLogger log = OlympusSpectra.getLogger(DirectNotificationStrategy.class);

    private final NotificationService notificationService;
    private final KfsPdfGenerator kfsPdfGenerator;

    @Override
    public boolean supports(String mode) {
        return "direct".equalsIgnoreCase(mode);
    }

    @Override
    public void dispatch(String to, String subject, String body,
                         String eventType, Map<String, Object> atroposPayload) {
        notificationService.sendEmail(to, subject, body);
    }

    @Override
    public void dispatchKfsGenerated(String to, KfsResponse kfsResponse,
                                     Map<String, Object> atroposPayload) {
        try {
            byte[] pdfBytes = kfsPdfGenerator.generate(kfsResponse);
            String subject = "Your Key Fact Statement is Ready \uD83D\uDCC4";
            String body = "Hi,\n\nYour Key Fact Statement (KFS) is ready for review.\n\n"
                    + "Loan ID: " + kfsResponse.getLoanId() + "\n"
                    + "Principal: \u20B9" + kfsResponse.getPrincipal() + "\n"
                    + "Please find the attached KFS document. Review it and accept it to receive your disbursement.\n\n"
                    + "MicroLoan Team";
            notificationService.sendEmailWithAttachment(
                    to, subject, body, pdfBytes,
                    "KFS-" + kfsResponse.getLoanId() + ".pdf");
        } catch (Exception e) {
            log.error("Failed to send KFS PDF email — falling back to plain email", e)
                    .attr("loanId", String.valueOf(kfsResponse.getLoanId())).log();
            notificationService.sendEmail(to,
                    "Your Key Fact Statement is Ready",
                    "Your KFS for loan " + kfsResponse.getLoanId() + " is ready. Please log in to view it.");
        }
    }
}
