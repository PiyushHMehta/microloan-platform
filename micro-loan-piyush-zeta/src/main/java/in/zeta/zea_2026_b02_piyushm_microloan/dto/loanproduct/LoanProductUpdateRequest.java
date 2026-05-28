package in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProductUpdateRequest {

    @Size(max = 100)
    private String name;

    private String description;

    @Positive(message = "Min principal must be positive")
    private BigDecimal minPrincipal;

    @Positive(message = "Max principal must be positive")
    private BigDecimal maxPrincipal;

    @Positive(message = "Min tenure must be positive")
    private Integer minTenureMonths;

    @Positive(message = "Max tenure must be positive")
    private Integer maxTenureMonths;

    @Positive(message = "Interest rate must be positive")
    @DecimalMax(value = "100", message = "Interest rate cannot exceed 100%")
    private BigDecimal interestRate;

    @PositiveOrZero(message = "Penalty rate must be zero or positive")
    private BigDecimal penaltyRate;

    private KycLevel minKycLevel;

    private List<RepaymentFrequency> frequencies;
}
