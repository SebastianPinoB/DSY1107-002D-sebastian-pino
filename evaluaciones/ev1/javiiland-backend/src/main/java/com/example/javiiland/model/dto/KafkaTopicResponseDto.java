package com.example.javiiland.model.dto;

import java.util.Map;

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
public class KafkaTopicResponseDto {
    private String name;
    private int partitions;
    private int replicationFactor;
    private Map<String, String> configs;
}
