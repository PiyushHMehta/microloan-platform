package in.zeta.zea_2026_b02_piyushm_microloan.scheduler;

import in.zeta.zea_2026_b02_piyushm_microloan.service.OverdueDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OverdueDetectionSchedulerTest {

    @Mock
    private OverdueDetectionService overdueDetectionService;

    @InjectMocks
    private OverdueDetectionScheduler scheduler;

    @Test
    void callsService() {
        scheduler.detectOverduePayments();
        verify(overdueDetectionService).detectAndMarkOverdue();
    }

    @Test
    void exceptionIsCaughtAndDoesNotPropagate() {
        doThrow(new RuntimeException("boom")).when(overdueDetectionService).detectAndMarkOverdue();
        assertDoesNotThrow(() -> scheduler.detectOverduePayments());
        verify(overdueDetectionService).detectAndMarkOverdue();
    }
}

