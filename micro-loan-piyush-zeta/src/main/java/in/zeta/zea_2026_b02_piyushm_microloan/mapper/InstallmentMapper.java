package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.InstallmentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;

import java.math.BigDecimal;

@Component
public class InstallmentMapper {

    public InstallmentResponse toResponse(Installment inst) {
        BigDecimal remaining = inst.getTotalDue().subtract(inst.getAmountPaid());
        return InstallmentResponse.builder()
                .installmentId(inst.getInstallmentId())
                .installmentNo(inst.getInstallmentNo())
                .dueDate(inst.getDueDate())
                .epiAmount(inst.getEpiAmount())
                .penaltyAmount(inst.getPenaltyAmount())
                .totalDue(inst.getTotalDue())
                .amountPaid(inst.getAmountPaid())
                .remaining(remaining)
                .status(inst.getStatus())
                .penaltyApplied(inst.getPenaltyApplied())
                .paidAt(inst.getPaidAt())
                .build();
    }
}
