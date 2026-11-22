package com.example.javiiland.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipoUsoDto {
   @NotNull(message = "El id del equipo es obligatorio")
   private Long equipoId;

   @Positive(message = "Las horas de uso deben ser mayores a 0")
   private double horas;
}