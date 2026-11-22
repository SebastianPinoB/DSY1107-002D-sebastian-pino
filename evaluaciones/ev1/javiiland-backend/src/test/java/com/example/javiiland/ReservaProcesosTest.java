package com.example.javiiland;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.javiiland.model.Reserva;
import com.example.javiiland.model.ReservaStatus;
import com.example.javiiland.model.TipoReserva;
import com.example.javiiland.repository.ReservaRepository;
import com.example.javiiland.service.EmailService;
import com.example.javiiland.service.QrService;
import com.example.javiiland.service.ReservaProcesos;

public class ReservaProcesosTest {
   @Mock
   private ReservaRepository reservaRepository;

   @Mock
   private EmailService emailService;

   @Mock
   private QrService qrService;

   @InjectMocks
   private ReservaProcesos reservaProcesos;

   @BeforeEach
   void setUp() {
      MockitoAnnotations.openMocks(this);
   }

   @Test
   void enviarConfirmacion_DebeSerIdempotente_SiYaFueEnviado() {
      // Arrange
      Long reservaId = 1L;
      Reserva reserva = new Reserva();
      reserva.setId(reservaId);
      reserva.setTipo(TipoReserva.CLIENTE);
      reserva.setEstatus(ReservaStatus.CONFIRMED);
      reserva.setEmailEnviado(true); // Ya fue enviado

      when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reserva));

      // Act
      reservaProcesos.enviarConfirmacion(reservaId);

      // Assert: Verifica que NUNCA intente generar el QR ni enviar el correo de nuevo
      verify(qrService, never()).generar(any());
      verify(emailService, never()).enviarConfirmacion(any(), any());
   }

   @Test
   void confirmarAsistencia_DebeRetornarTrue_CuandoActualizaCorrectamente() {
      // Arrange
      String token = "tokenvalido123";
      when(reservaRepository.confirmarAsistencia(token)).thenReturn(1);

      // Act
      boolean resultado = reservaProcesos.confirmarAsistencia(token);

      // Assert
      assert (resultado);
      verify(reservaRepository, times(1)).confirmarAsistencia(token);
   }
}
