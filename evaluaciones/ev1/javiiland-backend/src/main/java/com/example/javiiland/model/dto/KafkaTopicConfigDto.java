package com.example.javiiland.model.dto;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cambios sobre un tópico existente: ambos campos son opcionales. */
@Getter
@Setter
@NoArgsConstructor
public class KafkaTopicConfigDto {

    private Map<String, String> configs;

    /** Nueva cantidad total de particiones (solo se puede aumentar). */
    @Min(1)
    @Max(50)
    private Integer partitions;
}
