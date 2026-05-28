package in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.event;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class NotificationEvent {

    private String eventId;
    private String eventType;
    private String occurredAt;
    private Map<String, Object> payload = new HashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        /* Silently absorb unknown Atropos envelope fields for forward compatibility */
    }
}
