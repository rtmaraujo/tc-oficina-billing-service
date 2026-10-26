package com.oficina.billing.service.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.service.application.event.SagaEvent;
import com.oficina.billing.service.application.payment.CheckoutResult;
import com.oficina.billing.service.application.payment.MockPaymentGateway;
import com.oficina.billing.service.application.payment.PaymentGateway;
import com.oficina.billing.service.application.payment.PaymentResult;
import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import com.oficina.billing.service.infrastructure.persistence.OrcamentoEntity;
import com.oficina.billing.service.infrastructure.repository.OrcamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingApplicationServiceImpl - Testes de Aplicacao")
class BillingApplicationServiceImplTest {

    @Mock
    private OrcamentoRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentGateway paymentGateway;

    private BillingApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        paymentGateway = new MockPaymentGateway();
        service = new BillingApplicationServiceImpl(repository, kafkaTemplate,
            new ObjectMapper(), paymentGateway, 500.0);
    }

    private OrcamentoEntity orcamentoExistente() {
        OrcamentoEntity entity = new OrcamentoEntity(
            new Orcamento(1L, "ORC-1", "12345678909", 100.0, StatusOrcamento.PENDENTE));
        entity.setId(1L);
        entity.setSagaId("SAGA-OS-1");
        entity.setOsId(1L);
        return entity;
    }

    private SagaEvent eventoOs() {
        SagaEvent event = SagaEvent.of("OS_CREATED", "SAGA-OS-1");
        event.osId = 1L;
        event.osNumero = "OS-1";
        event.clienteCpf = "12345678909";
        return event;
    }

    @Test
    @DisplayName("Deve gerar orcamento manual e publicar evento JSON")
    void shouldGenerateBudgetAndPublishEvent() {
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> {
            OrcamentoEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        Orcamento result = service.gerarOrcamento("12345678909", 250.0);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertTrue(result.getCodigoOrcamento().startsWith("ORC-"));
        assertEquals("12345678909", result.getClienteCpf());
        assertEquals(250.0, result.getValor());
        assertEquals(StatusOrcamento.PENDENTE, result.getStatus());
        verify(kafkaTemplate).send(eq("budget-generated"), eq("10"), contains("BUDGET_GENERATED"));
    }

    @Test
    @DisplayName("Deve gerar orcamento automatico a partir do os-created")
    void shouldGenerateBudgetFromOsCreated() {
        when(repository.findByOsId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> {
            OrcamentoEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });
        SagaEvent event = eventoOs();
        event.valor = 300.0;

        Orcamento result = service.gerarOrcamentoParaOs(event);

        assertEquals(11L, result.getId());
        assertEquals(300.0, result.getValor());
        assertEquals("SAGA-OS-1", result.getSagaId());
        assertEquals(1L, result.getOsId());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("budget-generated"), eq("11"), payload.capture());
        assertTrue(payload.getValue().contains("OS_CREATED")
            || payload.getValue().contains("BUDGET_GENERATED"));
    }

    @Test
    @DisplayName("Deve usar valor padrao quando os-created nao trouxer valor")
    void shouldUseDefaultValueWhenEventHasNoValue() {
        when(repository.findByOsId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> {
            OrcamentoEntity entity = invocation.getArgument(0);
            entity.setId(12L);
            return entity;
        });

        Orcamento result = service.gerarOrcamentoParaOs(eventoOs());

        assertEquals(500.0, result.getValor());
    }

    @Test
    @DisplayName("Deve usar valor padrao quando o valor do evento for zero")
    void shouldUseDefaultValueWhenEventValueIsZero() {
        when(repository.findByOsId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> {
            OrcamentoEntity entity = invocation.getArgument(0);
            entity.setId(13L);
            return entity;
        });
        SagaEvent event = eventoOs();
        event.valor = 0.0;

        Orcamento result = service.gerarOrcamentoParaOs(event);

        assertEquals(500.0, result.getValor());
    }

    @Test
    @DisplayName("Deve processar novo pagamento mesmo com outro paymentId")
    void shouldProcessNewPaymentWithDifferentId() {
        OrcamentoEntity entity = orcamentoExistente();
        entity.setPagamentoId("MP-ANTIGO");
        entity.setStatus(StatusOrcamento.PAGO);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.processarPagamento(1L,
            new PaymentResult("MP-NOVO", "approved", true, "1", "ok"));

        assertEquals("MP-NOVO", result.getPagamentoId());
        assertEquals(StatusOrcamento.PAGO, result.getStatus());
    }

    @Test
    @DisplayName("Pagamento recusado ja processado deve ser idempotente")
    void shouldIgnoreAlreadyProcessedRejectedPayment() {
        OrcamentoEntity entity = orcamentoExistente();
        entity.setPagamentoId("MP-6");
        entity.setStatus(StatusOrcamento.REJEITADO);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        Orcamento result = service.processarPagamento(1L,
            new PaymentResult("MP-6", "rejected", false, "1", "recusado"));

        assertEquals(StatusOrcamento.REJEITADO, result.getStatus());
        verify(repository, never()).save(any(OrcamentoEntity.class));
    }

    @Test
    @DisplayName("Nao deve duplicar orcamento para a mesma OS")
    void shouldNotDuplicateBudgetForSameOs() {        OrcamentoEntity existente = orcamentoExistente();
        when(repository.findByOsId(1L)).thenReturn(Optional.of(existente));

        Orcamento result = service.gerarOrcamentoParaOs(eventoOs());

        assertEquals(1L, result.getId());
        verify(repository, never()).save(any(OrcamentoEntity.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve atualizar status para APROVADO e publicar evento")
    void shouldUpdateStatusToApproved() {
        when(repository.findById(1L)).thenReturn(Optional.of(orcamentoExistente()));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.APROVADO);

        assertEquals(StatusOrcamento.APROVADO, result.getStatus());
        verify(kafkaTemplate).send(eq("budget-approved"), eq("1"), contains("BUDGET_APPROVED"));
    }

    @Test
    @DisplayName("Deve atualizar status para PAGO, gerar pagamentoId e publicar evento")
    void shouldUpdateStatusToPaid() {
        when(repository.findById(1L)).thenReturn(Optional.of(orcamentoExistente()));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.PAGO);

        assertEquals(StatusOrcamento.PAGO, result.getStatus());
        assertNotNull(result.getPagamentoId());
        assertTrue(result.getPagamentoId().startsWith("PAG-"));
        verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), contains("PAYMENT_COMPLETED"));
    }

    @Test
    @DisplayName("Deve atualizar status para REJEITADO e publicar evento")
    void shouldUpdateStatusToRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(orcamentoExistente()));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.REJEITADO);

        assertEquals(StatusOrcamento.REJEITADO, result.getStatus());
        verify(kafkaTemplate).send(eq("budget-rejected"), eq("1"), contains("BUDGET_REJECTED"));
    }

    @Test
    @DisplayName("Nao deve publicar evento para status sem tratamento")
    void shouldNotPublishEventForPendingStatus() {
        when(repository.findById(1L)).thenReturn(Optional.of(orcamentoExistente()));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.PENDENTE);

        assertEquals(StatusOrcamento.PENDENTE, result.getStatus());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve retornar null ao atualizar status de orcamento inexistente")
    void shouldReturnNullWhenUpdatingUnknownBudget() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertNull(service.atualizarStatus(99L, StatusOrcamento.APROVADO));
        verify(repository, never()).save(any(OrcamentoEntity.class));
    }

    @Test
    @DisplayName("Deve cancelar orcamento pela compensacao usando osId")
    void shouldCancelBudgetByOsId() {
        OrcamentoEntity entity = orcamentoExistente();
        when(repository.findByOsId(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.cancelarPorSaga(eventoOs());

        assertEquals(StatusOrcamento.CANCELADO, result.getStatus());
    }

    @Test
    @DisplayName("Deve cancelar orcamento pela compensacao usando orcamentoId")
    void shouldCancelBudgetByBudgetId() {
        OrcamentoEntity entity = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SagaEvent event = SagaEvent.of("SAGA_COMPENSATED", "SAGA-OS-1");
        event.orcamentoId = 1L;

        Orcamento result = service.cancelarPorSaga(event);

        assertEquals(StatusOrcamento.CANCELADO, result.getStatus());
    }

    @Test
    @DisplayName("Deve retornar null quando nao houver orcamento para compensar")
    void shouldReturnNullWhenNoBudgetToCompensate() {
        when(repository.findByOsId(1L)).thenReturn(Optional.empty());
        SagaEvent event = eventoOs();

        assertNull(service.cancelarPorSaga(event));
    }

    @Test
    @DisplayName("Deve criar checkout e persistir preferencia")
    void shouldCreateCheckout() {
        OrcamentoEntity entity = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResult result = service.criarCheckout(1L);

        assertTrue(result.mock());
        assertEquals("MOCK-PREF-1", result.preferenceId());
        assertEquals("MOCK-PREF-1", entity.getPreferenceId());
        assertNotNull(entity.getInitPoint());
    }

    @Test
    @DisplayName("Deve falhar ao criar checkout de orcamento inexistente")
    void shouldFailCheckoutForUnknownBudget() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.criarCheckout(99L));
    }

    @Test
    @DisplayName("Deve aprovar pagamento e publicar payment-completed")
    void shouldProcessApprovedPayment() {
        OrcamentoEntity entity = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.processarPagamento(1L,
            new PaymentResult("MP-1", "approved", true, "1", "ok"));

        assertEquals(StatusOrcamento.PAGO, result.getStatus());
        assertEquals("MP-1", result.getPagamentoId());
        verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), contains("PAYMENT_COMPLETED"));
    }

    @Test
    @DisplayName("Deve recusar pagamento e publicar payment-failed")
    void shouldProcessRejectedPayment() {
        OrcamentoEntity entity = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.processarPagamento(1L,
            new PaymentResult("MP-2", "rejected", false, "1", "recusado"));

        assertEquals(StatusOrcamento.REJEITADO, result.getStatus());
        verify(kafkaTemplate).send(eq("payment-failed"), eq("1"), contains("PAYMENT_FAILED"));
    }

    @Test
    @DisplayName("Deve localizar orcamento pelo paymentId quando id nao for informado")
    void shouldFindBudgetByPaymentId() {
        OrcamentoEntity entity = orcamentoExistente();
        entity.setPagamentoId("MP-3");
        when(repository.findByPagamentoId("MP-3")).thenReturn(Optional.of(entity));
        when(repository.save(any(OrcamentoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.processarPagamento(null,
            new PaymentResult("MP-3", "approved", true, "1", "ok"));

        assertEquals(StatusOrcamento.PAGO, result.getStatus());
    }

    @Test
    @DisplayName("Pagamento ja processado deve ser idempotente")
    void shouldIgnoreAlreadyProcessedPayment() {
        OrcamentoEntity entity = orcamentoExistente();
        entity.setPagamentoId("MP-4");
        entity.setStatus(StatusOrcamento.PAGO);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        Orcamento result = service.processarPagamento(1L,
            new PaymentResult("MP-4", "approved", true, "1", "ok"));

        assertEquals(StatusOrcamento.PAGO, result.getStatus());
        verify(repository, never()).save(any(OrcamentoEntity.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve retornar null ao processar pagamento de orcamento inexistente")
    void shouldReturnNullWhenPaymentBudgetNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertNull(service.processarPagamento(99L,
            new PaymentResult("MP-5", "approved", true, "99", "ok")));
    }

    @Test
    @DisplayName("Deve consultar orcamento por id")
    void shouldFindBudgetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(orcamentoExistente()));

        Orcamento result = service.consultarPorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("Deve retornar null ao consultar orcamento inexistente")
    void shouldReturnNullWhenBudgetNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertNull(service.consultarPorId(99L));
    }

    @Test
    @DisplayName("Deve listar orcamentos por cliente")
    void shouldListBudgetsByClient() {
        List<OrcamentoEntity> orcamentos = List.of(orcamentoExistente());
        when(repository.findByClienteCpf("12345678909")).thenReturn(orcamentos);

        List<Orcamento> result = service.listarPorCliente("12345678909");

        assertEquals(1, result.size());
        verify(repository).findByClienteCpf("12345678909");
    }
}
