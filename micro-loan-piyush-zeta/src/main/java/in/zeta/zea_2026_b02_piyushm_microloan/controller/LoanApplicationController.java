package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanApplicationProvider;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.ApproveRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationCreateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.LoanApplicationResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication.RejectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanApplicationService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping
    @SandboxAuthorizedSync(action = "loanApplication.apply", object = "$$loan-applications$$@" + LoanApplicationProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanApplicationResponse> apply(
            @Valid @RequestBody LoanApplicationCreateRequest request) {
        LoanApplicationResponse response = loanApplicationService.apply(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{applicationId}/approval")
    @SandboxAuthorizedSync(action = "loanApplication.approve", object = "$$loan-applications$$@" + LoanApplicationProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanApplicationResponse> approve(
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ApproveRequest request) {
        LoanApplicationResponse response = loanApplicationService.approve(applicationId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{applicationId}/rejection")
    @SandboxAuthorizedSync(action = "loanApplication.reject", object = "$$loan-applications$$@" + LoanApplicationProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanApplicationResponse> reject(
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody RejectRequest request) {
        LoanApplicationResponse response = loanApplicationService.reject(applicationId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{applicationId}")
    @SandboxAuthorizedSync(action = "loanApplication.getById", object = "$$loan-applications$$@" + LoanApplicationProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanApplicationResponse> getApplication(@PathVariable("applicationId") UUID applicationId) {
        LoanApplicationResponse response = loanApplicationService.getById(applicationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @SandboxAuthorizedSync(action = "loanApplication.getAll", object = "$$loan-applications$$@" + LoanApplicationProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<PagedResponse<LoanApplicationResponse>> listApplications(
            @RequestParam(required = false) Map<String, String> filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PagedResponse<LoanApplicationResponse> response = loanApplicationService.list(filters, pageable);
        return ResponseEntity.ok(response);
    }
}
