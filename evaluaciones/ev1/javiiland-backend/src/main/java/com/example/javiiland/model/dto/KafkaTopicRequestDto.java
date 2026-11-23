package com.example.javiiland.model.dto;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaTopicRequestDto {

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9._-]{1,200}", message = "Nombre inválido")
    private String name;

    @Min(1)
    @Max(50)
    private int partitions = 1;

    @Min(1)
    @Max(5)
    private short replicationFactor = 1;

    /** Configuración opcional del tópico (retention.ms, cleanup.policy, ...). */
    private Map<String, String> configs;
}
