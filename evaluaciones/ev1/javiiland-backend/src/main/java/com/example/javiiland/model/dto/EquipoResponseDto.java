package com.example.javiiland.model.dto;

import com.example.javiiland.model.EstadoEquipo;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record EquipoResponseDto(Long id, String nombre, Integer limiteUsos, Double limiteHoras,
      int usosAcumulados, double horasAcumuladas,
      EstadoEquipo estado, LocalDate mantencionDesde) {
}