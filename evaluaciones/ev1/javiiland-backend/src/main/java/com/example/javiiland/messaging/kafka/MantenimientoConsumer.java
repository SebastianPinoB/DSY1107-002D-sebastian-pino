package com.example.javiiland.messaging.kafka;

import com.example.javiiland.service.MantenimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MantenimientoConsumer {

   private final MantenimientoService mantenimientoService;

   @KafkaListener(topics = UsoEquipamientoPublisher.TOPIC, groupId = "mantenimiento")
   public void onUso(UsoEquipamientoEvent evento) {
      mantenimientoService.procesar(evento);
   }
}