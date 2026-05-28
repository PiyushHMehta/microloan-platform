package in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationCreateRequest {

    @NotNull(message = "Borrower ID is required")
    private UUID borrowerId;

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be positive")
    private BigDecimal requestedAmount;

    @NotNull(message = "Requested tenure is required")
    @Positive(message = "Tenure must be positive")
    private Integer requestedTenureMonths;

    @NotNull(message = "Repayment frequency is required")
    private RepaymentFrequency repaymentFrequency;

    private String purpose;
}
