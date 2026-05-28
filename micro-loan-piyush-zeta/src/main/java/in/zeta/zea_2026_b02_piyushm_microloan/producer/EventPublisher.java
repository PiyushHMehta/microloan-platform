package in.zeta.zea_2026_b02_piyushm_microloan.producer;

import in.zeta.oms.atropos.response.PublishStatus;
import in.zeta.spectra.capture.SpectraLogger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import olympus.pubsub.model.TopicScope;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(EventPublisher.class);

    private final EventProducer eventProducer;

    // ── Topic config (eventType → Atropos topic name) ─────────────────────────

    @Value("${atropos.borrowerRegistered.topic}")
    private String borrowerRegisteredTopic;
    @Value("${atropos.kycStatusUpdated.topic}")
    private String kycStatusUpdatedTopic;
    @Value("${atropos.loanApplicationApproved.topic}")
    private String loanApplicationApprovedTopic;
    @Value("${atropos.loanRejected.topic}")
    private String loanRejectedTopic;
    @Value("${atropos.kfsGenerated.topic}")
    private String kfsGeneratedTopic;
    @Value("${atropos.loanIssued.topic}")
    private String loanIssuedTopic;
    @Value("${atropos.repaymentMade.topic}")
    private String repaymentMadeTopic;
    @Value("${atropos.loanOverdue.topic}")
    private String loanOverdueTopic;
    @Value("${atropos.loanClosed.topic}")
    private String loanClosedTopic;

    private Map<String, String> topicByEvent;

    @PostConstruct
    void initTopicMap() {
        topicByEvent = Map.of(
                "BorrowerRegistered",      borrowerRegisteredTopic,
                "KycStatusUpdated",        kycStatusUpdatedTopic,
                "LoanApplicationApproved", loanApplicationApprovedTopic,
                "LoanRejected",            loanRejectedTopic,
                "KfsGenerated",            kfsGeneratedTopic,
                "LoanIssued",              loanIssuedTopic,
                "RepaymentMade",           repaymentMadeTopic,
                "LoanOverdue",             loanOverdueTopic,
                "LoanClosed",              loanClosedTopic
        );
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public void publish(String eventType, Map<String, Object> payload) {
        String topic = topicByEvent.get(eventType);
        if (topic == null) {
            logger.warn("No Atropos topic configured for event — skipping publish")
                    .attr("eventType", eventType).log();
            return;
        }
        publishToTopic(eventType, payload, topic);
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    private void publishToTopic(String eventType, Map<String, Object> payload, String topic) {
        try {
            String objectId = payload.getOrDefault("loanId",
                    payload.getOrDefault("borrowerId", "id")).toString();

            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventId", objectId);
            envelope.put("eventType", eventType);
            envelope.put("occurredAt", Instant.now().atOffset(ZoneOffset.UTC).toString());
            envelope.put("payload", payload);

            logger.info("Publishing event")
                    .attr("eventType", eventType)
                    .attr("eventId", objectId)
                    .attr("topic", topic)
                    .log();

            eventProducer.publishEvent(objectId, envelope, topic, TopicScope.SYSTEM)
                    .thenAccept(response -> {
                        if (response != null && response.getStatus() == PublishStatus.FAILED) {
                            logger.error("Publish failed")
                                    .attr("eventType", eventType)
                                    .attr("status", response.getStatus())
                                    .log();
                        } else {
                            logger.info("Publish success")
                                    .attr("eventType", eventType)
                                    .attr("topic", topic)
                                    .log();
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Publish exception")
                                .attr("eventType", eventType)
                                .attr("error", e.getMessage())
                                .log();
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Failed to publish event")
                    .attr("eventType", eventType)
                    .attr("error", e.getMessage())
                    .log();
        }
    }
}
