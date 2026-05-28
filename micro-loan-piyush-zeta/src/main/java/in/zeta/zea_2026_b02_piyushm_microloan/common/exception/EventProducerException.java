package in.zeta.zea_2026_b02_piyushm_microloan.common.exception;

public class EventProducerException extends RuntimeException {

    public EventProducerException(String message) {
        super(message);
    }

    public EventProducerException(String message, Throwable cause) {
        super(message, cause);
    }
}
