package com.example.javiiland.controller;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import com.example.javiiland.model.AlertaMantenimiento;
import com.example.javiiland.model.dto.*;
import com.example.javiiland.service.MantenimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MantenimientoController {

   private final MantenimientoService service;
   private final UsoEquipamientoPublisher publisher;

   @GetMapping("/equipos")
   public List<EquipoResponseDto> equipos() {
      return service.listarEquipos();
   }

   @PreAuthorize("hasRole('ADMIN')")
   @PostMapping("/equipos")
   @ResponseStatus(HttpStatus.CREATED)
   public EquipoResponseDto crear(@Valid @RequestBody EquipoRequestDto dto) {
      return service.crearEquipo(dto);
   }

   /**
    * El operario reporta una falla: pasa por Kafka y el consumidor bloquea el
    * equipo.
    */
   @PreAuthorize("hasRole('ADMIN')")
   @PostMapping("/equipos/{id}/falla")
   public ResponseEntity<Void> reportarFalla(@PathVariable Long id, @Valid @RequestBody ReporteFallaDto dto) {
      service.validarExiste(id);
      publisher.publicarFalla(id, dto.getDetalle());
      return ResponseEntity.accepted().build(); // 202: el bloqueo se aplica de forma asíncrona
   }

   @PreAuthorize("hasRole('ADMIN')")
   @PostMapping("/equipos/{id}/mantencion/cerrar")
   public EquipoResponseDto cerrarMantencion(@PathVariable Long id) {
      return service.cerrarMantencion(id);
   }

   @PreAuthorize("hasRole('ADMIN')")
   @GetMapping("/mantenimiento/alertas")
   public List<AlertaMantenimiento> alertas(@RequestParam(defaultValue = "false") boolean resueltas) {
      return service.listarAlertas(resueltas);
   }
}