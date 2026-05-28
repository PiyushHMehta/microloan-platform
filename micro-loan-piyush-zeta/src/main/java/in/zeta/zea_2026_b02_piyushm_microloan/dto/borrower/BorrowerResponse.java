package in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.Gender;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerResponse {

    private UUID borrowerId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private Gender gender;
    private BigDecimal monthlyIncome;
    private BigDecimal annualHouseholdIncome;
    private KycLevel kycLevel;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
