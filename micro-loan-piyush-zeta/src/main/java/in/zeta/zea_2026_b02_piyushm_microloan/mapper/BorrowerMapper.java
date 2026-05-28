package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;

@Component
public class BorrowerMapper {

    public Borrower toEntity(BorrowerCreateRequest dto) {
        return Borrower.builder()
                .fullName(dto.getFullName())
                .phoneNumber(dto.getPhoneNumber())
                .email(dto.getEmail())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .monthlyIncome(dto.getMonthlyIncome())
                .annualHouseholdIncome(dto.getAnnualHouseholdIncome())
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .pincode(dto.getPincode())
                .kycLevel(KycLevel.NO_KYC)
                .isActive(true)
                .build();
    }

    public void updateEntity(Borrower borrower, BorrowerUpdateRequest dto) {
        if (dto.getFullName() != null) borrower.setFullName(dto.getFullName());
        if (dto.getEmail() != null) borrower.setEmail(dto.getEmail());
        if (dto.getDateOfBirth() != null) borrower.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) borrower.setGender(dto.getGender());
        if (dto.getMonthlyIncome() != null) borrower.setMonthlyIncome(dto.getMonthlyIncome());
        if (dto.getAnnualHouseholdIncome() != null) borrower.setAnnualHouseholdIncome(dto.getAnnualHouseholdIncome());
        if (dto.getAddressLine1() != null) borrower.setAddressLine1(dto.getAddressLine1());
        if (dto.getAddressLine2() != null) borrower.setAddressLine2(dto.getAddressLine2());
        if (dto.getCity() != null) borrower.setCity(dto.getCity());
        if (dto.getState() != null) borrower.setState(dto.getState());
        if (dto.getPincode() != null) borrower.setPincode(dto.getPincode());
        // phone_number and kyc_level are NOT updatable
    }

    public BorrowerResponse toResponse(Borrower entity) {
        return BorrowerResponse.builder()
                .borrowerId(entity.getBorrowerId())
                .fullName(entity.getFullName())
                .phoneNumber(entity.getPhoneNumber())
                .email(entity.getEmail())
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .monthlyIncome(entity.getMonthlyIncome())
                .annualHouseholdIncome(entity.getAnnualHouseholdIncome())
                .kycLevel(entity.getKycLevel())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
