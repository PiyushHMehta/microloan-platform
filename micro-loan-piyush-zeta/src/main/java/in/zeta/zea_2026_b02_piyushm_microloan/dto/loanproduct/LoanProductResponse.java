package in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProductResponse {

    private UUID productId;
    private String name;
    private String description;
    private BigDecimal minPrincipal;
    private BigDecimal maxPrincipal;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;
    private BigDecimal interestRate;
    private BigDecimal penaltyRate;
    private KycLevel minKycLevel;
    private Boolean isActive;
    private List<RepaymentFrequency> frequencies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
