package com.example.javiiland.messaging.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.javiiland.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notifica por correo al administrador cuando un equipo queda en mantención.
 * Usa su propio groupId, por lo que recibe todas las alertas
 * independientemente del consumidor de uso-equipamiento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertaMantenimientoConsumer {

   private final EmailService emailService;

   @KafkaListener(topics = UsoEquipamientoPublisher.TOPIC_ALERTAS, groupId = "notificaciones-admin", properties = {
         "spring.json.value.default.type=com.example.javiiland.messaging.kafka.AlertaMantenimientoEvent" })
   public void onAlerta(AlertaMantenimientoEvent alerta) {
      log.info("Alerta de mantenimiento recibida: equipo {} ({})", alerta.equipo(), alerta.motivo());
      emailService.enviarAlertaMantenimiento(alerta);
   }
}
