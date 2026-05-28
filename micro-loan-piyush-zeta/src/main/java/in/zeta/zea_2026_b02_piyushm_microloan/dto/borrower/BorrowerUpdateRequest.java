package in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerUpdateRequest {

    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

    @Positive(message = "Monthly income must be positive")
    @DecimalMin(value = "1000", message = "Monthly income must be at least 1000")
    private BigDecimal monthlyIncome;

    @Positive(message = "Annual household income must be positive")
    private BigDecimal annualHouseholdIncome;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Pattern(regexp = "^\\d{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    // NOTE: phone_number and kyc_level are NOT updatable via this endpoint
}
