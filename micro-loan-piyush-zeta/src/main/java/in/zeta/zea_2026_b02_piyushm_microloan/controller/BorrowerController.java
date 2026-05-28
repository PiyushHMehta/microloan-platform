package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.BorrowerProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.borrower.BorrowerUpdateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.service.BorrowerService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrowers")
@RequiredArgsConstructor
public class BorrowerController {

    private final BorrowerService borrowerService;

    @PostMapping
    @SandboxAuthorizedSync(action = "borrower.create", object = "$$borrowers$$@" + BorrowerProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<BorrowerResponse> createBorrower(
            @Valid @RequestBody BorrowerCreateRequest request) {
        BorrowerResponse response = borrowerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{borrowerId}")
    @SandboxAuthorizedSync(action = "borrower.update", object = "$$borrowers$$@" + BorrowerProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<BorrowerResponse> updateBorrower(
            @PathVariable("borrowerId") UUID borrowerId,
            @Valid @RequestBody BorrowerUpdateRequest request) {
        BorrowerResponse response = borrowerService.update(borrowerId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{borrowerId}")
    @SandboxAuthorizedSync(action = "borrower.getById", object = "$$borrowers$$@" + BorrowerProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<BorrowerResponse> getBorrower(@PathVariable("borrowerId") UUID borrowerId) {
        BorrowerResponse response = borrowerService.getById(borrowerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @SandboxAuthorizedSync(action = "borrower.getAll", object = "$$borrowers$$@" + BorrowerProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<PagedResponse<BorrowerResponse>> listBorrowers(
            @RequestParam(required = false) Map<String, String> filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PagedResponse<BorrowerResponse> response = borrowerService.list(filters, pageable);
        return ResponseEntity.ok(response);
    }
}
