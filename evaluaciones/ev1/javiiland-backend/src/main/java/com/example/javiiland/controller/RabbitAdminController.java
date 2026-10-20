package com.example.javiiland.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.javiiland.model.dto.BindingRequestDto;
import com.example.javiiland.model.dto.ExchangeRequestDto;
import com.example.javiiland.model.dto.QueueRequestDto;
import com.example.javiiland.service.RabbitAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API REST para administrar RabbitMQ (solo ADMIN).
 *
 *  POST   /api/admin/rabbit/queues            crea una cola
 *  DELETE /api/admin/rabbit/queues/{name}     elimina una cola
 *  POST   /api/admin/rabbit/exchanges         crea un exchange
 *  DELETE /api/admin/rabbit/exchanges/{name}  elimina un exchange
 *  POST   /api/admin/rabbit/bindings          crea un binding
 *  DELETE /api/admin/rabbit/bindings          elimina un binding (datos en el body)
 */
@RestController
@RequestMapping("/api/admin/rabbit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RabbitAdminController {

    private final RabbitAdminService service;

    @PostMapping("/queues")
    public ResponseEntity<Map<String, String>> crearCola(@Valid @RequestBody QueueRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mensaje(service.crearCola(request)));
    }

    @DeleteMapping("/queues/{name}")
    public Map<String, String> eliminarCola(@PathVariable String name) {
        return mensaje(service.eliminarCola(name));
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> crearExchange(@Valid @RequestBody ExchangeRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mensaje(service.crearExchange(request)));
    }

    @DeleteMapping("/exchanges/{name}")
    public Map<String, String> eliminarExchange(@PathVariable String name) {
        return mensaje(service.eliminarExchange(name));
    }

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> crearBinding(@Valid @RequestBody BindingRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mensaje(service.crearBinding(request)));
    }

    @DeleteMapping("/bindings")
    public Map<String, String> eliminarBinding(@Valid @RequestBody BindingRequestDto request) {
        return mensaje(service.eliminarBinding(request));
    }

    private Map<String, String> mensaje(String texto) {
        return Map.of("message", texto);
    }
}
