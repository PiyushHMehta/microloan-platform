package in.zeta.zea_2026_b02_piyushm_microloan.scheduler;

import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan.service.OverdueDetectionService;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OverdueDetectionScheduler {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(OverdueDetectionScheduler.class);
    private static final String ERROR = "error";

    private final OverdueDetectionService overdueDetectionService;

    @Scheduled(cron = "${app.scheduler.overdue-cron:0 0 2 * * ?}")
    public void detectOverduePayments() {
        logger.info("Scheduler: starting overdue detection + penalty accumulation").log();
        try {
            overdueDetectionService.detectAndMarkOverdue();
            logger.info("Scheduler: overdue detection complete").log();
        } catch (Exception e) {
            logger.error("Scheduler: overdue detection failed").attr(ERROR, e.getMessage()).log();
        }
    }
}
