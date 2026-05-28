package in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment;

import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentMode;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentResponse {

    private UUID repaymentId;
    private UUID loanId;
    private BigDecimal amount;
    private String paymentReference;
    private PaymentMode paymentMode;
    private PaymentStatus paymentStatus;
    private LoanStatus loanStatus;
    private BigDecimal remainingBalance;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
