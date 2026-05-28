package in.zeta.zea_2026_b02_piyushm_microloan.controller;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.zea_2026_b02_piyushm_microloan.provider.KycProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycInitiateResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.kyc.KycVerifyOtpRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.service.KycService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrowers/{borrowerId}/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;


    @PostMapping
    @SandboxAuthorizedSync(action = "kyc.initiate", object = "$$kyc$$@" + KycProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<KycInitiateResponse> initiateKyc(
            @PathVariable("borrowerId") UUID borrowerId,
            @Valid @RequestBody KycInitiateRequest request) {
        KycInitiateResponse response = kycService.initiateKyc(borrowerId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }


    @PostMapping("/otp-verification")
    @SandboxAuthorizedSync(action = "kyc.verifyOtp", object = "$$kyc$$@" + KycProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<KycResponse> verifyOtp(
            @PathVariable("borrowerId") UUID borrowerId,
            @Valid @RequestBody KycVerifyOtpRequest request) {
        KycResponse response = kycService.verifyOtp(borrowerId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @SandboxAuthorizedSync(action = "kyc.getByBorrowerId", object = "$$kyc$$@" + KycProvider.OBJECT_TYPE + ".cipher.app", tenantID = "1001034")
    public ResponseEntity<KycResponse> getKyc(@PathVariable("borrowerId") UUID borrowerId) {
        KycResponse response = kycService.getByBorrowerId(borrowerId);
        return ResponseEntity.ok(response);
    }
}
