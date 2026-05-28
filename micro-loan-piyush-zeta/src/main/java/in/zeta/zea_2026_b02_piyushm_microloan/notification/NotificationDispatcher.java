package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for all notifications.
 *
 * <p>Mode is controlled by {@code notification.mode} property:
 * <ul>
 *   <li>{@code direct}  — send email immediately from the service (local/dev).
 *       No Atropos event is published.</li>
 *   <li>{@code atropos} — publish the Atropos event only. The webhook consumer
 *       ({@link in.zeta.zea_2026_b02_piyushm_microloan.consumer.NotificationConsumer})
 *       handles the actual email send once Atropos delivers the event.</li>
 * </ul>
 *
 * <p>This ensures every notification type (OTP, KFS, loan updates…) follows
 * the same path — no per-service inconsistency.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final SpectraLogger log = OlympusSpectra.getLogger(NotificationDispatcher.class);

    private final SmsService smsService;
    private final List<NotificationStrategy> strategies;

    @Value("${notification.mode:atropos}")
    private String notificationMode;

    private NotificationStrategy resolveStrategy() {
        return strategies.stream()
                .filter(s -> s.supports(notificationMode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No NotificationStrategy found for mode: " + notificationMode));
    }

    // ── Generic plain-text notification ──────────────────────────────────────

    /**
     * Delegate to the active {@link NotificationStrategy}.
     */
    private void dispatch(String to, String subject, String body,
                          String eventType, Map<String, Object> atroposPayload) {
        resolveStrategy().dispatch(to, subject, body, eventType, atroposPayload);
    }

    // ── KFS-specific (email + PDF attachment) ────────────────────────────────

    public void dispatchKfsGenerated(String to, KfsResponse kfsResponse,
                                     Map<String, Object> atroposPayload) {
        resolveStrategy().dispatchKfsGenerated(to, kfsResponse, atroposPayload);
    }

    // ── Loan application notifications ───────────────────────────────────────

    public void dispatchLoanApplicationApproved(String to, UUID applicationId,
                                                Map<String, Object> atroposPayload) {
        String subject = "Loan Application Approved";
        String body = "Hi,\n\nYour loan application (ID: " + applicationId + ") has been approved.\n"
                + "Please check your account for further details.";
        dispatch(to, subject, body, "LoanApplicationApproved", atroposPayload);
    }

    public void dispatchLoanApplicationRejected(String to, UUID applicationId,
                                                String rejectionReason,
                                                Map<String, Object> atroposPayload) {
        String subject = "Loan Application Rejected";
        String body = "Hi,\n\nYour loan application (ID: " + applicationId + ") has been rejected.\n"
                + "Reason: " + rejectionReason + "\nPlease contact support for more information.";
        dispatch(to, subject, body, "LoanApplicationRejected", atroposPayload);
    }

    // ── Loan lifecycle notifications ──────────────────────────────────────────

    public void dispatchLoanIssued(String to, UUID loanId, BigDecimal principalAmount,
                                   LocalDateTime disbursedAt, Map<String, Object> atroposPayload) {
        String subject = "Your Loan Has Been Disbursed";
        String body = "Congratulations! Your loan has been disbursed.\n\n"
                + "Loan ID: " + loanId + "\n"
                + "Amount: \u20B9" + principalAmount + "\n"
                + "Disbursed At: " + (disbursedAt != null ? disbursedAt.toString() : "-")
                + "\n\nThank you for choosing MicroLoan.";
        dispatch(to, subject, body, "LoanIssued", atroposPayload);
    }

    public void dispatchLoanClosed(String to, UUID loanId, BigDecimal totalPaid,
                                   Map<String, Object> atroposPayload) {
        String subject = "Your Loan Has Been Closed";
        String body = "Your loan has been fully repaid and is now closed.\n\n"
                + "Loan ID: " + loanId + "\n"
                + "Total Paid: \u20B9" + totalPaid
                + "\n\nThank you for choosing MicroLoan.";
        dispatch(to, subject, body, "LoanClosed", atroposPayload);
    }

    // ── Borrower notifications ────────────────────────────────────────────────

    public void dispatchBorrowerRegistered(String to, String fullName, UUID borrowerId,
                                           Map<String, Object> atroposPayload) {
        String subject = "Welcome to MicroLoan!";
        String body = "Hi " + fullName + ",\n\n"
                + "Your borrower account has been successfully created.\n"
                + "Borrower ID: " + borrowerId + "\n\n"
                + "Thank you for registering with MicroLoan.";
        dispatch(to, subject, body, "BorrowerRegistered", atroposPayload);
    }

    // ── KYC / OTP notifications ───────────────────────────────────────────────

    // OTP is always sent directly — never via Atropos.
    // Reasons: (1) OTP is a short-lived credential; storing it in an event topic is a security risk.
    // (2) It has a 10-min expiry; routing through Atropos → webhook adds unpredictable latency.
    // (3) There is no Atropos topic defined for OTP events.
    public void dispatchOtpGenerated(String phone, String documentType, String otpCode) {
        smsService.sendSms(phone, "[MicroLoan] Your " + documentType + " OTP is: " + otpCode
                + ". Valid for 10 minutes. Do not share.");
    }

    // KYC outcome notifications are always sent directly (same reasoning as OTP —
    // short-lived state, no Atropos topic defined).

    public void dispatchKycVerified(String phone, String documentType, String newKycLevel) {
        smsService.sendSms(phone, "[MicroLoan] Your " + documentType + " verification was successful. "
                + "KYC level: " + newKycLevel + ".");
    }

    public void dispatchKycStatusUpdated(String to, Map<String, Object> atroposPayload) {
        String newLevel = String.valueOf(atroposPayload.get("newLevel"));
        String oldLevel = String.valueOf(atroposPayload.get("oldLevel"));
        String subject = "KYC Status Updated";
        String body = "Hi,\n\nYour KYC level has been updated from " + oldLevel + " to " + newLevel + ".\n\nMicroLoan Team";
        dispatch(to, subject, body, "KycStatusUpdated", atroposPayload);
    }

    public void dispatchOtpFailed(String phone, String documentType, int attemptsRemaining) {
        smsService.sendSms(phone, "[MicroLoan] Incorrect OTP for " + documentType
                + ". Attempts remaining: " + attemptsRemaining + ".");
    }

    public void dispatchOtpExpired(String phone, String documentType) {
        smsService.sendSms(phone, "[MicroLoan] Your " + documentType + " OTP has expired. Please request a new one.");
    }

    public void dispatchOtpMaxAttemptsExceeded(String phone, String documentType) {
        smsService.sendSms(phone, "[MicroLoan] " + documentType + " OTP blocked — too many attempts. Please re-initiate KYC.");
    }

    // ── Overdue / Repayment notifications ────────────────────────────────────

    public void dispatchInstallmentOverdue(String to, int installmentNo, UUID loanId,
                                           LocalDate dueDate, Map<String, Object> atroposPayload) {
        String subject = "Installment Overdue Notice";
        String body = "Hi,\n\nYour installment number " + installmentNo
                + " for Loan ID: " + loanId + " is overdue as of " + dueDate
                + ".\nPlease make the payment at the earliest to avoid penalties.";
        dispatch(to, subject, body, "LoanOverdue", atroposPayload);
    }

    public void dispatchRepaymentMade(String to, BigDecimal amount, UUID loanId,
                                      String paymentReference, Map<String, Object> atroposPayload) {
        String subject = "Repayment Received";
        String body = "Hi,\n\nYour repayment of \u20B9" + amount
                + " has been received for Loan ID: " + loanId
                + ".\nReference: " + paymentReference
                + "\n\nThank you for your payment.";
        dispatch(to, subject, body, "RepaymentMade", atroposPayload);
    }


}


