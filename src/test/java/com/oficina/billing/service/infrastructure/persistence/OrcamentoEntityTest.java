package com.oficina.billing.service.infrastructure.persistence;

import com.oficina.billing.service.domain.Orcamento;
import com.oficina.billing.service.domain.StatusOrcamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrcamentoEntity - Testes de Persistencia")
class OrcamentoEntityTest {

    @Test
    @DisplayName("Deve criar entidade com valores padrao")
    void shouldCreateDefaultEntity() {
        OrcamentoEntity entity = new OrcamentoEntity();

        assertNull(entity.getId());
        assertNotNull(entity.getDataCriacao());
        assertNotNull(entity.getDataAtualizacao());
    }

    @Test
    @DisplayName("Deve criar entidade a partir do dominio")
    void shouldCreateEntityFromDomain() {
        Orcamento dominio = new Orcamento(7L, "ORC-7", "12345678909", 200.0, StatusOrcamento.APROVADO);
        dominio.setPagamentoId("PAG-7");
        dominio.setSagaId("SAGA-7");
        dominio.setOsId(7L);
        dominio.setPreferenceId("PREF-7");
        dominio.setInitPoint("https://checkout/7");

        OrcamentoEntity entity = new OrcamentoEntity(dominio);

        assertEquals(7L, entity.getId());
        assertEquals("ORC-7", entity.getCodigoOrcamento());
        assertEquals("12345678909", entity.getClienteCpf());
        assertEquals(200.0, entity.getValor());
        assertEquals(StatusOrcamento.APROVADO, entity.getStatus());
        assertEquals("PAG-7", entity.getPagamentoId());
        assertEquals("SAGA-7", entity.getSagaId());
        assertEquals(7L, entity.getOsId());
        assertEquals("PREF-7", entity.getPreferenceId());
        assertEquals("https://checkout/7", entity.getInitPoint());
        assertNotNull(entity.getDataCriacao());
        assertNotNull(entity.getDataAtualizacao());
    }

    @Test
    @DisplayName("Deve converter entidade para dominio")
    void shouldConvertEntityToDomain() {
        OrcamentoEntity entity = new OrcamentoEntity();
        entity.setId(3L);
        entity.setCodigoOrcamento("ORC-3");
        entity.setClienteCpf("11122233344");
        entity.setValor(99.9);
        entity.setStatus(StatusOrcamento.REJEITADO);
        entity.setPagamentoId("PAG-3");
        entity.setSagaId("SAGA-3");
        entity.setOsId(3L);
        entity.setPreferenceId("PREF-3");
        entity.setInitPoint("https://checkout/3");
        entity.setDataCriacao(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setDataAtualizacao(LocalDateTime.of(2026, 1, 2, 11, 0));

        Orcamento dominio = entity.toDomain();

        assertEquals(3L, dominio.getId());
        assertEquals("ORC-3", dominio.getCodigoOrcamento());
        assertEquals("11122233344", dominio.getClienteCpf());
        assertEquals(99.9, dominio.getValor());
        assertEquals(StatusOrcamento.REJEITADO, dominio.getStatus());
        assertEquals("PAG-3", dominio.getPagamentoId());
        assertEquals("SAGA-3", dominio.getSagaId());
        assertEquals(3L, dominio.getOsId());
        assertEquals("PREF-3", dominio.getPreferenceId());
        assertEquals("https://checkout/3", dominio.getInitPoint());
        assertEquals("2026-01-01T10:00", dominio.getDataCriacao());
        assertEquals("2026-01-02T11:00", dominio.getDataAtualizacao());
    }

    @Test
    @DisplayName("Deve atualizar todos os campos via setters")
    void shouldSetAllFields() {
        OrcamentoEntity entity = new OrcamentoEntity();

        entity.setId(4L);
        entity.setCodigoOrcamento("ORC-4");
        entity.setClienteCpf("55566677788");
        entity.setValor(10.5);
        entity.setStatus(StatusOrcamento.CANCELADO);
        entity.setPagamentoId("PAG-4");
        entity.setSagaId("SAGA-4");
        entity.setOsId(4L);
        entity.setPreferenceId("PREF-4");
        entity.setInitPoint("https://checkout/4");
        entity.setDataCriacao(LocalDateTime.of(2026, 2, 1, 8, 30));
        entity.setDataAtualizacao(LocalDateTime.of(2026, 2, 2, 9, 45));

        assertEquals(4L, entity.getId());
        assertEquals("ORC-4", entity.getCodigoOrcamento());
        assertEquals("55566677788", entity.getClienteCpf());
        assertEquals(10.5, entity.getValor());
        assertEquals(StatusOrcamento.CANCELADO, entity.getStatus());
        assertEquals("PAG-4", entity.getPagamentoId());
        assertEquals("SAGA-4", entity.getSagaId());
        assertEquals(4L, entity.getOsId());
        assertEquals("PREF-4", entity.getPreferenceId());
        assertEquals("https://checkout/4", entity.getInitPoint());
        assertEquals("2026-02-01T08:30", entity.getDataCriacao());
        assertEquals("2026-02-02T09:45", entity.getDataAtualizacao());
    }
}
