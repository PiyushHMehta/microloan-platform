package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.LoanProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.PagedResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.InstallmentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.LoanResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.service.InstallmentService;
import in.zeta.zea_2026_b02_piyushm_microloan.service.LoanService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final InstallmentService installmentService;

    @GetMapping("/{loanId}")
    @SandboxAuthorizedSync(action = "loan.getById", object = "$$loans$$@" + LoanProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanResponse> getLoan(@PathVariable("loanId") UUID loanId) {
        return ResponseEntity.ok(loanService.getById(loanId));
    }

    @GetMapping
    @SandboxAuthorizedSync(action = "loan.getAll", object = "$$loans$$@" + LoanProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<PagedResponse<LoanResponse>> listLoans(
            @RequestParam(required = false) Map<String, String> filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(loanService.list(filters, pageable));
    }

    @GetMapping("/{loanId}/kfs")
    @SandboxAuthorizedSync(action = "loan.getKfs", object = "$$loans$$@" + LoanProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<KfsResponse> getKfs(@PathVariable("loanId") UUID loanId) {
        return ResponseEntity.ok(loanService.getKfs(loanId));
    }

    @PostMapping("/{loanId}/kfs/acceptance")
    @SandboxAuthorizedSync(action = "loan.acceptKfs", object = "$$loans$$@" + LoanProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<LoanResponse> acceptKfs(@PathVariable("loanId") UUID loanId) {
        return ResponseEntity.ok(loanService.acceptKfs(loanId));
    }

    @GetMapping("/{loanId}/installments")
    @SandboxAuthorizedSync(action = "loan.getInstallments", object = "$$loans$$@" + LoanProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<List<InstallmentResponse>> getInstallments(@PathVariable("loanId") UUID loanId) {
        return ResponseEntity.ok(installmentService.getByLoanId(loanId));
    }
}
