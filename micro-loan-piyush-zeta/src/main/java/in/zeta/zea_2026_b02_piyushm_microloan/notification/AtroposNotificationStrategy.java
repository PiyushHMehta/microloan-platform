package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes Atropos events for all notifications.
 * The webhook consumer handles the actual delivery once Atropos delivers the event.
 * Default mode ({@code notification.mode=atropos}).
 */
@Component
@RequiredArgsConstructor
public class AtroposNotificationStrategy implements NotificationStrategy {

    private static final SpectraLogger log = OlympusSpectra.getLogger(AtroposNotificationStrategy.class);

    private final EventPublisher eventPublisher;

    @Override
    public boolean supports(String mode) {
        return "atropos".equalsIgnoreCase(mode);
    }

    @Override
    public void dispatch(String to, String subject, String body,
                         String eventType, Map<String, Object> atroposPayload) {
        log.info("Dispatching notification via Atropos").attr("eventType", eventType).log();
        eventPublisher.publish(eventType, atroposPayload);
    }

    @Override
    public void dispatchKfsGenerated(String to, KfsResponse kfsResponse,
                                     Map<String, Object> atroposPayload) {
        log.info("Dispatching notification via Atropos").attr("eventType", "KfsGenerated").log();
        eventPublisher.publish("KfsGenerated", atroposPayload);
    }
}
