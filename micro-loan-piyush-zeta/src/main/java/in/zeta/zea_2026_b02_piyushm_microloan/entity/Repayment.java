package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import jakarta.persistence.*;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentMode;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repayments are immutable — only created_at, no updated_at.
 * Hence, we don't extend BaseEntity.
 */
@Entity
@Table(name = "repayment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "repayment_id", updatable = false, nullable = false)
    private UUID repaymentId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 100)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 20)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.SUCCESS;

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
