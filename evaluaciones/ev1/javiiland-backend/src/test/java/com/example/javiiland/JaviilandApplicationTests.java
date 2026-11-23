package com.example.javiiland;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
      "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
	  "spring.mail.host=localhost",
      "spring.kafka.listener.auto-startup=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
})
class JaviilandApplicationTests {

   @Test
   void contextLoads() {
   }
}
