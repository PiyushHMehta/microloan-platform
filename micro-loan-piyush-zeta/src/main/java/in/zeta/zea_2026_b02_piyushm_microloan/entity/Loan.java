package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsSnapshot;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "loan_id", updatable = false, nullable = false)
    private UUID loanId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "application_id", unique = true)
    private UUID applicationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_penalty_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPenaltyAmount = BigDecimal.ZERO;

    @Column(name = "total_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPayable;

    @Column(name = "total_paid", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "epi_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal epiAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "penalty_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal penaltyRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.KFS_PENDING;

    @Column(name = "kfs_generated_at")
    private LocalDateTime kfsGeneratedAt;

    @Column(name = "kfs_acknowledged_at")
    private LocalDateTime kfsAcknowledgedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kfs_snapshot", columnDefinition = "jsonb")
    private KfsSnapshot kfsSnapshot;

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;
}
