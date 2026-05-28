package in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycInitiateRequest {
    @NotBlank(message = "Document type is required")
    private String documentType; // PAN or AADHAAR

    @NotBlank(message = "Document value is required")
    private String documentValue;
}
