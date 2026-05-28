package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import in.zeta.spectra.capture.SpectraLogger;
import jakarta.annotation.PostConstruct;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends SMS messages via Twilio.
 *
 * <p>OTP messages are always delivered via SMS — Twilio is active in all environments.
 * Supply credentials via environment variables: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN,
 * TWILIO_FROM_NUMBER.
 */
@Service
public class SmsService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(SmsService.class);

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
        log.info("[SMS] Twilio initialized").attr("from", fromNumber).log();
    }

    /**
     * Send an SMS to the given phone number.
     * Phone number must be in E.164 format (e.g. {@code +919876543210}).
     * If the number is blank, the call is a no-op.
     */
    public void sendSms(String toPhone, String body) {
        if (toPhone == null || toPhone.isBlank()) {
            log.warn("[SMS] Skipping SMS — blank recipient").log();
            return;
        }

        String e164 = toE164(toPhone);
        try {
            Message message = Message.creator(
                    new PhoneNumber(e164),
                    new PhoneNumber(fromNumber),
                    body
            ).create();
            log.info("[SMS] Sent").attr("to", e164).attr("sid", message.getSid()).log();
        } catch (Exception e) {
            log.error("[SMS] Failed to send SMS", e).attr("to", e164).log();
        }
    }

    /**
     * Converts a 10-digit Indian mobile number to E.164 format (+91XXXXXXXXXX).
     * If the number already starts with '+', it is returned as-is.
     */
    private String toE164(String phone) {
        if (phone.startsWith("+")) {
            return phone;
        }
        // Strip leading zeros or country code duplicates
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return "+" + digits;
    }
}
