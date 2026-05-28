package in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {

    private UUID applicationId;
    private UUID borrowerId;
    private UUID productId;
    private BigDecimal requestedAmount;
    private Integer requestedTenureMonths;
    private RepaymentFrequency repaymentFrequency;
    private String purpose;
    private ApplicationStatus status;
    private String rejectionReason;
    private String reviewedBy;
    private UUID loanId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
