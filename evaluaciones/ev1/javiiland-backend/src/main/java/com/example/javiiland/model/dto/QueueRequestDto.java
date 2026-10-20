package com.example.javiiland.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud para crear una cola. Solo "name" es obligatorio.
 * Si "durable" no se envía, la cola se crea durable.
 */
public record QueueRequestDto(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = 255, message = "El nombre de la cola no puede superar 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                 message = "El nombre solo puede tener letras, números, '.', '_' y '-' (sin espacios)")
        String name,

        Boolean durable,

        @Size(max = 255, message = "deadLetterExchange no puede superar 255 caracteres")
        String deadLetterExchange,

        @Size(max = 255, message = "deadLetterRoutingKey no puede superar 255 caracteres")
        String deadLetterRoutingKey,

        @Min(value = 1, message = "ttlMs debe ser mayor que 0")
        Integer ttlMs
) {}
