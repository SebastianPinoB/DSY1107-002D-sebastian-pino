package com.example.javiiland.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Solicitud para crear un exchange. Si "durable" no se envía, se crea durable. */
public record ExchangeRequestDto(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = 255, message = "El nombre del exchange no puede superar 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                 message = "El nombre solo puede tener letras, números, '.', '_' y '-' (sin espacios)")
        String name,

        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(regexp = "^(direct|topic|fanout|headers)$",
                 message = "El tipo debe ser uno de: direct, topic, fanout, headers")
        String type,

        Boolean durable
) {}
