package in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentMode;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentRequest {

    @NotNull(message = "loanId is required")
    private UUID loanId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "paymentMode is required")
    private PaymentMode paymentMode;
}
