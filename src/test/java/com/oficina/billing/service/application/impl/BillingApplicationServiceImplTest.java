package com.oficina.billing.service.application.impl;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import com.oficina.billing.service.infrastructure.repository.OrcamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private BillingApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillingApplicationServiceImpl(repository, kafkaTemplate);
    }

    private Orcamento orcamentoExistente() {
        return new Orcamento(1L, "ORC-1", "12345678909", 100.0, StatusOrcamento.PENDENTE);
    }

    @Test
    @DisplayName("Deve gerar orcamento, persistir e publicar evento")
    void shouldGenerateBudgetAndPublishEvent() {
        when(repository.save(any(Orcamento.class))).thenAnswer(invocation -> {
            Orcamento orcamento = invocation.getArgument(0);
            orcamento.setId(10L);
            return orcamento;
        });

        Orcamento result = service.gerarOrcamento("12345678909", 250.0);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertTrue(result.getCodigoOrcamento().startsWith("ORC-"));
        assertEquals("12345678909", result.getClienteCpf());
        assertEquals(250.0, result.getValor());
        assertEquals(StatusOrcamento.PENDENTE, result.getStatus());
        verify(repository).save(any(Orcamento.class));
        verify(kafkaTemplate).send(eq("budget-generated"), eq("10"), contains("clienteCpf='12345678909'"));
    }

    @Test
    @DisplayName("Deve atualizar status para APROVADO e publicar evento")
    void shouldUpdateStatusToApproved() {
        Orcamento orcamento = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(orcamento));
        when(repository.save(any(Orcamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.APROVADO);

        assertEquals(StatusOrcamento.APROVADO, result.getStatus());
        verify(kafkaTemplate).send(eq("budget-approved"), eq("1"), contains("ORC-1"));
    }

    @Test
    @DisplayName("Deve atualizar status para PAGO, gerar pagamentoId e publicar evento")
    void shouldUpdateStatusToPaid() {
        Orcamento orcamento = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(orcamento));
        when(repository.save(any(Orcamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.PAGO);

        assertEquals(StatusOrcamento.PAGO, result.getStatus());
        assertNotNull(result.getPagamentoId());
        assertTrue(result.getPagamentoId().startsWith("PAG-"));
        verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), contains("ORC-1"));
    }

    @Test
    @DisplayName("Deve atualizar status para REJEITADO e publicar evento")
    void shouldUpdateStatusToRejected() {
        Orcamento orcamento = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(orcamento));
        when(repository.save(any(Orcamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.REJEITADO);

        assertEquals(StatusOrcamento.REJEITADO, result.getStatus());
        verify(kafkaTemplate).send(eq("budget-rejected"), eq("1"), contains("ORC-1"));
    }

    @Test
    @DisplayName("Nao deve publicar evento para status sem tratamento")
    void shouldNotPublishEventForPendingStatus() {
        Orcamento orcamento = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(orcamento));
        when(repository.save(any(Orcamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orcamento result = service.atualizarStatus(1L, StatusOrcamento.PENDENTE);

        assertEquals(StatusOrcamento.PENDENTE, result.getStatus());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve retornar null ao atualizar status de orcamento inexistente")
    void shouldReturnNullWhenUpdatingUnknownBudget() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        Orcamento result = service.atualizarStatus(99L, StatusOrcamento.APROVADO);

        assertNull(result);
        verify(repository, never()).save(any(Orcamento.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve consultar orcamento por id")
    void shouldFindBudgetById() {
        Orcamento orcamento = orcamentoExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(orcamento));

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
        List<Orcamento> orcamentos = List.of(orcamentoExistente());
        when(repository.findByClienteCpf("12345678909")).thenReturn(orcamentos);

        List<Orcamento> result = service.listarPorCliente("12345678909");

        assertEquals(1, result.size());
        verify(repository).findByClienteCpf("12345678909");
    }
}
