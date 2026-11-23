package com.example.javiiland.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaTestEventRequestDto {
    @NotBlank
    private String key;

    @NotNull
    private String payload;
}
