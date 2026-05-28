package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import jakarta.persistence.*;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loan_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "product_id", updatable = false, nullable = false)
    private UUID productId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal minPrincipal;

    @Column(name = "max_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxPrincipal;

    @Column(name = "min_tenure_months", nullable = false)
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months", nullable = false)
    private Integer maxTenureMonths;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "penalty_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal penaltyRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_kyc_level", nullable = false, length = 20)
    private KycLevel minKycLevel;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "loanProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanProductFrequency> frequencies = new ArrayList<>();
}
