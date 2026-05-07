package com.ebank.transactions;

import com.ebank.transactions.event.TransactionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TransactionApplicationTests {

    @MockitoBean
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
