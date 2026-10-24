package com.oficina.billing.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orcamento - Testes de Dominio")
class OrcamentoTest {

    @Test
    @DisplayName("Deve criar orcamento vazio")
    void shouldCreateEmptyOrcamento() {
        Orcamento orcamento = new Orcamento();

        assertNull(orcamento.getId());
        assertNull(orcamento.getCodigoOrcamento());
        assertNull(orcamento.getClienteCpf());
        assertEquals(0.0, orcamento.getValor());
        assertNull(orcamento.getStatus());
        assertNull(orcamento.getDataCriacao());
        assertNull(orcamento.getDataAtualizacao());
        assertNull(orcamento.getPagamentoId());
    }

    @Test
    @DisplayName("Deve criar orcamento com construtor completo")
    void shouldCreateOrcamentoWithFullConstructor() {
        Orcamento orcamento = new Orcamento(1L, "ORC-1", "12345678909", 150.0, StatusOrcamento.PENDENTE);

        assertEquals(1L, orcamento.getId());
        assertEquals("ORC-1", orcamento.getCodigoOrcamento());
        assertEquals("12345678909", orcamento.getClienteCpf());
        assertEquals(150.0, orcamento.getValor());
        assertEquals(StatusOrcamento.PENDENTE, orcamento.getStatus());
        assertNotNull(orcamento.getDataCriacao());
        assertEquals(orcamento.getDataCriacao(), orcamento.getDataAtualizacao());
    }

    @Test
    @DisplayName("Deve atualizar todos os campos via setters")
    void shouldSetAllFields() {
        Orcamento orcamento = new Orcamento();

        orcamento.setId(2L);
        orcamento.setCodigoOrcamento("ORC-2");
        orcamento.setClienteCpf("98765432100");
        orcamento.setValor(300.0);
        orcamento.setStatus(StatusOrcamento.PAGO);
        orcamento.setDataCriacao("2026-01-01T10:00:00");
        orcamento.setDataAtualizacao("2026-01-02T10:00:00");
        orcamento.setPagamentoId("PAG-123");

        assertEquals(2L, orcamento.getId());
        assertEquals("ORC-2", orcamento.getCodigoOrcamento());
        assertEquals("98765432100", orcamento.getClienteCpf());
        assertEquals(300.0, orcamento.getValor());
        assertEquals(StatusOrcamento.PAGO, orcamento.getStatus());
        assertEquals("2026-01-01T10:00:00", orcamento.getDataCriacao());
        assertEquals("2026-01-02T10:00:00", orcamento.getDataAtualizacao());
        assertEquals("PAG-123", orcamento.getPagamentoId());
    }

    @Test
    @DisplayName("Deve expor todos os status do orcamento")
    void shouldExposeAllStatus() {
        assertEquals(5, StatusOrcamento.values().length);
        assertEquals(StatusOrcamento.PENDENTE, StatusOrcamento.valueOf("PENDENTE"));
        assertEquals(StatusOrcamento.APROVADO, StatusOrcamento.valueOf("APROVADO"));
        assertEquals(StatusOrcamento.PAGO, StatusOrcamento.valueOf("PAGO"));
        assertEquals(StatusOrcamento.REJEITADO, StatusOrcamento.valueOf("REJEITADO"));
        assertEquals(StatusOrcamento.CANCELADO, StatusOrcamento.valueOf("CANCELADO"));
    }
}
