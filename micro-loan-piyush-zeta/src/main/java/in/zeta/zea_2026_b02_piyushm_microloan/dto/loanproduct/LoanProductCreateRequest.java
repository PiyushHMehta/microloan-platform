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
public class LoanProductCreateRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "Min principal is required")
    @Positive(message = "Min principal must be positive")
    private BigDecimal minPrincipal;

    @NotNull(message = "Max principal is required")
    @Positive(message = "Max principal must be positive")
    private BigDecimal maxPrincipal;

    @NotNull(message = "Min tenure is required")
    @Positive(message = "Min tenure must be positive")
    private Integer minTenureMonths;

    @NotNull(message = "Max tenure is required")
    @Positive(message = "Max tenure must be positive")
    private Integer maxTenureMonths;

    @NotNull(message = "Interest rate is required")
    @Positive(message = "Interest rate must be positive")
    @DecimalMax(value = "100", message = "Interest rate cannot exceed 100%")
    private BigDecimal interestRate;

    @PositiveOrZero(message = "Penalty rate must be zero or positive")
    private BigDecimal penaltyRate;

    @NotNull(message = "Min KYC level is required")
    private KycLevel minKycLevel;

    @NotEmpty(message = "At least one frequency is required")
    private List<RepaymentFrequency> frequencies;
}
