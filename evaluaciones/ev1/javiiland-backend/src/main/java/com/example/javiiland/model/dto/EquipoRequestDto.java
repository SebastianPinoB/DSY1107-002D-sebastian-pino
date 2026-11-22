package com.example.javiiland.model.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipoRequestDto {
   @NotBlank
   private String nombre;
   @Positive
   private Integer limiteUsos;
   @Positive
   private Double limiteHoras;
}