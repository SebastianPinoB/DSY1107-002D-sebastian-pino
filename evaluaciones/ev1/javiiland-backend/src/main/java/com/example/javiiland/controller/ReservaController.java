package com.example.javiiland.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.javiiland.model.dto.CalendarioDto;
import com.example.javiiland.model.dto.ReservaRequestDto;
import com.example.javiiland.model.dto.ReservaResponseDto;
import com.example.javiiland.model.dto.ReservaUpdateDto;
import com.example.javiiland.service.ReservaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {
    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<ReservaResponseDto> crear(@RequestParam Long usuarioId,
            @Valid @RequestBody ReservaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(usuarioId, request));
    }

    @GetMapping
    public List<ReservaResponseDto> listar() {
        return reservaService.listar();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<ReservaResponseDto> listarPorUsuario(@PathVariable Long usuarioId) {
        return reservaService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/calendario")
    public List<CalendarioDto> calendario(@RequestParam LocalDate inicio, @RequestParam LocalDate fin) {
        return reservaService.calendario(inicio, fin);
    }

    @GetMapping("/{id}")
    public ReservaResponseDto buscarPorId(@PathVariable Long id) {
        return reservaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public ReservaResponseDto actualizar(@PathVariable Long id, @RequestBody ReservaUpdateDto request) {
        return reservaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ReservaResponseDto cancelar(@PathVariable Long id) {
        return reservaService.cancelar(id);
    }
}