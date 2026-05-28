package in.zeta.zea_2026_b02_piyushm_microloan.common.exception;

import lombok.Getter;

@Getter
public class InvalidStateException extends RuntimeException {

    private final ErrorCode errorCode;

    public InvalidStateException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public InvalidStateException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
