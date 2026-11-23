package com.example.javiiland.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.javiiland.model.dto.KafkaTopicConfigDto;
import com.example.javiiland.model.dto.KafkaTopicRequestDto;
import com.example.javiiland.model.dto.KafkaTopicResponseDto;
import com.example.javiiland.service.KafkaAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API REST para administrar tópicos de Kafka (solo ADMIN).
 *
 *  GET    /api/admin/kafka/topics            lista los tópicos
 *  GET    /api/admin/kafka/topics/{name}     detalle: particiones, réplicas y configuración
 *  POST   /api/admin/kafka/topics            crea un tópico
 *  PATCH  /api/admin/kafka/topics/{name}     modifica configuración y/o aumenta particiones
 *  DELETE /api/admin/kafka/topics/{name}     elimina un tópico
 */
@RestController
@RequestMapping("/api/admin/kafka/topics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class KafkaAdminController {

    private final KafkaAdminService service;

    @GetMapping
    public List<String> listar() {
        return service.listarTopicos();
    }

    @GetMapping("/{name}")
    public KafkaTopicResponseDto detalle(@PathVariable String name) {
        return service.describirTopico(name);
    }

    @PostMapping
    public ResponseEntity<KafkaTopicResponseDto> crear(@Valid @RequestBody KafkaTopicRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTopico(request));
    }

    @PatchMapping("/{name}")
    public KafkaTopicResponseDto actualizar(@PathVariable String name,
            @Valid @RequestBody KafkaTopicConfigDto request) {
        return service.actualizarTopico(name, request);
    }

    @DeleteMapping("/{name}")
    public Map<String, String> eliminar(@PathVariable String name) {
        service.eliminarTopico(name);
        return Map.of("mensaje", "Tópico '" + name + "' eliminado");
    }
}
