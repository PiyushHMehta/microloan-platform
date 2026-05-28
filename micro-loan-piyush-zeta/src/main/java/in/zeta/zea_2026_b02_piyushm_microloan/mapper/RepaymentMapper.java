package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Repayment;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class RepaymentMapper {

    public Repayment toEntity(RepaymentRequest dto, String paymentReference) {
        return Repayment.builder()
                .loanId(dto.getLoanId())
                .amount(dto.getAmount())
                .paymentReference(paymentReference)
                .paymentMode(dto.getPaymentMode())
                .paymentStatus(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
    }

    public RepaymentResponse toResponse(Repayment repayment, LoanStatus loanStatus, BigDecimal remainingBalance) {
        return RepaymentResponse.builder()
                .repaymentId(repayment.getRepaymentId())
                .loanId(repayment.getLoanId())
                .amount(repayment.getAmount())
                .paymentReference(repayment.getPaymentReference())
                .paymentMode(repayment.getPaymentMode())
                .paymentStatus(repayment.getPaymentStatus())
                .loanStatus(loanStatus)
                .remainingBalance(remainingBalance)
                .paidAt(repayment.getPaidAt())
                .createdAt(repayment.getCreatedAt())
                .build();
    }
}
