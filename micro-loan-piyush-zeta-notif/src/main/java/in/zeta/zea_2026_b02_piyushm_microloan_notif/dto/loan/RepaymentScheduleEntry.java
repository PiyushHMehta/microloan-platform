package in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentScheduleEntry {
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal epiAmount;
}
