package in.zeta.zea_2026_b02_piyushm_microloan.dto.loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {

    private UUID loanId;
    private UUID borrowerId;
    private UUID applicationId;
    private UUID productId;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal totalAmount;
    private BigDecimal totalPenaltyAmount;
    private BigDecimal totalPayable;
    private BigDecimal totalPaid;
    private BigDecimal remaining;
    private BigDecimal epiAmount;
    private RepaymentFrequency repaymentFrequency;
    private BigDecimal penaltyRate;
    private LoanStatus status;
    private LocalDateTime kfsGeneratedAt;
    private LocalDateTime kfsAcknowledgedAt;
    private LocalDateTime disbursedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
