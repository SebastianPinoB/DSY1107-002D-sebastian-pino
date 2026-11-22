package com.example.javiiland.config;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

   @Bean
   NewTopic usoEquipamientoTopic() {
      return TopicBuilder.name(UsoEquipamientoPublisher.TOPIC).partitions(3).replicas(1).build();
   }

   @Bean
   NewTopic usoEquipamientoDlt() {
      // misma cantidad de particiones que el original (el recoverer usa la misma
      // partición)
      return TopicBuilder.name(UsoEquipamientoPublisher.TOPIC + ".DLT").partitions(3).replicas(1).build();
   }

   @Bean
   NewTopic alertasTopic() {
      return TopicBuilder.name(UsoEquipamientoPublisher.TOPIC_ALERTAS).partitions(1).replicas(1).build();
   }

   /**
    * Reintenta 3 veces con backoff exponencial (1s, 2s, 4s) y luego envía el
    * mensaje a uso-equipamiento.DLT. Spring Boot aplica este bean al listener.
    */
   @Bean
   DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template,
         @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {

      // Los mensajes que no se pudieron deserializar llegan como byte[]: necesitan su
      // propio serializador
      KafkaTemplate<String, byte[]> bytesTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));

      Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
      templates.put(byte[].class, bytesTemplate);
      templates.put(Object.class, template);

      var backoff = new ExponentialBackOffWithMaxRetries(3);
      backoff.setInitialInterval(1_000);
      backoff.setMultiplier(2.0);

      var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(templates), backoff);
      handler.addNotRetryableExceptions(IllegalArgumentException.class); // datos inválidos: directo a DLT
      return handler;
   }
}