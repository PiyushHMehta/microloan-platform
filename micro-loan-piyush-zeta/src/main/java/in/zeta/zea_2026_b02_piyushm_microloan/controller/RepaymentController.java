package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.RepaymentProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.service.RepaymentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;

    @PostMapping("/repayments")
    @SandboxAuthorizedSync(action = "repayment.process", object = "$$repayments$$@" + RepaymentProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<RepaymentResponse> create(@Valid @RequestBody RepaymentRequest dto) {
        RepaymentResponse response = repaymentService.processPayment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/loans/{loanId}/repayments")
    @SandboxAuthorizedSync(action = "repayment.getByLoan", object = "$$repayments$$@" + RepaymentProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<List<RepaymentResponse>> getByLoanId(@PathVariable("loanId") UUID loanId) {
        return ResponseEntity.ok(repaymentService.getByLoanId(loanId));
    }
}
