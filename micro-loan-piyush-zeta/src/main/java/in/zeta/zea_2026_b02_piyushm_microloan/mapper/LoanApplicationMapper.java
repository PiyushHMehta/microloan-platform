package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanApplication;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;

import java.util.UUID;

@Component
public class LoanApplicationMapper {

    public LoanApplication toEntity(LoanApplicationCreateRequest dto) {
        return LoanApplication.builder()
                .borrowerId(dto.getBorrowerId())
                .productId(dto.getProductId())
                .requestedAmount(dto.getRequestedAmount())
                .requestedTenureMonths(dto.getRequestedTenureMonths())
                .repaymentFrequency(dto.getRepaymentFrequency())
                .purpose(dto.getPurpose())
                .status(ApplicationStatus.PENDING)
                .build();
    }

    public LoanApplicationResponse toResponse(LoanApplication app, UUID loanId) {
        return LoanApplicationResponse.builder()
                .applicationId(app.getApplicationId())
                .borrowerId(app.getBorrowerId())
                .productId(app.getProductId())
                .requestedAmount(app.getRequestedAmount())
                .requestedTenureMonths(app.getRequestedTenureMonths())
                .repaymentFrequency(app.getRepaymentFrequency())
                .purpose(app.getPurpose())
                .status(app.getStatus())
                .rejectionReason(app.getRejectionReason())
                .reviewedBy(app.getReviewedBy())
                .loanId(loanId)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
