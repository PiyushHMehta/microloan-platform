package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Kyc;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

@Component
public class KycMapper {

    public KycResponse toResponse(Kyc kyc, KycLevel level) {
        return KycResponse.builder()
                .kycId(kyc.getKycId())
                .borrowerId(kyc.getBorrowerId())
                .panNumber(kyc.getPanNumber())
                .aadhaarNumber(kyc.getAadhaarNumber())
                .panVerified(kyc.getPanVerified())
                .aadhaarVerified(kyc.getAadhaarVerified())
                .kycLevel(level)
                .createdAt(kyc.getCreatedAt())
                .updatedAt(kyc.getUpdatedAt())
                .build();
    }
}
