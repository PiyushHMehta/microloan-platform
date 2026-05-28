package in.zeta.zea_2026_b02_piyushm_microloan.producer;

import com.google.gson.Gson;
import in.zeta.oms.atropos.client.AtroposPublisherClient;
import in.zeta.oms.atropos.model.PublishMode;
import in.zeta.oms.atropos.response.PublishEventResponse;
import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.EventProducerException;
import olympus.pubsub.model.OperationType;
import olympus.pubsub.model.PubSubEvent;
import olympus.pubsub.model.TopicScope;
import olympus.trace.OlympusSpectra;
import org.apache.http.NameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
public class EventProducer {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(EventProducer.class);

    private final PublishMode publishMode;
    private final AtroposPublisherClient atroposPublisherClient;
    private final Gson gson;

    public EventProducer(
            AtroposPublisherClient atroposPublisherClient,
            Gson gson,
            @Value("${atropos.publish.mode}") String publishModeString
    ) {
        this.atroposPublisherClient = atroposPublisherClient;
        this.gson = gson;
        try {
            this.publishMode = PublishMode.valueOf(publishModeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid publish mode: " + publishModeString).log();
            throw new EventProducerException("Invalid publish mode: " + publishModeString, e);
        } catch (Exception e) {
            logger.error("Unexpected error while getting publish mode: " + e.getMessage(), e).log();
            throw new EventProducerException(
                    "Unexpected error while initializing EventProducer with publish mode: " + publishModeString, e);
        }
    }

    public CompletionStage<PublishEventResponse> publishEvent(
            String objectId,
            Map<String, Object> data,
            String topic,
            TopicScope topicScope
    ) {
        try {
            PubSubEvent.Builder builder = new PubSubEvent.Builder()
                    .tenant("0")
                    .topicScope(topicScope)
                    .objectType(topic)
                    .objectID(objectId)
                    .operationType(OperationType.CREATED)
                    .sourceAttributes(new NameValuePair[0])
                    .tags(List.of())
                    .stateMachineState("default")
                    .data(gson.toJsonTree(data));

            return atroposPublisherClient.publish(builder, publishMode)
                    .exceptionally(e -> {
                        logger.error("Failed to publish event to topic " + topic + ": " + e.getMessage(), e).log();
                        throw new EventProducerException("Failed to publish event to topic " + topic, e);
                    });
        } catch (Exception e) {
            logger.error("Exception in publishEvent for topic " + topic + ": " + e.getMessage(), e).log();
            throw new EventProducerException("Failed to publish event to topic " + topic, e);
        }
    }
}
