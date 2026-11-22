package com.example.javiiland.messaging.kafka;

import java.time.LocalDate;

/** Payload del tópico uso-equipamiento. */
public record UsoEquipamientoEvent(
      String eventId, // determinístico: permite idempotencia
      Tipo tipo,
      Long reservaId, // null en FALLA
      Long equipoId,
      double horas,
      LocalDate fecha, // fecha del evento (o de la falla)
      String detalle) { // solo FALLA

   public enum Tipo {
      USO, REVERSA, FALLA
   }
}