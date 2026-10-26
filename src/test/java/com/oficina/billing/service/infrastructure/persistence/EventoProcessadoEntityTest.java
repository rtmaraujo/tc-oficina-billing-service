package com.oficina.billing.service.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventoProcessadoEntity - Idempotencia")
class EventoProcessadoEntityTest {

    @Test
    @DisplayName("Deve registrar evento processado")
    void shouldCreateEventoProcessado() {
        EventoProcessadoEntity entity = new EventoProcessadoEntity("evt-1", "os-created");

        assertEquals("evt-1", entity.getEventId());
        assertEquals("os-created", entity.getTopico());
        assertNotNull(entity.getProcessadoEm());

        entity.setEventId("evt-2");
        entity.setTopico("compensate-budget");
        entity.setProcessadoEm(LocalDateTime.of(2026, 1, 1, 10, 0));

        assertEquals("evt-2", entity.getEventId());
        assertEquals("compensate-budget", entity.getTopico());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), entity.getProcessadoEm());
    }
}
