package com.ebank.transactions.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void publish(TransactionEvent event) {
        log.info("Publishing transaction event: type={}, transactionId={}, status={}",
                event.eventType(), event.transactionId(), event.status());

        kafkaTemplate.send(TOPIC, event.transactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction event for transactionId={}: {}",
                                event.transactionId(), ex.getMessage(), ex);
                    } else {
                        log.info("Successfully published transaction event for transactionId={} to topic={}, partition={}, offset={}",
                                event.transactionId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
