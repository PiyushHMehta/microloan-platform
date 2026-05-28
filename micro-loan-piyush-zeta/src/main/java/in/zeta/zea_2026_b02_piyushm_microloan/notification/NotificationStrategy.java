package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;

import java.util.Map;

/**
 * Strategy contract for delivering notifications.
 *
 * <p>Each implementation handles a specific delivery mode (e.g. {@code direct},
 * {@code atropos}).  Adding a new mode requires only a new implementation —
 * {@link NotificationDispatcher} does not need to change (OCP).
 */
public interface NotificationStrategy {

    /**
     * Returns {@code true} if this strategy handles the given notification mode.
     *
     * @param mode value of the {@code notification.mode} property
     */
    boolean supports(String mode);

    /**
     * Deliver a plain-text email notification.
     */
    void dispatch(String to, String subject, String body,
                  String eventType, Map<String, Object> atroposPayload);

    /**
     * Deliver a KFS notification (may include a PDF attachment depending on implementation).
     */
    void dispatchKfsGenerated(String to, KfsResponse kfsResponse,
                               Map<String, Object> atroposPayload);
}
