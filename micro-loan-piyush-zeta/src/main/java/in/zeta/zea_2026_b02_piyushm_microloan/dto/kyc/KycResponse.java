package in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycResponse {

    private UUID kycId;
    private UUID borrowerId;
    private String panNumber;
    private String aadhaarNumber;
    private Boolean panVerified;
    private Boolean aadhaarVerified;
    private KycLevel kycLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
