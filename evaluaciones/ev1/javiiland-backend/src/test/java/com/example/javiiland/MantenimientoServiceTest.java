package com.example.javiiland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import com.example.javiiland.messaging.kafka.AlertaMantenimientoEvent;
import com.example.javiiland.messaging.kafka.UsoEquipamientoEvent;
import com.example.javiiland.messaging.kafka.UsoEquipamientoEvent.Tipo;
import com.example.javiiland.model.AlertaMantenimiento;
import com.example.javiiland.model.Equipo;
import com.example.javiiland.model.EstadoEquipo;
import com.example.javiiland.model.EventoProcesado;
import com.example.javiiland.repository.AlertaMantenimientoRepository;
import com.example.javiiland.repository.EquipoRepository;
import com.example.javiiland.repository.EventoProcesadoRepository;
import com.example.javiiland.service.MantenimientoService;

public class MantenimientoServiceTest {

   @Mock
   private EquipoRepository equipoRepository;

   @Mock
   private AlertaMantenimientoRepository alertaRepository;

   @Mock
   private EventoProcesadoRepository procesadoRepository;

   @Mock
   private ApplicationEventPublisher eventPublisher;

   @InjectMocks
   private MantenimientoService service;

   private final LocalDate fecha = LocalDate.of(2026, 12, 10);

   @BeforeEach
   void setUp() {
      MockitoAnnotations.openMocks(this);
   }

   private Equipo equipo(Integer limiteUsos, Double limiteHoras) {
      return Equipo.builder().id(1L).nombre("Castillo inflable")
            .limiteUsos(limiteUsos).limiteHoras(limiteHoras).build();
   }

   private UsoEquipamientoEvent evento(String id, Tipo tipo, double horas) {
      return new UsoEquipamientoEvent(id, tipo, 10L, 1L, horas, fecha, "detalle");
   }

   @Test
   void procesar_IgnoraEventoDuplicado() {
      when(procesadoRepository.existsById("e1")).thenReturn(true);

      service.procesar(evento("e1", Tipo.USO, 2));

      verify(equipoRepository, never()).findById(any());
      verify(procesadoRepository, never()).save(any());
   }

   @Test
   void procesar_UsoSumaContadoresYRegistraEvento() {
      Equipo eq = equipo(10, 50.0);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.USO, 3));

      assertEquals(1, eq.getUsosAcumulados());
      assertEquals(3.0, eq.getHorasAcumuladas());
      assertEquals(EstadoEquipo.DISPONIBLE, eq.getEstado());
      verify(procesadoRepository).save(any(EventoProcesado.class));
   }

   @Test
   void procesar_BloqueaEquipoAlAlcanzarLimiteDeUsos() {
      Equipo eq = equipo(1, null);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.USO, 2));

      assertEquals(EstadoEquipo.EN_MANTENCION, eq.getEstado());
      assertEquals(fecha.plusDays(1), eq.getMantencionDesde());
      verify(alertaRepository).save(any(AlertaMantenimiento.class));
      verify(eventPublisher).publishEvent(any(AlertaMantenimientoEvent.class));
   }

   @Test
   void procesar_BloqueaEquipoAlAlcanzarLimiteDeHoras() {
      Equipo eq = equipo(null, 4.0);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.USO, 4));

      assertEquals(EstadoEquipo.EN_MANTENCION, eq.getEstado());
   }

   @Test
   void procesar_ReversaNoDejaContadoresNegativos() {
      Equipo eq = equipo(10, 50.0);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.REVERSA, 5));

      assertEquals(0, eq.getUsosAcumulados());
      assertEquals(0.0, eq.getHorasAcumuladas());
   }

   @Test
   void procesar_FallaBloqueaElEquipo() {
      Equipo eq = equipo(10, 50.0);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.FALLA, 0));

      assertEquals(EstadoEquipo.EN_MANTENCION, eq.getEstado());
      verify(eventPublisher).publishEvent(any(AlertaMantenimientoEvent.class));
   }

   @Test
   void procesar_NoDuplicaAlertaSiYaEstaEnMantencion() {
      Equipo eq = equipo(10, 50.0);
      eq.setEstado(EstadoEquipo.EN_MANTENCION);
      when(equipoRepository.findById(1L)).thenReturn(Optional.of(eq));

      service.procesar(evento("e1", Tipo.FALLA, 0));

      verify(alertaRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any(Object.class));
   }

   @Test
   void procesar_EquipoInexistenteLanzaIllegalArgument() {
      when(equipoRepository.findById(1L)).thenReturn(Optional.empty());

      assertThrows(IllegalArgumentException.class, () -> service.procesar(evento("e1", Tipo.USO, 1)));
      verify(procesadoRepository, never()).save(any());
   }
}
