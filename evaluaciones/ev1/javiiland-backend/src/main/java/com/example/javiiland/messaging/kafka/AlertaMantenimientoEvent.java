package com.example.javiiland.messaging.kafka;

import com.example.javiiland.model.MotivoAlerta;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sirve como evento de Spring y como payload del tópico alertas-mantenimiento.
 */
public record AlertaMantenimientoEvent(Long equipoId, String equipo, MotivoAlerta motivo,
      String detalle, LocalDate bloqueadoDesde, LocalDateTime ocurridaEn) {
}