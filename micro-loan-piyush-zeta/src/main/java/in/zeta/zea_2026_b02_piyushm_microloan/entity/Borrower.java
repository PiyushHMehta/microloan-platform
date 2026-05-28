package in.zeta.zea_2026_b02_piyushm_microloan.entity;

import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.Gender;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "borrower")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Borrower extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "borrower_id", updatable = false, nullable = false)
    private UUID borrowerId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "full_name", nullable = false, length = 256)
    private String fullName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", nullable = false, unique = true, length = 64)
    private String phoneNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "monthly_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "annual_household_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualHouseholdIncome;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false, length = 20)
    @Builder.Default
    private KycLevel kycLevel = KycLevel.NO_KYC;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line1", length = 512)
    private String addressLine1;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line2", length = 512)
    private String addressLine2;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "city", length = 256)
    private String city;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "state", length = 256)
    private String state;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pincode", length = 64)
    private String pincode;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
