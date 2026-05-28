package in.zeta.zea_2026_b02_piyushm_microloan.dto.loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentResponse {

    private UUID installmentId;
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal epiAmount;
    private BigDecimal penaltyAmount;
    private BigDecimal totalDue;
    private BigDecimal amountPaid;
    private BigDecimal remaining;
    private InstallmentStatus status;
    private Boolean penaltyApplied;
    private LocalDateTime paidAt;
}
