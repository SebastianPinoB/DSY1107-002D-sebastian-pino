package com.example.javiiland.config;

import com.example.javiiland.messaging.kafka.UsoEquipamientoPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
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
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

   private static final String SIETE_DIAS_MS = String.valueOf(7L * 24 * 60 * 60 * 1000);
   private static final String TREINTA_DIAS_MS = String.valueOf(30L * 24 * 60 * 60 * 1000);

   @Value("${app.kafka.replication-factor:3}")
   private short replicationFactor;

   @Value("${app.kafka.min-insync-replicas:2}")
   private String minInsyncReplicas;

   private NewTopic topic(String nombre, int particiones, String retencionMs) {
      return TopicBuilder.name(nombre)
            .partitions(particiones)
            .replicas(replicationFactor)
            .config(TopicConfig.RETENTION_MS_CONFIG, retencionMs)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsyncReplicas)
            .build();
   }

   @Bean
   NewTopic usoEquipamientoTopic() {
      return topic(UsoEquipamientoPublisher.TOPIC, 3, SIETE_DIAS_MS);
   }

   // misma cantidad de particiones que el original: el recoverer publica en la misma partición
   @Bean
   NewTopic usoEquipamientoDlt() {
      return topic(UsoEquipamientoPublisher.TOPIC + ".DLT", 3, TREINTA_DIAS_MS);
   }

   @Bean
   NewTopic alertasTopic() {
      return topic(UsoEquipamientoPublisher.TOPIC_ALERTAS, 1, SIETE_DIAS_MS);
   }

   @Bean
   NewTopic alertasDlt() {
      return topic(UsoEquipamientoPublisher.TOPIC_ALERTAS + ".DLT", 1, TREINTA_DIAS_MS);
   }

   @Bean("kafkaTemplate")
   KafkaTemplate<String, Object> kafkaTemplate(
         @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
      Map<String, Object> producerProperties = new LinkedHashMap<>();
      producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
      producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
      producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
      producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
      producerProperties.put("spring.json.add.type.headers", false);

      return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
   }

   @Bean("kafkaRawTemplate")
   KafkaTemplate<String, String> kafkaRawTemplate(
         @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
      Map<String, Object> producerProperties = new LinkedHashMap<>();
      producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
      producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");

      return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
   }

   /**
    * Reintenta 3 veces con backoff exponencial (1s, 2s, 4s) y luego envía el
    * mensaje a <topic>.DLT. Spring Boot aplica este bean al listener.
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

      var recoverer = new DeadLetterPublishingRecoverer(templates,
            (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
      var handler = new DefaultErrorHandler(recoverer, backoff);
      handler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class);
      return handler;
   }
}