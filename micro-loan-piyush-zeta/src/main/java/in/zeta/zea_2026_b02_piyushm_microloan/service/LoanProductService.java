package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProduct;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProductFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.KycLevel;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.RepaymentFrequency;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.LoanProductMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductFrequencyRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanProductRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.config.EncryptionService;


import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanProductService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(LoanProductService.class);
    private static final String FIELD_PRODUCT_ID = "productId";

    private final LoanProductRepository loanProductRepository;
    private final LoanProductFrequencyRepository frequencyRepository;
    private final LoanProductMapper loanProductMapper;
    private final EncryptionService encryptionService;

    @Transactional
    public LoanProductResponse create(LoanProductCreateRequest dto) {
        // Business validations
        if (dto.getFrequencies() == null || dto.getFrequencies().isEmpty()) {
            throw new BusinessException(ErrorCode.PRD_004);
        }
        if (dto.getMinPrincipal().compareTo(dto.getMaxPrincipal()) > 0) {
            throw new BusinessException(ErrorCode.PRD_002);
        }
        if (dto.getMinTenureMonths() > dto.getMaxTenureMonths()) {
            throw new BusinessException(ErrorCode.PRD_002);
        }

        LoanProduct product = loanProductMapper.toEntity(dto);

        product = loanProductRepository.save(product);

        // Save frequencies
        List<LoanProductFrequency> freqs = new ArrayList<>();
        for (RepaymentFrequency freq : dto.getFrequencies()) {
            LoanProductFrequency f = LoanProductFrequency.builder()
                    .loanProduct(product)
                    .frequency(freq)
                    .build();
            freqs.add(f);
        }
        frequencyRepository.saveAll(freqs);
        product.setFrequencies(freqs);

        log.info("Loan product created").attr(FIELD_PRODUCT_ID, product.getProductId()).log();

        return toResponse(product);
    }

    @Transactional
    public LoanProductResponse update(UUID productId, LoanProductUpdateRequest dto) {
        LoanProduct product = loanProductRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRD_001));

        if (!product.getIsActive()) {
            throw new BusinessException(ErrorCode.PRD_003);
        }

        // Apply non-null fields
        loanProductMapper.updateEntity(product, dto);

        // Re-validate ranges
        if (product.getMinPrincipal().compareTo(product.getMaxPrincipal()) > 0) {
            throw new BusinessException(ErrorCode.PRD_002);
        }
        if (product.getMinTenureMonths() > product.getMaxTenureMonths()) {
            throw new BusinessException(ErrorCode.PRD_002);
        }

        // Replace frequencies if provided — null means no change, empty list is rejected
        if (dto.getFrequencies() != null && dto.getFrequencies().isEmpty()) {
            throw new BusinessException(ErrorCode.PRD_004);
        }
        if (dto.getFrequencies() != null && !dto.getFrequencies().isEmpty()) {
            product.getFrequencies().clear();
            loanProductRepository.saveAndFlush(product); // flush orphan removal

            for (RepaymentFrequency freq : dto.getFrequencies()) {
                LoanProductFrequency f = LoanProductFrequency.builder()
                        .loanProduct(product)
                        .frequency(freq)
                        .build();
                product.getFrequencies().add(f);
            }
        }

        loanProductRepository.saveAndFlush(product);

        log.info("Loan product updated").attr(FIELD_PRODUCT_ID, productId).log();

        return toResponse(product);
    }

    @Transactional
    public void deactivate(UUID productId) {
        LoanProduct product = loanProductRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRD_001));

        if (!product.getIsActive()) {
            throw new BusinessException(ErrorCode.PRD_003);
        }

        product.setIsActive(false);
        loanProductRepository.save(product);

        log.info("Loan product deactivated").attr(FIELD_PRODUCT_ID, productId).log();
    }

    @Transactional(readOnly = true)
    public LoanProductResponse getById(UUID productId) {
        LoanProduct product = loanProductRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRD_001));

        return toResponse(product);
    }

        // Only enum, boolean, and string fields are filterable (string fields are encrypted)
        private static final Set<String> PRODUCT_ENUM_FIELDS = Set.of("minKycLevel");
        private static final Set<String> PRODUCT_BOOLEAN_FIELDS = Set.of("isActive");
        private static final Set<String> PRODUCT_STRING_FIELDS = Set.of("name", "description");
        private static final Set<String> PRODUCT_FILTERABLE_FIELDS = Set.of("minKycLevel", "isActive", "name", "description");
        private static final Set<String> PAGINATION_PARAMS = Set.of("page", "size", "sort");

    @Transactional(readOnly = true)
    public PagedResponse<LoanProductResponse> list(Map<String, String> filters, Pageable pageable) {

        Specification<LoanProduct> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            filters.forEach((key, value) -> {
                if (PAGINATION_PARAMS.contains(key) || value == null || value.isBlank()) return;
                if (!PRODUCT_FILTERABLE_FIELDS.contains(key)) return;

                // Only equality filtering allowed for encrypted fields
                if (PRODUCT_STRING_FIELDS.contains(key)) {
                    // String fields are NOT encrypted in DB, so allow LIKE (case-insensitive)
                    predicates.add(cb.like(cb.lower(root.get(key)), "%" + value.toLowerCase() + "%"));
                } else if (PRODUCT_ENUM_FIELDS.contains(key)) {
                    predicates.add(cb.equal(root.get(key), KycLevel.valueOf(value.toUpperCase())));
                } else if (PRODUCT_BOOLEAN_FIELDS.contains(key)) {
                    predicates.add(cb.equal(root.get(key), Boolean.parseBoolean(value)));
                }
            });
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LoanProduct> page = loanProductRepository.findAll(spec, pageable);

        return PagedResponse.<LoanProductResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private LoanProductResponse toResponse(LoanProduct product) {
        return loanProductMapper.toResponse(product);
    }
}
