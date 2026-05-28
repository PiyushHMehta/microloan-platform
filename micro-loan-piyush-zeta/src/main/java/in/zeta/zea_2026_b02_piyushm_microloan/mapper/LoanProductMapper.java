package in.zeta.zea_2026_b02_piyushm_microloan.mapper;

import org.springframework.stereotype.Component;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProduct;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProductFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;

import java.math.BigDecimal;
import java.util.List;

@Component
public class LoanProductMapper {

    public LoanProduct toEntity(LoanProductCreateRequest dto) {
        return LoanProduct.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .minPrincipal(dto.getMinPrincipal())
                .maxPrincipal(dto.getMaxPrincipal())
                .minTenureMonths(dto.getMinTenureMonths())
                .maxTenureMonths(dto.getMaxTenureMonths())
                .interestRate(dto.getInterestRate())
                .penaltyRate(dto.getPenaltyRate() != null ? dto.getPenaltyRate() : BigDecimal.ZERO)
                .minKycLevel(dto.getMinKycLevel())
                .isActive(true)
                .build();
    }

    public void updateEntity(LoanProduct product, LoanProductUpdateRequest dto) {
        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getMinPrincipal() != null) product.setMinPrincipal(dto.getMinPrincipal());
        if (dto.getMaxPrincipal() != null) product.setMaxPrincipal(dto.getMaxPrincipal());
        if (dto.getMinTenureMonths() != null) product.setMinTenureMonths(dto.getMinTenureMonths());
        if (dto.getMaxTenureMonths() != null) product.setMaxTenureMonths(dto.getMaxTenureMonths());
        if (dto.getInterestRate() != null) product.setInterestRate(dto.getInterestRate());
        if (dto.getPenaltyRate() != null) product.setPenaltyRate(dto.getPenaltyRate());
        if (dto.getMinKycLevel() != null) product.setMinKycLevel(dto.getMinKycLevel());
    }

    public LoanProductResponse toResponse(LoanProduct product) {
        List<RepaymentFrequency> freqs = product.getFrequencies().stream()
                .map(LoanProductFrequency::getFrequency)
                .toList();

        return LoanProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .minPrincipal(product.getMinPrincipal())
                .maxPrincipal(product.getMaxPrincipal())
                .minTenureMonths(product.getMinTenureMonths())
                .maxTenureMonths(product.getMaxTenureMonths())
                .interestRate(product.getInterestRate())
                .penaltyRate(product.getPenaltyRate())
                .minKycLevel(product.getMinKycLevel())
                .isActive(product.getIsActive())
                .frequencies(freqs)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
