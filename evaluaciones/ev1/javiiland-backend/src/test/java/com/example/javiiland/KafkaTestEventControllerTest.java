package com.example.javiiland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

import com.example.javiiland.controller.KafkaTestEventController;
import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import com.example.javiiland.model.dto.KafkaTestEventRequestDto;

class KafkaTestEventControllerTest {

    @Test
    void publicarEnviaElPayloadRawYRespondeAccepted() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(UsoEquipamientoPublisher.TOPIC, "1", "esto-no-es-json"))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaTestEventController controller = new KafkaTestEventController(kafkaTemplate);
        KafkaTestEventRequestDto request = new KafkaTestEventRequestDto();
        request.setKey("1");
        request.setPayload("esto-no-es-json");

        var response = controller.publicar(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(kafkaTemplate).send(UsoEquipamientoPublisher.TOPIC, "1", "esto-no-es-json");
    }
}
