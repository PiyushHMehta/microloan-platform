package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.InternalAdminProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import in.zeta.zea_2026_b02_piyushm_microloan.service.OverdueDetectionService;

import java.util.Map;

/**
 * Internal admin endpoints for manual triggering of scheduled jobs.
 * Only available when app.internal.endpoints.enabled=true (default: false).
 *
 * DO NOT expose in production. Will be replaced by proper admin tooling
 * or Atropos cron subscription triggers during Zeta integration.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalAdminController {

    private final OverdueDetectionService overdueDetectionService;

    @Value("${app.internal.endpoints.enabled:false}")
    private boolean internalEndpointsEnabled;

    @PostMapping("/trigger-overdue-check")
    @SandboxAuthorizedSync(action = "internalAdmin.triggerOverdueCheck", object = "$$internal$$@" + InternalAdminProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<Map<String, String>> triggerOverdueCheck() {
        if (!internalEndpointsEnabled) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Internal endpoints are disabled"));
        }
        overdueDetectionService.detectAndMarkOverdue();
        return ResponseEntity.ok(Map.of("status", "Overdue check triggered successfully"));
    }
}
