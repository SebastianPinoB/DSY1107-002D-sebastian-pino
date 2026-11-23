package com.example.javiiland.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import com.example.javiiland.model.dto.KafkaTopicConfigDto;
import com.example.javiiland.model.dto.KafkaTopicRequestDto;
import com.example.javiiland.model.dto.KafkaTopicResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Administración de tópicos Kafka a través del AdminClient. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

    private static final Set<String> TOPICOS_PROTEGIDOS = Set.of(
            UsoEquipamientoPublisher.TOPIC,
            UsoEquipamientoPublisher.TOPIC + ".DLT",
            UsoEquipamientoPublisher.TOPIC_ALERTAS,
            UsoEquipamientoPublisher.TOPIC_ALERTAS + ".DLT");

    private static final int TIMEOUT_SEGUNDOS = 15;

    private final KafkaAdmin kafkaAdmin;

    public List<String> listarTopicos() {
        try (AdminClient admin = crearCliente()) {
            return new ArrayList<>(new TreeSet<>(
                    admin.listTopics().names().get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)));
        } catch (Exception e) {
            throw error(e);
        }
    }

    public KafkaTopicResponseDto describirTopico(String nombre) {
        try (AdminClient admin = crearCliente()) {
            return describir(admin, nombre);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw error(e);
        }
    }

    public KafkaTopicResponseDto crearTopico(KafkaTopicRequestDto dto) {
        try (AdminClient admin = crearCliente()) {
            NewTopic topic = new NewTopic(dto.getName(), dto.getPartitions(), dto.getReplicationFactor());
            if (dto.getConfigs() != null && !dto.getConfigs().isEmpty()) {
                topic.configs(dto.getConfigs());
            }
            admin.createTopics(List.of(topic)).all().get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Tópico creado: {}", dto.getName());
            return describir(admin, dto.getName());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw error(e);
        }
    }

    public KafkaTopicResponseDto actualizarTopico(String nombre, KafkaTopicConfigDto dto) {
        boolean sinConfigs = dto.getConfigs() == null || dto.getConfigs().isEmpty();
        if (sinConfigs && dto.getPartitions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay cambios que aplicar");
        }
        try (AdminClient admin = crearCliente()) {
            int actuales = describir(admin, nombre).getPartitions();

            if (dto.getPartitions() != null) {
                if (dto.getPartitions() <= actuales) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Las particiones solo se pueden aumentar (actuales: " + actuales + ")");
                }
                admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(dto.getPartitions())))
                        .all().get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            }
            if (!sinConfigs) {
                ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
                Collection<AlterConfigOp> ops = new ArrayList<>();
                dto.getConfigs().forEach((k, v) -> ops.add(
                        new AlterConfigOp(new ConfigEntry(k, v), AlterConfigOp.OpType.SET)));
                admin.incrementalAlterConfigs(Map.of(recurso, ops))
                        .all().get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            }
            log.info("Tópico actualizado: {}", nombre);
            return describir(admin, nombre);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw error(e);
        }
    }

    public void eliminarTopico(String nombre) {
        if (nombre.startsWith("__") || TOPICOS_PROTEGIDOS.contains(nombre)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El tópico '" + nombre + "' es parte de la solución y no se puede eliminar");
        }
        try (AdminClient admin = crearCliente()) {
            admin.deleteTopics(List.of(nombre)).all().get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Tópico eliminado: {}", nombre);
        } catch (Exception e) {
            throw error(e);
        }
    }

    // ------------------------------------------------------------------ internos

    AdminClient crearCliente() {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    private KafkaTopicResponseDto describir(AdminClient admin, String nombre)
            throws InterruptedException, ExecutionException, TimeoutException {
        TopicDescription desc = admin.describeTopics(List.of(nombre)).allTopicNames()
                .get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS).get(nombre);

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        var config = admin.describeConfigs(List.of(recurso)).all()
                .get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS).get(recurso);

        Map<String, String> configs = new TreeMap<>();
        config.entries().stream()
                .filter(e -> e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)
                .forEach(e -> configs.put(e.name(), e.value()));

        int rf = desc.partitions().isEmpty() ? 0 : desc.partitions().get(0).replicas().size();
        return KafkaTopicResponseDto.builder()
                .name(nombre)
                .partitions(desc.partitions().size())
                .replicationFactor(rf)
                .configs(configs)
                .build();
    }

    private ResponseStatusException error(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        Throwable causa = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (causa instanceof UnknownTopicOrPartitionException) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tópico no encontrado");
        }
        if (causa instanceof TopicExistsException) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "El tópico ya existe");
        }
        if (causa instanceof InvalidPartitionsException
                || causa instanceof InvalidReplicationFactorException
                || causa instanceof InvalidConfigurationException) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, causa.getMessage());
        }
        log.error("Error de Kafka Admin", causa);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo completar la operación en Kafka");
    }
}
