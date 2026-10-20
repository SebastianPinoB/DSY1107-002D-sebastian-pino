package com.example.javiiland.service;

import java.util.Set;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.model.dto.BindingRequestDto;
import com.example.javiiland.model.dto.ExchangeRequestDto;
import com.example.javiiland.model.dto.QueueRequestDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsula toda la administración de RabbitMQ (colas, exchanges y bindings).
 * El controller solo llama a estos métodos y no conoce la librería de RabbitMQ.
 *
 * Las colas y exchanges que usa el propio sistema de reservas están protegidos:
 * no se pueden borrar desde la API porque dejarían sin funcionar los listeners.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitAdminService {

    private static final Set<String> COLAS_PROTEGIDAS = Set.of(
            RabbitConfig.Q_CREADA, RabbitConfig.Q_RECORDATORIO, RabbitConfig.Q_EXPIRADA,
            RabbitConfig.Q_ESPERA_48H, RabbitConfig.DLQ);

    private static final Set<String> EXCHANGES_PROTEGIDOS = Set.of(
            RabbitConfig.EX, RabbitConfig.EX_DELAYED, RabbitConfig.DLX);

    private final AmqpAdmin admin;

    // ===================== Colas =====================

    public String crearCola(QueueRequestDto dto) {
        String nombre = dto.name().trim();
        if (COLAS_PROTEGIDAS.contains(nombre) || admin.getQueueInfo(nombre) != null) {
            throw error(HttpStatus.CONFLICT, "La cola '" + nombre + "' ya existe");
        }

        boolean durable = dto.durable() == null || dto.durable();
        QueueBuilder builder = durable ? QueueBuilder.durable(nombre) : QueueBuilder.nonDurable(nombre);
        if (hayTexto(dto.deadLetterExchange())) {
            builder.deadLetterExchange(dto.deadLetterExchange().trim());
        }
        if (hayTexto(dto.deadLetterRoutingKey())) {
            builder.deadLetterRoutingKey(dto.deadLetterRoutingKey().trim());
        }
        if (dto.ttlMs() != null) {
            builder.ttl(dto.ttlMs());
        }

        Queue cola = builder.build();
        ejecutar("crear cola " + nombre, () -> admin.declareQueue(cola));
        log.info("[RABBIT-ADMIN] cola creada: {}", nombre);
        return "Cola '" + nombre + "' creada";
    }

    public String eliminarCola(String nombre) {
        validarNombre(nombre, "cola");
        if (COLAS_PROTEGIDAS.contains(nombre)) {
            throw error(HttpStatus.CONFLICT, "La cola '" + nombre + "' la usa el sistema y no se puede eliminar");
        }
        if (admin.getQueueInfo(nombre) == null) {
            throw error(HttpStatus.NOT_FOUND, "La cola '" + nombre + "' no existe");
        }
        ejecutar("eliminar cola " + nombre, () -> admin.deleteQueue(nombre));
        log.info("[RABBIT-ADMIN] cola eliminada: {}", nombre);
        return "Cola '" + nombre + "' eliminada";
    }

    // ===================== Exchanges =====================

    public String crearExchange(ExchangeRequestDto dto) {
        String nombre = dto.name().trim();
        if (nombre.startsWith("amq.")) {
            throw error(HttpStatus.BAD_REQUEST, "Los nombres que empiezan con 'amq.' están reservados por RabbitMQ");
        }
        if (EXCHANGES_PROTEGIDOS.contains(nombre)) {
            throw error(HttpStatus.CONFLICT, "El exchange '" + nombre + "' ya existe");
        }

        boolean durable = dto.durable() == null || dto.durable();
        Exchange exchange = switch (dto.type()) {
            case "direct" -> new DirectExchange(nombre, durable, false);
            case "topic" -> new TopicExchange(nombre, durable, false);
            case "fanout" -> new FanoutExchange(nombre, durable, false);
            case "headers" -> new HeadersExchange(nombre, durable, false);
            default -> throw error(HttpStatus.BAD_REQUEST, "Tipo de exchange no soportado: " + dto.type());
        };

        ejecutar("crear exchange " + nombre, () -> admin.declareExchange(exchange));
        log.info("[RABBIT-ADMIN] exchange creado: {} ({})", nombre, dto.type());
        return "Exchange '" + nombre + "' (" + dto.type() + ") creado";
    }

    /** RabbitMQ no falla si el exchange no existe, por eso no se devuelve 404 aquí. */
    public String eliminarExchange(String nombre) {
        validarNombre(nombre, "exchange");
        if (EXCHANGES_PROTEGIDOS.contains(nombre)) {
            throw error(HttpStatus.CONFLICT, "El exchange '" + nombre + "' lo usa el sistema y no se puede eliminar");
        }
        ejecutar("eliminar exchange " + nombre, () -> admin.deleteExchange(nombre));
        log.info("[RABBIT-ADMIN] exchange eliminado: {}", nombre);
        return "Exchange '" + nombre + "' eliminado";
    }

    // ===================== Bindings =====================

    public String crearBinding(BindingRequestDto dto) {
        Binding binding = aBinding(dto);
        if (binding.getDestinationType() == DestinationType.QUEUE
                && admin.getQueueInfo(binding.getDestination()) == null) {
            throw error(HttpStatus.NOT_FOUND, "La cola destino '" + binding.getDestination() + "' no existe");
        }
        ejecutar("crear binding", () -> admin.declareBinding(binding));
        log.info("[RABBIT-ADMIN] binding creado: {} -[{}]-> {}", dto.exchange(), dto.routingKey(), dto.destination());
        return "Binding " + dto.exchange() + " -[" + dto.routingKey() + "]-> " + dto.destination() + " creado";
    }

    public String eliminarBinding(BindingRequestDto dto) {
        Binding binding = aBinding(dto);
        if (EXCHANGES_PROTEGIDOS.contains(dto.exchange()) && COLAS_PROTEGIDAS.contains(dto.destination())) {
            throw error(HttpStatus.CONFLICT, "Ese binding lo usa el sistema y no se puede eliminar");
        }
        ejecutar("eliminar binding", () -> admin.removeBinding(binding));
        log.info("[RABBIT-ADMIN] binding eliminado: {} -[{}]-> {}", dto.exchange(), dto.routingKey(), dto.destination());
        return "Binding " + dto.exchange() + " -[" + dto.routingKey() + "]-> " + dto.destination() + " eliminado";
    }

    // ===================== Utilidades =====================

    private Binding aBinding(BindingRequestDto dto) {
        DestinationType tipo = "queue".equals(dto.destinationType())
                ? DestinationType.QUEUE
                : DestinationType.EXCHANGE;
        return new Binding(dto.destination().trim(), tipo, dto.exchange().trim(), dto.routingKey(), null);
    }

    /** Los nombres que llegan por la URL no pasan por @Valid, así que se validan aquí. */
    private void validarNombre(String nombre, String recurso) {
        if (nombre == null || nombre.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "El nombre de la " + recurso + " no puede estar vacío");
        }
        if (!nombre.matches("^[A-Za-z0-9._-]{1,255}$")) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Nombre de " + recurso + " inválido: solo letras, números, '.', '_' y '-' (máx. 255)");
        }
    }

    private boolean hayTexto(String s) {
        return s != null && !s.isBlank();
    }

    private ResponseStatusException error(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }

    /** Traduce los errores del broker a respuestas HTTP claras. */
    private void ejecutar(String accion, Runnable operacion) {
        try {
            operacion.run();
        } catch (AmqpConnectException e) {
            log.error("[RABBIT-ADMIN] sin conexión con RabbitMQ al {}", accion, e);
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "No hay conexión con RabbitMQ");
        } catch (AmqpException e) {
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            log.warn("[RABBIT-ADMIN] RabbitMQ rechazó '{}': {}", accion, causa.getMessage());
            throw error(HttpStatus.BAD_REQUEST, "RabbitMQ rechazó la operación (" + accion + "): " + causa.getMessage());
        }
    }
}
