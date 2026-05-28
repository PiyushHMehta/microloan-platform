package in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycInitiateResponse {

    private String message;
    private String documentType;
    private int expiresInSeconds;
    private String otp;
}
