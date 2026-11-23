package com.example.javiiland.controller;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import com.example.javiiland.model.dto.KafkaTestEventRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/kafka")
@PreAuthorize("hasRole('ADMIN')")
public class KafkaTestEventController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaTestEventController(@Qualifier("kafkaRawTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/test-event")
    public ResponseEntity<Map<String, String>> publicar(@Valid @RequestBody KafkaTestEventRequestDto request) {
        try {
            kafkaTemplate.send(UsoEquipamientoPublisher.TOPIC, request.getKey(), request.getPayload())
                    .get(5, TimeUnit.SECONDS);
            return ResponseEntity.accepted()
                    .body(Map.of("message", "Evento publicado en " + UsoEquipamientoPublisher.TOPIC));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La publicación del evento fue interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo publicar el evento en Kafka", exception);
        }
    }
}
