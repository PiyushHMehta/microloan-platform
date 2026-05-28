package in.zeta.zea_2026_b02_piyushm_microloan_notif.controller;

import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/health")
public class HealthController {

    public final SpectraLogger logger = OlympusSpectra.getLogger(HealthController.class);

    @GetMapping()
    public ResponseEntity<Void> healthApi() {
        logger.info("Health api hit").attr("time", new Date(System.currentTimeMillis())).log();
        return ResponseEntity.ok().build();
    }
}
