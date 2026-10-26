package com.oficina.billing.service.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Controle de idempotencia do consumidor: cada eventId e gravado uma unica vez.
 */
@Entity
@Table(name = "evento_processado")
public class EventoProcessadoEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "topico", nullable = false)
    private String topico;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;

    public EventoProcessadoEntity() {
        this.processadoEm = LocalDateTime.now();
    }

    public EventoProcessadoEntity(String eventId, String topico) {
        this();
        this.eventId = eventId;
        this.topico = topico;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTopico() { return topico; }
    public void setTopico(String topico) { this.topico = topico; }
    public LocalDateTime getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(LocalDateTime processadoEm) { this.processadoEm = processadoEm; }
}
