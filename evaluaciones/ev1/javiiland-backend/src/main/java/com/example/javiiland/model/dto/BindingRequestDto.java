package com.example.javiiland.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud para crear o eliminar un binding: exchange --(routingKey)--> destination.
 * destinationType indica si el destino es una cola ("queue") u otro exchange ("exchange").
 * routingKey puede ser "" (por ejemplo en exchanges fanout), pero no null.
 */
public record BindingRequestDto(
        @NotBlank(message = "El exchange de origen es obligatorio")
        @Size(max = 255, message = "El nombre del exchange no puede superar 255 caracteres")
        String exchange,

        @NotBlank(message = "El destino es obligatorio")
        @Size(max = 255, message = "El nombre del destino no puede superar 255 caracteres")
        String destination,

        @NotBlank(message = "destinationType es obligatorio")
        @Pattern(regexp = "^(queue|exchange)$",
                 message = "destinationType debe ser 'queue' o 'exchange'")
        String destinationType,

        @NotNull(message = "routingKey es obligatoria (puede ser \"\")")
        @Size(max = 255, message = "routingKey no puede superar 255 caracteres")
        String routingKey
) {}
