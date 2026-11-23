package com.example.javiiland.service;

import com.example.javiiland.messaging.kafka.AlertaMantenimientoEvent;
import com.example.javiiland.messaging.kafka.UsoEquipamientoEvent;
import com.example.javiiland.model.*;
import com.example.javiiland.model.dto.*;
import com.example.javiiland.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MantenimientoService {

   private final EquipoRepository equipoRepository;
   private final AlertaMantenimientoRepository alertaRepository;
   private final EventoProcesadoRepository procesadoRepository;
   private final ApplicationEventPublisher eventPublisher;

   // ---------------------------------------------------------------- consumidor

   /**
    * Idempotente: el eventId queda guardado en la MISMA transacción que el cambio
    * de contadores. Si Kafka entrega el mensaje dos veces, la segunda se ignora.
    */
   @Transactional
   public void procesar(UsoEquipamientoEvent e) {
      if (procesadoRepository.existsById(e.eventId())) {
         log.info("Evento duplicado ignorado: {}", e.eventId());
         return;
      }
      Equipo equipo = equipoRepository.findById(e.equipoId())
            .orElseThrow(() -> new IllegalArgumentException("Equipo inexistente: " + e.equipoId()));

      switch (e.tipo()) {
         case USO -> {
            equipo.setUsosAcumulados(equipo.getUsosAcumulados() + 1);
            equipo.setHorasAcumuladas(equipo.getHorasAcumuladas() + e.horas());
            evaluarLimites(equipo, e.fecha());
         }
         case REVERSA -> {
            equipo.setUsosAcumulados(Math.max(0, equipo.getUsosAcumulados() - 1));
            equipo.setHorasAcumuladas(Math.max(0, equipo.getHorasAcumuladas() - e.horas()));
         }
         case FALLA -> bloquear(equipo, MotivoAlerta.FALLA_REPORTADA, e.detalle(), e.fecha());
      }
      equipoRepository.save(equipo);
      procesadoRepository.save(new EventoProcesado(e.eventId()));
   }

   private void evaluarLimites(Equipo eq, LocalDate fechaUso) {
      if (eq.getEstado() == EstadoEquipo.EN_MANTENCION)
         return;
      // La reserva que gatilla el límite sí se realiza: se bloquea desde el día
      // siguiente
      LocalDate desde = fechaUso.plusDays(1);

      if (eq.getLimiteUsos() != null && eq.getUsosAcumulados() >= eq.getLimiteUsos()) {
         bloquear(eq, MotivoAlerta.LIMITE_USOS,
               "Alcanzó %d usos (límite %d)".formatted(eq.getUsosAcumulados(), eq.getLimiteUsos()), desde);
      } else if (eq.getLimiteHoras() != null && eq.getHorasAcumuladas() >= eq.getLimiteHoras()) {
         bloquear(eq, MotivoAlerta.LIMITE_HORAS,
               "Alcanzó %.1f horas (límite %.1f)".formatted(eq.getHorasAcumuladas(), eq.getLimiteHoras()), desde);
      }
   }

   private void bloquear(Equipo eq, MotivoAlerta motivo, String detalle, LocalDate desde) {
      if (eq.getEstado() == EstadoEquipo.EN_MANTENCION)
         return; // ya bloqueado: no duplicar alerta
      eq.setEstado(EstadoEquipo.EN_MANTENCION);
      eq.setMantencionDesde(desde);

      alertaRepository.save(AlertaMantenimiento.builder()
            .equipo(eq).motivo(motivo).detalle(detalle).build());

      // Se envía a alertas-mantenimiento solo tras el COMMIT
      eventPublisher.publishEvent(new AlertaMantenimientoEvent(
            eq.getId(), eq.getNombre(), motivo, detalle, desde, LocalDateTime.now()));
      log.warn("Equipo {} EN_MANTENCION desde {} ({})", eq.getNombre(), desde, motivo);
   }

   // ---------------------------------------------------------------- API

   @Transactional
   public EquipoResponseDto crearEquipo(EquipoRequestDto dto) {
      if (equipoRepository.existsByNombreIgnoreCase(dto.getNombre())) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un equipo con ese nombre");
      }
      return toDto(equipoRepository.save(Equipo.builder()
            .nombre(dto.getNombre())
            .limiteUsos(dto.getLimiteUsos())
            .limiteHoras(dto.getLimiteHoras())
            .build()));
   }

   @Transactional(readOnly = true)
   public List<EquipoResponseDto> listarEquipos() {
      return equipoRepository.findAll().stream().map(this::toDto).toList();
   }

   @Transactional(readOnly = true)
   public List<AlertaResponseDto> listarAlertas(boolean resueltas) {
      return alertaRepository.findByResueltaOrderByCreadaEnDesc(resueltas).stream()
            .map(a -> AlertaResponseDto.builder()
                  .id(a.getId())
                  .equipoId(a.getEquipo().getId())
                  .equipo(a.getEquipo().getNombre())
                  .motivo(a.getMotivo())
                  .detalle(a.getDetalle())
                  .creadaEn(a.getCreadaEn())
                  .resuelta(a.isResuelta())
                  .resueltaEn(a.getResueltaEn())
                  .build())
            .toList();
   }

   @Transactional(readOnly = true)
   public void validarExiste(Long equipoId) {
      if (!equipoRepository.existsById(equipoId)) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado");
      }
   }

   /**
    * Fin de la mantención: libera el equipo, reinicia contadores y cierra alertas.
    */
   @Transactional
   public EquipoResponseDto cerrarMantencion(Long equipoId) {
      Equipo eq = equipoRepository.findById(equipoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
      if (eq.getEstado() != EstadoEquipo.EN_MANTENCION) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "El equipo no está en mantención");
      }
      eq.setEstado(EstadoEquipo.DISPONIBLE);
      eq.setMantencionDesde(null);
      eq.setUsosAcumulados(0);
      eq.setHorasAcumuladas(0);

      alertaRepository.findByEquipoIdAndResuelta(equipoId, false).forEach(a -> {
         a.setResuelta(true);
         a.setResueltaEn(LocalDateTime.now());
      });
      return toDto(equipoRepository.save(eq));
   }

   private EquipoResponseDto toDto(Equipo e) {
      return EquipoResponseDto.builder()
            .id(e.getId()).nombre(e.getNombre())
            .limiteUsos(e.getLimiteUsos()).limiteHoras(e.getLimiteHoras())
            .usosAcumulados(e.getUsosAcumulados()).horasAcumuladas(e.getHorasAcumuladas())
            .estado(e.getEstado()).mantencionDesde(e.getMantencionDesde())
            .build();
   }
}