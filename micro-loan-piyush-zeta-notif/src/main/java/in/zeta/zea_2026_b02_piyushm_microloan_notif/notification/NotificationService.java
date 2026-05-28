package in.zeta.zea_2026_b02_piyushm_microloan_notif.notification;

import in.zeta.spectra.capture.SpectraLogger;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("[NOTIFICATION] Skipping email — blank recipient").attr("subject", subject).log();
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[NOTIFICATION] Email sent").attr("to", to).attr("subject", subject).log();
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed to send email", e).attr("to", to).attr("subject", subject).log();
        }
    }

    public void sendEmailWithAttachment(String to, String subject, String body,
                                        byte[] attachmentBytes, String attachmentFileName) {
        if (to == null || to.isBlank()) {
            log.warn("[NOTIFICATION] Skipping email — blank recipient").attr("subject", subject).log();
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentFileName, new ByteArrayResource(attachmentBytes));
            mailSender.send(message);
            log.info("[NOTIFICATION] Email with attachment sent")
                    .attr("to", to).attr("subject", subject)
                    .attr("attachment", attachmentFileName).log();
        } catch (MessagingException e) {
            log.error("[NOTIFICATION] Failed to send email with attachment", e)
                    .attr("to", to).attr("subject", subject).log();
        }
    }
}
