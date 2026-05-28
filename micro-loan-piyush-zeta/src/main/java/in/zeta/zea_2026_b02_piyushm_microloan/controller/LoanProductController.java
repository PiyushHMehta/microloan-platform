package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanProductProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanproduct.LoanProductUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanProductService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    @PostMapping
    @SandboxAuthorizedSync(action = "loanProduct.create", object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanProductResponse> createProduct(
            @Valid @RequestBody LoanProductCreateRequest request) {
        LoanProductResponse response = loanProductService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{productId}")
    @SandboxAuthorizedSync(action = "loanProduct.update", object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanProductResponse> updateProduct(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody LoanProductUpdateRequest request) {
        LoanProductResponse response = loanProductService.update(productId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{productId}")
    @SandboxAuthorizedSync(action = "loanProduct.deactivate", object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<Void> deactivateProduct(@PathVariable("productId") UUID productId) {
        loanProductService.deactivate(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}")
    @SandboxAuthorizedSync(action = "loanProduct.getById", object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanProductResponse> getProduct(@PathVariable("productId") UUID productId) {
        LoanProductResponse response = loanProductService.getById(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @SandboxAuthorizedSync(action = "loanProduct.getAll", object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<PagedResponse<LoanProductResponse>> listProducts(
            @RequestParam(required = false) Map<String, String> filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PagedResponse<LoanProductResponse> response = loanProductService.list(filters, pageable);
        return ResponseEntity.ok(response);
    }
}
