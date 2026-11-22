package com.example.javiiland.messaging.kafka;

import java.time.LocalDate;
import java.util.List;

/** Evento interno de Spring (se convierte a Kafka solo tras el COMMIT). */
public record UsoEquipamientoCambioEvent(Long reservaId, LocalDate fecha, List<Item> items, boolean reversa) {
    public record Item(Long equipoId, double horas) {
    }
}