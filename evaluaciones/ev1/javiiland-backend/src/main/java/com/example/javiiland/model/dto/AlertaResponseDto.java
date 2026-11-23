package com.example.javiiland.model.dto;

import java.time.LocalDateTime;

import com.example.javiiland.model.MotivoAlerta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaResponseDto {
    private Long id;
    private Long equipoId;
    private String equipo;
    private MotivoAlerta motivo;
    private String detalle;
    private LocalDateTime creadaEn;
    private boolean resuelta;
    private LocalDateTime resueltaEn;
}
