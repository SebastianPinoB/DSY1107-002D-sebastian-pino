package com.example.javiiland;

import com.example.javiiland.controller.ReservaController;
import com.example.javiiland.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaControllerUnitTest {

    @Mock
    private ReservaService reservaService;

    @InjectMocks
    private ReservaController reservaController;

    @Test
    void calendario_DebeRetornarLista() {
        LocalDate inicio = LocalDate.now();
        LocalDate fin = inicio.plusDays(7);

        when(reservaService.calendario(inicio, fin)).thenReturn(Collections.emptyList());

        var resultado = reservaController.calendario(inicio, fin);
        assertEquals(0, resultado.size());
    }
}