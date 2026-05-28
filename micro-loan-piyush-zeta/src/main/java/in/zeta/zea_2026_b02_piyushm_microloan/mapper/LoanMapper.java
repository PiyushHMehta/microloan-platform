package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;

import java.math.BigDecimal;

@Component
public class LoanMapper {

    public LoanResponse toResponse(Loan loan) {
        BigDecimal remaining = loan.getTotalPayable().subtract(loan.getTotalPaid());
        return LoanResponse.builder()
                .loanId(loan.getLoanId())
                .borrowerId(loan.getBorrowerId())
                .applicationId(loan.getApplicationId())
                .productId(loan.getProductId())
                .principalAmount(loan.getPrincipalAmount())
                .interestRate(loan.getInterestRate())
                .tenureMonths(loan.getTenureMonths())
                .totalAmount(loan.getTotalAmount())
                .totalPenaltyAmount(loan.getTotalPenaltyAmount())
                .totalPayable(loan.getTotalPayable())
                .totalPaid(loan.getTotalPaid())
                .remaining(remaining)
                .epiAmount(loan.getEpiAmount())
                .repaymentFrequency(loan.getRepaymentFrequency())
                .penaltyRate(loan.getPenaltyRate())
                .status(loan.getStatus())
                .kfsGeneratedAt(loan.getKfsGeneratedAt())
                .kfsAcknowledgedAt(loan.getKfsAcknowledgedAt())
                .disbursedAt(loan.getDisbursedAt())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .build();
    }
}
