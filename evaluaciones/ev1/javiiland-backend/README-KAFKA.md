# Kafka en Javiiland: control de uso y mantención de equipamiento

Este módulo registra el desgaste de los juegos (Inflable Castillo, Taca-Taca, Cama elástica, etc.) a partir de las reservas y bloquea automáticamente los equipos que necesitan mantención.

> **RabbitMQ vs Kafka**: RabbitMQ sigue manejando los correos y recordatorios de reservas. Kafka se usa solo para el flujo de **uso de equipamiento**, donde conviene tener un historial de eventos ordenado por equipo.

---

## Resumen de lo implementado

- **Clúster**: Zookeeper, 3 brokers y Kafka-UI en `docker-compose.yml`. Se usa Zookeeper porque la pauta lo pide, aunque las versiones recientes de Kafka usan KRaft.
- **Tópicos**: 4 tópicos con particiones, 3 réplicas, retención (7 o 30 días), `cleanup.policy=delete` y `min.insync.replicas=2`.
- **Productores**: `UsoEquipamientoPublisher` publica los eventos de uso, reversa y falla, y las alertas de mantención, con `acks=all` e idempotencia.
- **Consumidores**: `MantenimientoConsumer` actualiza contadores y bloquea equipos. `AlertaMantenimientoConsumer` envía el correo al administrador.
- **Errores**: reintentos con backoff exponencial y envío a un tópico `.DLT` por cada flujo.
- **Administración**: API REST de tópicos en `/api/admin/kafka/topics` (listar, detalle, crear, modificar y eliminar).
- **Consumer groups**: `mantenimiento` y `notificaciones-admin` (ver más abajo).
- **Pruebas**: pruebas unitarias con Mockito y una prueba de contexto con H2.
- **Ajustes en reservas**: cancelar con `actualizar()` genera la reversa de uso, las reservas de admin aceptan equipos y las alertas se exponen con un DTO.

---

## Ejecutar el backend en local

Con la infraestructura levantada (sección 3.1), desde la carpeta del backend. Ejemplo en PowerShell:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="<clave de tu MySQL local>"
$env:SPRING_MAIL_HOST="localhost"
$env:SPRING_MAIL_PORT="1025"
./mvnw spring-boot:run
```

- MySQL local: puerto 3306 (el MySQL del compose usa el 3307).
- Los correos se capturan en Mailpit: http://localhost:8025.
- Si el puerto 8080 está ocupado (por ejemplo por el contenedor `backend-reservas`), detenerlo o definir `SERVER_PORT`.

---

## 1. Cómo funciona

### Paso a paso

1. **Reserva**: el cliente crea una reserva indicando los equipos y las horas de uso de cada uno.
2. **Validación**: `ReservaService` rechaza con `409` si algún equipo está `EN_MANTENCION` en esa fecha.
3. **Publicación**: solo después del `COMMIT` de la base de datos, `UsoEquipamientoPublisher` envía un evento por equipo al tópico `uso-equipamiento`. Si la transacción falla, no se publica nada.
4. **Consumo**: `MantenimientoConsumer` lee el evento y `MantenimientoService` actualiza los contadores.
5. **Bloqueo preventivo**: si el equipo alcanza `limiteUsos` o `limiteHoras`, queda `EN_MANTENCION`, se guarda una `AlertaMantenimiento` y se publica en `alertas-mantenimiento`.
6. **Notificación**: `AlertaMantenimientoConsumer` lee `alertas-mantenimiento` y envía un correo al administrador (visible en Mailpit, `http://localhost:8025`).
7. **Cierre de mantención**: un admin ejecuta `POST /api/equipos/{id}/mantencion/cerrar`. El equipo vuelve a `DISPONIBLE`, los contadores se reinician y las alertas se marcan como resueltas.

### Tópicos

| Tópico | Particiones | Réplicas | Retención | Contenido | Key |
|---|---|---|---|---|---|
| `uso-equipamiento` | 3 | 3 | 7 días | Eventos `USO`, `REVERSA` y `FALLA` | `equipoId` |
| `uso-equipamiento.DLT` | 3 | 3 | 30 días | Mensajes que no se pudieron procesar | `equipoId` |
| `alertas-mantenimiento` | 1 | 3 | 7 días | Alertas de bloqueo de equipos | `equipoId` |
| `alertas-mantenimiento.DLT` | 1 | 3 | 30 días | Alertas que no se pudieron notificar | `equipoId` |

Todos usan `cleanup.policy=delete` y `min.insync.replicas=2`. Con `acks=all` en el productor, un mensaje solo se confirma cuando al menos 2 brokers lo tienen. La replicación y el mínimo de réplicas sincronizadas se pueden cambiar con `app.kafka.replication-factor` y `app.kafka.min-insync-replicas` (para un solo broker: `1` y `1`).

### Tipos de evento

| Tipo | Cuándo se genera | Efecto |
|---|---|---|
| `USO` | Se crea una reserva con equipos | Suma 1 uso y las horas indicadas |
| `REVERSA` | Se cancela una reserva | Resta 1 uso y las horas (mínimo 0) |
| `FALLA` | Un operario reporta una falla | Bloquea el equipo desde hoy |

### Consumer groups

Un *consumer group* es un conjunto de consumidores que comparten un mismo `groupId` y se reparten el trabajo de un tópico: cada partición la lee **un solo** miembro del grupo, así que cada mensaje se procesa una vez por grupo.

| Listener | Tópico | groupId | Qué ocurre |
|---|---|---|---|
| `MantenimientoConsumer` | `uso-equipamiento` (3 particiones) | `mantenimiento` | Actualiza contadores |
| `AlertaMantenimientoConsumer` | `alertas-mantenimiento` | `notificaciones-admin` | Envía el correo al admin |

- Hoy hay una instancia del backend, por lo que el grupo `mantenimiento` tiene un único miembro que lee las 3 particiones.
- Si se levanta una segunda instancia del backend (mismo `groupId`), Kafka reparte automáticamente las particiones entre ambas. Como la key es `equipoId`, los eventos de un mismo equipo siguen procesándose en orden.
- Como cada listener usa un `groupId` distinto, ambos reciben sus mensajes sin interferirse.
- Se puede ver el estado del grupo en Kafka-UI (menú *Consumers*) o con:

```bash
docker exec kafka1-reservas kafka-consumer-groups \
  --bootstrap-server localhost:29092 --describe --group mantenimiento
```

La salida muestra la partición, el offset actual y el *lag* de cada una.

### Decisiones de diseño

- **Orden por equipo**: usar `equipoId` como key manda todos los eventos de un equipo a la misma partición, así se procesan en orden.
- **Idempotencia**: Kafka entrega "al menos una vez", por lo que un evento puede llegar duplicado. Cada evento lleva un `eventId` (por ejemplo `reserva-12-equipo-3-uso`) que se guarda en la tabla `eventos_procesados`, en la misma transacción que el cambio de contadores. Un duplicado se ignora.
- **Concurrencia**: `Equipo` usa `@Version` (bloqueo optimista) para proteger los contadores.
- **Reintentos y DLT**: ante un error se reintenta 3 veces con backoff exponencial (1s, 2s, 4s). Si sigue fallando, el mensaje va al tópico `.DLT` correspondiente (`uso-equipamiento.DLT` o `alertas-mantenimiento.DLT`). Los mensajes con JSON inválido o con un equipo inexistente van directo al DLT, sin reintentos.
- **Fecha del bloqueo**: si el bloqueo se gatilla por límite, parte el **día siguiente** a la reserva que lo causó (esa reserva sí se realiza). Si es por falla, parte hoy.

---

## 2. Requisitos

- Docker y Docker Compose (el clúster usa 1 Zookeeper y 3 brokers: conviene tener al menos 4 GB de RAM libres)
- Java 21 y Maven (o el wrapper `./mvnw`)
- `curl` (y opcionalmente `jq`)

---

## 3. Ejecutar la demostración

### 3.1 Levantar la infraestructura

```bash
docker compose up -d mysql rabbitmq mailpit zookeeper kafka1 kafka2 kafka3 kafka-ui
docker compose ps        # esperar a que los tres brokers estén "healthy"
```

Servicios expuestos:

| Servicio | Dirección |
|---|---|
| Brokers (desde el host) | `localhost:9092`, `localhost:9093`, `localhost:9094` |
| Kafka-UI | http://localhost:8085 |
| Mailpit | http://localhost:8025 |
| RabbitMQ | http://localhost:15672 |

En Kafka-UI se pueden ver los tópicos, sus particiones y configuración, los mensajes y los consumer groups.

### 3.2 Levantar el backend

En local (fuera de Docker):

```bash
./mvnw spring-boot:run
```

Por defecto el backend se conecta a `localhost:9092`. Si ese broker está caído, se puede indicar la lista completa: `SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094`.

Al arrancar se crean los tópicos automáticamente. Para verificarlo:

```bash
docker exec kafka1-reservas kafka-topics \
  --bootstrap-server localhost:29092 --list
```

Deberías ver `uso-equipamiento`, `uso-equipamiento.DLT`, `alertas-mantenimiento` y `alertas-mantenimiento.DLT`. Para ver particiones, réplicas y configuración:

```bash
docker exec kafka1-reservas kafka-topics \
  --bootstrap-server localhost:29092 --describe --topic uso-equipamiento
```

### 3.3 Abrir los monitores (cada uno en su propia terminal)

**Terminal A**: eventos de uso

```bash
docker exec -it kafka1-reservas kafka-console-consumer \
  --bootstrap-server localhost:29092 --topic uso-equipamiento \
  --from-beginning --property print.key=true
```

**Terminal B**: alertas

```bash
docker exec -it kafka1-reservas kafka-console-consumer \
  --bootstrap-server localhost:29092 --topic alertas-mantenimiento \
  --from-beginning --property print.key=true
```

**Terminal C**: dead letter topic

```bash
docker exec -it kafka1-reservas kafka-console-consumer \
  --bootstrap-server localhost:29092 --topic uso-equipamiento.DLT \
  --from-beginning --property print.key=true
```

### 3.4 Obtener un token de ADMIN

> Ajusta el body según tu `LoginRequestDto`. Por defecto el admin es `admin` / `admin123`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/usuarios/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

echo $TOKEN
```

> Si tu `LoginResponseDto` nombra distinto el campo del token (por ejemplo `accessToken`), cambia el `jq`.

### 3.5 Escenario 1: crear el catálogo de equipos

Para que la demo sea rápida, usamos límites bajos.

```bash
curl -X POST http://localhost:8080/api/equipos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Inflable Castillo","limiteUsos":3}'

curl -X POST http://localhost:8080/api/equipos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Taca-Taca #2","limiteHoras":10}'

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/equipos
```

Anota los `id` devueltos. En los ejemplos: Inflable Castillo = `1`, Taca-Taca #2 = `2`.

### 3.6 Escenario 2: reserva con equipos (flujo normal)

Cada día admite una sola reserva, así que usa fechas distintas y futuras (ajusta las fechas a las de tu calendario).

```bash
curl -X POST http://localhost:8080/api/reservas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "reservationDate": "2026-12-10",
    "eventName": "Cumpleaños de Sofía",
    "description": "Demo Kafka",
    "equipos": [
      {"equipoId": 1, "horas": 4},
      {"equipoId": 2, "horas": 4}
    ]
  }'
```

**Qué observar**

- Terminal A: dos mensajes, uno con key `1` y otro con key `2`.
- Logs del backend: `Publicado reserva-X-equipo-1-uso -> partición N`.
- Contadores actualizados:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/equipos
```

El Inflable Castillo debería tener `usosAcumulados: 1` y el Taca-Taca `horasAcumuladas: 4`.

### 3.7 Escenario 3: bloqueo automático por límite

Crea dos reservas más con el Inflable Castillo en fechas distintas:

```bash
for FECHA in 2026-12-11 2026-12-12; do
  curl -X POST http://localhost:8080/api/reservas \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"reservationDate\":\"$FECHA\",\"eventName\":\"Demo $FECHA\",\"equipos\":[{\"equipoId\":1,\"horas\":3}]}"
done
```

Con la tercera reserva el Inflable llega a 3 usos y se bloquea.

**Qué observar**

- Terminal B: la alerta con `motivo: LIMITE_USOS` y `bloqueadoDesde: 2026-12-13`.
- Logs: `Equipo Inflable Castillo EN_MANTENCION desde 2026-12-13`.
- Estado del equipo: `estado: EN_MANTENCION`.

Ahora intenta reservarlo el día 13 o posterior:

```bash
curl -i -X POST http://localhost:8080/api/reservas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"reservationDate":"2026-12-13","eventName":"Debe fallar","equipos":[{"equipoId":1,"horas":2}]}'
```

Respuesta esperada: `409 Conflict` con "El equipo 'Inflable Castillo' está en mantención en esa fecha".

### 3.8 Escenario 4: reporte de falla por un operario

```bash
curl -i -X POST http://localhost:8080/api/equipos/2/falla \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"detalle":"El motor del Taca-Taca hace ruido"}'
```

Respuesta: `202 Accepted` (el bloqueo se aplica de forma asíncrona).

- Terminal A: evento `FALLA` para el equipo 2.
- Terminal B: alerta con `motivo: FALLA_REPORTADA`.
- Mailpit (http://localhost:8025): correo "Equipo en mantención" dirigido al admin.
- Alertas pendientes:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/mantenimiento/alertas
```

### 3.9 Escenario 5: cerrar la mantención

```bash
curl -X POST http://localhost:8080/api/equipos/1/mantencion/cerrar \
  -H "Authorization: Bearer $TOKEN"
```

El equipo vuelve a `DISPONIBLE` con contadores en 0, y ya se puede reservar de nuevo.

### 3.10 Escenario 6: cancelación (evento REVERSA)

Cancela una reserva con equipos (ajusta el `id` y el método/ruta a los de tu `ReservaController`):

```bash
curl -X PUT http://localhost:8080/api/reservas/1/cancelar \
  -H "Authorization: Bearer $TOKEN"
```

Terminal A: aparece un evento `REVERSA` por cada equipo y los contadores bajan.

### 3.11 Escenario 7: idempotencia (mensaje duplicado)

Publica manualmente el mismo evento dos veces. Cambia `equipoId` por un equipo existente y `fecha` por una fecha válida:

```bash
docker exec -i kafka1-reservas kafka-console-producer \
  --bootstrap-server localhost:29092 --topic uso-equipamiento \
  --property parse.key=true --property key.separator='|' <<'EOF'
2|{"eventId":"demo-duplicado-1","tipo":"USO","reservaId":null,"equipoId":2,"horas":1.0,"fecha":"2026-12-20","detalle":null}
2|{"eventId":"demo-duplicado-1","tipo":"USO","reservaId":null,"equipoId":2,"horas":1.0,"fecha":"2026-12-20","detalle":null}
EOF
```

**Resultado**: en los logs del backend aparece una vez el procesamiento y una vez `Evento duplicado ignorado: demo-duplicado-1`. Las horas del equipo suben solo 1.

### 3.12 Escenario 8: Dead Letter Topic

**Caso A, mensaje corrupto**:

```bash
docker exec -i kafka1-reservas kafka-console-producer \
  --bootstrap-server localhost:29092 --topic uso-equipamiento \
  --property parse.key=true --property key.separator='|' <<'EOF'
1|esto-no-es-json
EOF
```

**Caso B, equipo inexistente**:

```bash
docker exec -i kafka1-reservas kafka-console-producer \
  --bootstrap-server localhost:29092 --topic uso-equipamiento \
  --property parse.key=true --property key.separator='|' <<'EOF'
999|{"eventId":"demo-dlt-1","tipo":"USO","reservaId":null,"equipoId":999,"horas":1.0,"fecha":"2026-12-20","detalle":null}
EOF
```

**Resultado**: ambos mensajes aparecen en la Terminal C (`uso-equipamiento.DLT`), con los headers de error de Spring Kafka. El consumidor principal sigue funcionando sin quedarse bloqueado.

### 3.13 Escenario 9: administración de tópicos por API

Los endpoints de `/api/admin/kafka/topics` (solo ADMIN) permiten listar, describir, crear, modificar y eliminar tópicos. Las peticiones de ejemplo están en `kafka-admin.rest`.

```bash
# listar
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/kafka/topics

# crear con configuración
curl -X POST http://localhost:8080/api/admin/kafka/topics \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"prueba-admin","partitions":2,"replicationFactor":3,"configs":{"retention.ms":"86400000"}}'

# cambiar retención y aumentar particiones
curl -X PATCH http://localhost:8080/api/admin/kafka/topics/prueba-admin \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"partitions":4,"configs":{"retention.ms":"172800000"}}'

# eliminar
curl -X DELETE http://localhost:8080/api/admin/kafka/topics/prueba-admin \
  -H "Authorization: Bearer $TOKEN"
```

Reglas: las particiones solo pueden aumentar (`400` si se intenta reducir), un tópico existente responde `409`, uno inexistente `404`, y los tópicos de la solución y los internos (`__*`) no se pueden eliminar (`403`).

---

## 4. Pruebas automatizadas

```bash
./mvnw test
```

- `MantenimientoServiceTest`: idempotencia, bloqueo por límite de usos y de horas, reversa sin valores negativos, bloqueo por falla, alerta no duplicada y equipo inexistente.
- `KafkaAdminServiceTest`: protección de tópicos de la solución y validación de cambios vacíos.
- `ReservaProcesosTest`: idempotencia del correo de confirmación.

Son pruebas unitarias con Mockito, no requieren broker. `JaviilandApplicationTests` levanta el contexto completo y necesita MySQL, RabbitMQ y Kafka activos.

---

## 5. Verificar el estado de la base de datos

```sql
SELECT id, nombre, estado, usos_acumulados, horas_acumuladas, mantencion_desde FROM equipos;
SELECT * FROM alertas_mantenimiento ORDER BY creada_en DESC;
SELECT * FROM eventos_procesados;
SELECT * FROM reserva_equipos;
```

Con el `docker-compose.yml` actual MySQL expone el puerto `3307`:

```bash
docker exec -it <contenedor-mysql> mysql -uroot -proot reservas
```

---

## 6. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El backend no arranca: `Connection refused` a Kafka | Broker no levantado o `bootstrap-servers` incorrecto | Revisar `docker compose ps`. En local usar `localhost:9092`; dentro de Docker, `kafka1:29092,kafka2:29092,kafka3:29092` |
| Los mensajes se publican pero nadie los consume | Group id o tipo de deserialización incorrectos | Revisar `application.yml` (`group-id: mantenimiento`, `spring.json.value.default.type`) |
| `409` al reservar sin razón aparente | Ya existe una reserva `CONFIRMED` ese día | Usar otra fecha |
| Contadores no cambian | El consumidor falló: revisar el DLT y los logs | `kafka-console-consumer` sobre `uso-equipamiento.DLT` |
| `Replication factor: 3 larger than available brokers` | Solo hay un broker levantado | Levantar `kafka1`, `kafka2` y `kafka3`, o usar `app.kafka.replication-factor=1` y `app.kafka.min-insync-replicas=1` |
| `NOT_ENOUGH_REPLICAS` al publicar | Hay menos de 2 brokers disponibles | Levantar el broker caído: `docker compose up -d kafka2` |
| `JaviilandApplicationTests` falla | Faltan servicios de infraestructura | Levantar el compose antes de correr las pruebas |

### Reiniciar la demo desde cero

```bash
docker compose down -v      # borra volúmenes (MySQL, RabbitMQ y Kafka)
docker compose up -d mysql rabbitmq mailpit zookeeper kafka1 kafka2 kafka3 kafka-ui
```

---

## 7. Archivos del módulo

```
config/KafkaConfig.java
controller/MantenimientoController.java
messaging/kafka/UsoEquipamientoEvent.java
messaging/kafka/UsoEquipamientoCambioEvent.java
messaging/kafka/AlertaMantenimientoEvent.java
messaging/kafka/UsoEquipamientoPublisher.java
messaging/kafka/MantenimientoConsumer.java
messaging/kafka/AlertaMantenimientoConsumer.java
controller/KafkaAdminController.java
service/KafkaAdminService.java
model/dto/KafkaTopicRequestDto.java · KafkaTopicConfigDto.java · KafkaTopicResponseDto.java · AlertaResponseDto.java
model/Equipo.java · EstadoEquipo.java · MotivoAlerta.java
model/ReservaEquipo.java · AlertaMantenimiento.java · EventoProcesado.java
model/dto/EquipoUsoDto.java · EquipoRequestDto.java · EquipoResponseDto.java · ReporteFallaDto.java
repository/EquipoRepository.java · ReservaEquipoRepository.java
repository/AlertaMantenimientoRepository.java · EventoProcesadoRepository.java
service/MantenimientoService.java
```


