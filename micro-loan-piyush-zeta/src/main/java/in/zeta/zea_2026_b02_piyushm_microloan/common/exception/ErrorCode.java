package in.zeta.zea_2026_b02_piyushm_microloan.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Borrower (BRW_xxx)
    BRW_001(HttpStatus.BAD_REQUEST, "Invalid phone number format"),
    BRW_002(HttpStatus.CONFLICT, "Phone number already registered"),
    BRW_003(HttpStatus.NOT_FOUND, "Borrower not found"),
    BRW_004(HttpStatus.BAD_REQUEST, "Monthly income must be positive"),
    BRW_005(HttpStatus.BAD_REQUEST, "Borrower is deactivated"),

    // KYC (KYC_xxx)
    KYC_001(HttpStatus.BAD_REQUEST, "Invalid PAN format (expected: ABCDE1234F)"),
    KYC_002(HttpStatus.BAD_REQUEST, "Invalid Aadhaar format (expected: 12 digits, all numeric)"),
    KYC_003(HttpStatus.CONFLICT, "PAN already registered to another borrower"),
    KYC_004(HttpStatus.CONFLICT, "Aadhaar already registered to another borrower"),
    KYC_006(HttpStatus.NOT_FOUND, "KYC record not found for borrower"),
    KYC_008(HttpStatus.BAD_REQUEST, "OTP expired or not found"),
    KYC_009(HttpStatus.BAD_REQUEST, "Invalid OTP"),
    KYC_010(HttpStatus.TOO_MANY_REQUESTS, "Max OTP verification attempts exceeded"),

    // Loan Product (PRD_xxx)
    PRD_001(HttpStatus.NOT_FOUND, "Loan product not found"),
    PRD_002(HttpStatus.BAD_REQUEST, "min_principal must be <= max_principal"),
    PRD_003(HttpStatus.BAD_REQUEST, "Product is inactive"),
    PRD_004(HttpStatus.BAD_REQUEST, "At least one repayment frequency required"),

    // Loan Application (APP_xxx)
    APP_001(HttpStatus.BAD_REQUEST, "Requested amount outside product range"),
    APP_002(HttpStatus.BAD_REQUEST, "Requested tenure outside product range"),
    APP_003(HttpStatus.BAD_REQUEST, "Repayment frequency not allowed for this product"),
    APP_004(HttpStatus.UNPROCESSABLE_ENTITY, "Borrower KYC level insufficient"),
    APP_005(HttpStatus.UNPROCESSABLE_ENTITY, "EPI exceeds 50% of monthly income"),
    APP_006(HttpStatus.NOT_FOUND, "Loan application not found"),
    APP_007(HttpStatus.CONFLICT, "Application is not in PENDING status"),
    APP_008(HttpStatus.BAD_REQUEST, "Borrower is not active"),
    APP_009(HttpStatus.BAD_REQUEST, "Loan product is not active"),
    APP_010(HttpStatus.UNPROCESSABLE_ENTITY, "Annual household income exceeds RBI microfinance limit"),
    APP_011(HttpStatus.CONFLICT, "Borrower already has an active loan"),

    // Loan (LOAN_xxx)
    LOAN_001(HttpStatus.NOT_FOUND, "Loan not found"),
    LOAN_002(HttpStatus.CONFLICT, "Loan is not in KFS_PENDING status"),
    LOAN_003(HttpStatus.CONFLICT, "Loan already exists for this application"),
    LOAN_004(HttpStatus.CONFLICT, "Loan is already closed"),
    LOAN_005(HttpStatus.CONFLICT, "KFS already accepted"),

    // Repayment (REP_xxx)
    REP_001(HttpStatus.BAD_REQUEST, "Payment amount must be positive"),
    REP_002(HttpStatus.CONFLICT, "Loan is closed or not yet disbursed"),
    REP_003(HttpStatus.BAD_REQUEST, "Payment amount exceeds remaining balance"),
    REP_004(HttpStatus.CONFLICT, "Duplicate payment reference"),
    REP_005(HttpStatus.BAD_REQUEST, "Invalid payment mode"),
    REP_006(HttpStatus.NOT_FOUND, "Repayment not found");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
