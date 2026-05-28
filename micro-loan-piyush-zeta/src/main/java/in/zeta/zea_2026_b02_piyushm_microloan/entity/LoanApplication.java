package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import jakarta.persistence.*;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.ApplicationStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "application_id", updatable = false, nullable = false)
    private UUID applicationId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_tenure_months", nullable = false)
    private Integer requestedTenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
}
