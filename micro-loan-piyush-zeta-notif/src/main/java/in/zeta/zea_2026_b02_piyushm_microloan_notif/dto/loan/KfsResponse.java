package in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan;

import in.zeta.zea_2026_b02_piyushm_microloan_notif.enums.RepaymentFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KfsResponse {

    private UUID loanId;
    private BigDecimal principal;
    private BigDecimal interestRate;
    private BigDecimal totalInterest;
    private BigDecimal totalAmount;
    private BigDecimal epiAmount;
    private RepaymentFrequency repaymentFrequency;
    private Integer tenureMonths;
    private Integer numInstallments;
    private BigDecimal penaltyRate;
    private List<RepaymentScheduleEntry> repaymentSchedule;
    private String status;
}
