package com.example.javiiland.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsoEquipamientoPublisher {

   public static final String TOPIC = "uso-equipamiento";
   public static final String TOPIC_ALERTAS = "alertas-mantenimiento";

   private final KafkaTemplate<String, Object> kafka;

   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   public void onCambio(UsoEquipamientoCambioEvent e) {
      String sufijo = e.reversa() ? "reversa" : "uso";
      var tipo = e.reversa() ? UsoEquipamientoEvent.Tipo.REVERSA : UsoEquipamientoEvent.Tipo.USO;
      for (var item : e.items()) {
         // eventId determinístico: si se publica dos veces, el consumidor lo descarta
         String id = "reserva-%d-equipo-%d-%s".formatted(e.reservaId(), item.equipoId(), sufijo);
         enviar(new UsoEquipamientoEvent(id, tipo, e.reservaId(), item.equipoId(),
               item.horas(), e.fecha(), null));
      }
   }

   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   public void onAlerta(AlertaMantenimientoEvent a) {
      kafka.send(TOPIC_ALERTAS, String.valueOf(a.equipoId()), a)
            .whenComplete((r, ex) -> {
               if (ex != null)
                  log.error("No se pudo publicar alerta del equipo {}", a.equipoId(), ex);
            });
   }

   /** Reporte manual de falla (no hay transacción de BD de por medio). */
   public void publicarFalla(Long equipoId, String detalle) {
      String id = "falla-equipo-%d-%d".formatted(equipoId, System.currentTimeMillis());
      enviar(new UsoEquipamientoEvent(id, UsoEquipamientoEvent.Tipo.FALLA, null, equipoId,
            0, LocalDate.now(), detalle));
   }

   private void enviar(UsoEquipamientoEvent ev) {
      // key = equipoId -> misma partición -> orden garantizado por equipo
      kafka.send(TOPIC, String.valueOf(ev.equipoId()), ev)
            .whenComplete((r, ex) -> {
               if (ex != null)
                  log.error("Fallo publicando {}", ev.eventId(), ex);
               else
                  log.info("Publicado {} -> partición {}", ev.eventId(), r.getRecordMetadata().partition());
            });
   }
}