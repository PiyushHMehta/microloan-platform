package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "kyc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kyc extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "kyc_id", updatable = false, nullable = false)
    private UUID kycId;

    @Column(name = "borrower_id", nullable = false, unique = true)
    private UUID borrowerId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pan_number", unique = true, length = 64)
    private String panNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "aadhaar_number", unique = true, length = 64)
    private String aadhaarNumber;

    @Column(name = "pan_verified")
    @Builder.Default
    private Boolean panVerified = false;

    @Column(name = "aadhaar_verified")
    @Builder.Default
    private Boolean aadhaarVerified = false;
}
