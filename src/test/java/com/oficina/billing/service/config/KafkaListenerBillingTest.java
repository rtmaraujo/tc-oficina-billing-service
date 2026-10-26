package com.oficina.billing.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.BillingApplicationService;
import com.oficina.billing.service.application.event.SagaEvent;
import com.oficina.billing.service.infrastructure.persistence.EventoProcessadoEntity;
import com.oficina.billing.service.infrastructure.repository.EventoProcessadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KafkaListenerBilling - Consumo dos eventos da Saga")
class KafkaListenerBillingTest {

    @Mock
    private BillingApplicationService billingService;
    @Mock
    private EventoProcessadoRepository eventoRepository;

    private KafkaListenerBilling listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new KafkaListenerBilling(billingService, eventoRepository, objectMapper);
        when(eventoRepository.existsById(anyString())).thenReturn(false);
        when(eventoRepository.save(any(EventoProcessadoEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String payload(String tipo) {
        try {
            return objectMapper.writeValueAsString(SagaEvent.of(tipo, "SAGA-OS-1"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Deve gerar orcamento ao consumir os-created")
    void shouldGenerateBudgetOnOsCreated() {
        listener.consumir(payload("OS_CREATED"), "os-created");

        verify(billingService).gerarOrcamentoParaOs(any(SagaEvent.class));
        verify(eventoRepository).save(any(EventoProcessadoEntity.class));
    }

    @Test
    @DisplayName("Deve compensar orcamento ao consumir compensate-budget")
    void shouldCompensateOnCompensateBudget() {
        listener.consumir(payload("COMPENSATE_BUDGET"), "compensate-budget");

        verify(billingService).cancelarPorSaga(any(SagaEvent.class));
        verify(eventoRepository).save(any(EventoProcessadoEntity.class));
    }

    @Test
    @DisplayName("Deve ignorar evento ja processado")
    void shouldIgnoreDuplicatedEvent() {
        when(eventoRepository.existsById(anyString())).thenReturn(true);

        listener.consumir(payload("OS_CREATED"), "os-created");

        verifyNoInteractions(billingService);
        verify(eventoRepository, never()).save(any(EventoProcessadoEntity.class));
    }

    @Test
    @DisplayName("Deve ignorar topico desconhecido")
    void shouldIgnoreUnknownTopic() {
        listener.consumir(payload("OUTRO"), "outro-topico");

        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Deve falhar com payload invalido")
    void shouldFailOnInvalidPayload() {
        assertThrows(IllegalArgumentException.class,
            () -> listener.consumir("{invalido}", "os-created"));

        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Nao deve registrar idempotencia quando o evento nao tiver eventId")
    void shouldNotRegisterWithoutEventId() {
        listener.consumir("{\"type\":\"OS_CREATED\",\"sagaId\":\"SAGA-1\"}", "os-created");

        verify(billingService).gerarOrcamentoParaOs(any(SagaEvent.class));
        verify(eventoRepository, never()).save(any(EventoProcessadoEntity.class));
    }
}
