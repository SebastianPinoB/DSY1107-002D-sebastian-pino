package Jar;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class Sender {
    @Autowired
    private final RabbitTemplate rabbitTemplate;

    // Inyección de dependencias por constructor
    public Sender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Método para enviar un mensaje simple
     *
     * Parámetros:
     * - routingKey: "hello" (nombre de la cola destino)
     * - message: el contenido del mensaje
     */
    public void sendMessage(String message) {
        try {
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));

            String fullMessage = String.format(
                "[%s] %s",
                timestamp,
                message
            );
            
            // Enviar el mensaje a la cola (usa el exchange por defecto si se pasa directamente la cola como routingKey)
            rabbitTemplate.convertAndSend("hello", fullMessage);
            
            System.out.println("[x] Mensaje enviado: '" + fullMessage + "'");
        } catch (Exception e) {
            System.err.println("[!] Error enviando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String exchange, String routingKey, String message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            System.out.println("[x] Mensaje enviado a exchange='" + exchange + "', routingKey='" + routingKey + "': '" + message + "'");
        } catch (Exception e) {
            System.err.println("[!] Error enviando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }
}