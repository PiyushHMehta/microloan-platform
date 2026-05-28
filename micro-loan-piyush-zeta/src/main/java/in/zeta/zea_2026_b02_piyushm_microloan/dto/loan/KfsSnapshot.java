package in.zeta.zea_2026_b02_piyushm_microloan.dto.loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KfsSnapshot {
    private UUID loanId;
    private UUID borrowerId;
    private String borrowerName;
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
    private LocalDateTime generatedAt;
}
