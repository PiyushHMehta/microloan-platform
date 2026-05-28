package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import jakarta.persistence.*;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.util.UUID;

@Entity
@Table(name = "loan_product_frequency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProductFrequency {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct loanProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RepaymentFrequency frequency;
}
