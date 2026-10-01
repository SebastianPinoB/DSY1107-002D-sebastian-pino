package Jar;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.*;

/**
 * Configuración de RabbitMQ
 * 202
 * 8
 *
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Configuration
public class RabbitMQConfig {
    /**
     * Define la cola "hello"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=false: se borra si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue helloQueue() {
        return new Queue("hello", false);
    }

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDERS_QUEUE = "orders.queue";
    public static final String ORDERS_ROUTING_KEY = "order.routing.key";

    public static final String DLX_EXCHANGE = "orders.dlx.exchange";
    public static final String DLQ_QUEUE = "orders.dlq";

    // 1. Exchange Principal
    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    // 2. Cola Principal con DLX configurado
    @Bean
    public Queue ordersQueue() {
        return QueueBuilder.durable(ORDERS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dlq.routing.key")
                .build();
    }

    // 3. Binding entre Exchange Principal y Cola Principal
    @Bean
    public Binding ordersBinding() {
        return BindingBuilder.bind(ordersQueue()).to(ordersExchange()).with(ORDERS_ROUTING_KEY);
    }

    // 4. Exchange de Dead Letter
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    // 5. Cola de Cuarentena (DLQ)
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // 6. Binding para el DLQ
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with("dlq.routing.key");
    }
}