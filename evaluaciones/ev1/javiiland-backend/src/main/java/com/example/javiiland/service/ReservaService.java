package com.example.javiiland.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.javiiland.model.Reserva;
import com.example.javiiland.model.ReservaStatus;
import com.example.javiiland.model.Usuario;
import com.example.javiiland.model.dto.CalendarioDto;
import com.example.javiiland.model.dto.ReservaRequestDto;
import com.example.javiiland.model.dto.ReservaResponseDto;
import com.example.javiiland.model.dto.ReservaUpdateDto;
import com.example.javiiland.repository.ReservaRepository;
import com.example.javiiland.repository.UsuarioRepository;

@Service
public class ReservaService {
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;

    public ReservaService(ReservaRepository reservaRepository, UsuarioRepository usuarioRepository) {
        this.reservaRepository = reservaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ReservaResponseDto crear(Long usuarioId, ReservaRequestDto request) {
        if (reservaRepository.existsByFechaReservaAndEstatus(request.getReservationDate(), ReservaStatus.CONFIRMED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La fecha ya está reservada");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Reserva reserva = Reserva.builder()
                .fechaReserva(request.getReservationDate())
                .nombreEvento(request.getEventName())
                .descripcion(request.getDescription())
                .estatus(ReservaStatus.CONFIRMED)
                .usuario(usuario)
                .build();
        return toResponse(reservaRepository.save(reserva));
    }

    @Transactional(readOnly = true)
    public List<ReservaResponseDto> listar() {
        return reservaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservaResponseDto> listarPorUsuario(Long usuarioId) {
        return reservaRepository.findByUsuarioId(usuarioId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReservaResponseDto buscarPorId(Long id) {
        return toResponse(obtener(id));
    }

    @Transactional
    public ReservaResponseDto actualizar(Long id, ReservaUpdateDto request) {
        Reserva reserva = obtener(id);
        if (request.getReservationDate() != null && !request.getReservationDate().equals(reserva.getFechaReserva())
                && reservaRepository.existsByFechaReservaAndEstatus(request.getReservationDate(), ReservaStatus.CONFIRMED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La fecha ya está reservada");
        }
        if (request.getReservationDate() != null) reserva.setFechaReserva(request.getReservationDate());
        if (request.getEventName() != null) reserva.setNombreEvento(request.getEventName());
        if (request.getDescription() != null) reserva.setDescripcion(request.getDescription());
        if (request.getStatus() != null) reserva.setEstatus(request.getStatus());
        return toResponse(reservaRepository.save(reserva));
    }

    @Transactional
    public ReservaResponseDto cancelar(Long id) {
        Reserva reserva = obtener(id);
        reserva.setEstatus(ReservaStatus.CANCELLED);
        return toResponse(reservaRepository.save(reserva));
    }

    @Transactional(readOnly = true)
    public List<CalendarioDto> calendario(LocalDate inicio, LocalDate fin) {
        List<Reserva> reservas = reservaRepository.findByFechaReservaBetweenAndEstatus(
                inicio, fin, ReservaStatus.CONFIRMED);
        return inicio.datesUntil(fin.plusDays(1))
                .map(date -> CalendarioDto.builder()
                        .date(date)
                        .available(reservas.stream().noneMatch(reserva -> reserva.getFechaReserva().equals(date)))
                        .build())
                .toList();
    }

    private Reserva obtener(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private ReservaResponseDto toResponse(Reserva reserva) {
        return ReservaResponseDto.builder()
                .id(reserva.getId())
                .fechaReserva(reserva.getFechaReserva())
                .nombreEvento(reserva.getNombreEvento())
                .descripcion(reserva.getDescripcion())
                .status(reserva.getEstatus())
                .usuarioId(reserva.getUsuario().getId())
                .nombreUsuario(reserva.getUsuario().getNombre())
                .creadoEn(reserva.getCreadaEn())
                .actualizadoEn(reserva.getActualizadaEn())
                .build();
    }
}