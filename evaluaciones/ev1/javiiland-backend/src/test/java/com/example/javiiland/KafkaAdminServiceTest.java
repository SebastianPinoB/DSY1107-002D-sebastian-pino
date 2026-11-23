package com.example.javiiland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.server.ResponseStatusException;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import com.example.javiiland.model.dto.KafkaTopicConfigDto;
import com.example.javiiland.service.KafkaAdminService;

public class KafkaAdminServiceTest {

   private final KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
   private final KafkaAdminService service = new KafkaAdminService(kafkaAdmin);

   @Test
   void eliminarTopico_RechazaTopicosDeLaSolucion() {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.eliminarTopico(UsoEquipamientoPublisher.TOPIC));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(kafkaAdmin);
   }

   @Test
   void eliminarTopico_RechazaTopicosInternos() {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.eliminarTopico("__consumer_offsets"));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
   }

   @Test
   void actualizarTopico_SinCambiosDevuelveBadRequest() {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.actualizarTopico("cualquiera", new KafkaTopicConfigDto()));

      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
      verifyNoInteractions(kafkaAdmin);
   }
}
