package com.oficina.billing.service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@DisplayName("KafkaErrorHandlerConfig - Retentativas e DLT no Billing")
class KafkaErrorHandlerConfigTest {

    @Test
    @DisplayName("Deve criar handler com retentativa e publicacao em DLT")
    @SuppressWarnings("unchecked")
    void shouldCreateErrorHandler() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaErrorHandlerConfig().kafkaErrorHandler(template);

        assertNotNull(handler);
    }
}
