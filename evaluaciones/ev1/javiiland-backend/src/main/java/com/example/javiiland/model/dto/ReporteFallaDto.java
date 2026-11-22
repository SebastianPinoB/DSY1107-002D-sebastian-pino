package com.example.javiiland.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteFallaDto {
   @NotBlank(message = "Describe la falla")
   private String detalle;
}