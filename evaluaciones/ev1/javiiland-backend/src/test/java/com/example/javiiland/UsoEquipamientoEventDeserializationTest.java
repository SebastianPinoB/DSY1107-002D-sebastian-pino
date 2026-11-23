package com.example.javiiland;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.example.javiiland.messaging.kafka.UsoEquipamientoEvent;
import com.example.javiiland.messaging.kafka.UsoEquipamientoEvent.Tipo;

class UsoEquipamientoEventDeserializationTest {

    @Test
    void deserializesLocalDateFromUsageEvent() {
        String payload = """
                {"eventId":"reserva-42-equipo-1-uso","tipo":"USO","reservaId":42,
                "equipoId":1,"horas":1.5,"fecha":"2026-12-20","detalle":null}
                """;
        var deserializer = new JsonDeserializer<>(UsoEquipamientoEvent.class, false);

        UsoEquipamientoEvent event = deserializer.deserialize(
                "uso-equipamiento",
                new RecordHeaders(),
                payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(Tipo.USO, event.tipo());
        assertEquals(LocalDate.of(2026, 12, 20), event.fecha());
    }
}
