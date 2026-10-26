package com.oficina.billing.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.event.SagaEvent;
import com.oficina.billing.service.infrastructure.persistence.EventoProcessadoEntity;
import com.oficina.billing.service.infrastructure.repository.EventoProcessadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor dos comandos/eventos da Saga no Billing Service.
 */
@Component
public class KafkaListenerBilling {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerBilling.class);

    private final BillingApplicationService billingService;
    private final EventoProcessadoRepository eventoRepository;
    private final ObjectMapper objectMapper;

    public KafkaListenerBilling(BillingApplicationService billingService,
                                EventoProcessadoRepository eventoRepository,
                                ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.eventoRepository = eventoRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"os-created", "compensate-budget"}, groupId = "billing-service-group")
    public void consumir(String message,
                         @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topico) {
        SagaEvent event;
        try {
            event = objectMapper.readValue(message, SagaEvent.class);
        } catch (Exception e) {
            log.error("Payload invalido no topico {}: {}", topico, message);
            throw new IllegalArgumentException("Payload invalido em " + topico, e);
        }

        if (event.eventId != null && eventoRepository.existsById(event.eventId)) {
            log.info("Evento {} ja processado, ignorando", event.eventId);
            return;
        }

        MDC.put("sagaId", event.sagaId);
        try {
            if ("os-created".equals(topico)) {
                billingService.gerarOrcamentoParaOs(event);
            } else if ("compensate-budget".equals(topico)) {
                billingService.cancelarPorSaga(event);
            } else {
                log.warn("Topico sem tratamento no Billing: {}", topico);
            }
            if (event.eventId != null) {
                eventoRepository.save(new EventoProcessadoEntity(event.eventId, topico));
            }
        } finally {
            MDC.remove("sagaId");
        }
    }
}
