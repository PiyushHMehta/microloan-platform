package in.zeta.zea_2026_b02_piyushm_microloan_notif.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.event.NotificationEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import olympus.trace.OlympusSpectra;

/**
 * Static JSON parsing utility for Atropos webhook payloads.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SpectraLogger logger = OlympusSpectra.getLogger(JsonUtil.class);

    public static NotificationEvent parseNotificationEvent(String json) {
        try {
            logger.info("Parsing NotificationEvent from webhook payload").log();
            NotificationEvent event = MAPPER.readValue(json, NotificationEvent.class);
            logger.info("Parsed NotificationEvent")
                    .attr("eventType", event.getEventType())
                    .attr("eventId", event.getEventId())
                    .log();
            return event;
        } catch (Exception e) {
            logger.error("Failed to parse NotificationEvent: " + e.getMessage(), e).log();
            throw new RuntimeException("Failed to parse NotificationEvent from webhook payload", e);
        }
    }
}
