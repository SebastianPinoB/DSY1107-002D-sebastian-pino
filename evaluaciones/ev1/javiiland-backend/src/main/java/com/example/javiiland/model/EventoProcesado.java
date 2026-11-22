package com.example.javiiland.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Registro de eventos ya procesados: garantiza idempotencia del consumidor. */
@Entity
@Table(name = "eventos_procesados")
@Getter @Setter @NoArgsConstructor
public class EventoProcesado {

    @Id
    @Column(name = "event_id", length = 150)
    private String eventId;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn = LocalDateTime.now();

    public EventoProcesado(String eventId) {
        this.eventId = eventId;
    }
}