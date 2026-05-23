# Arquitectura — DFS-UPB

## 1. Visión general

Sistema de archivos distribuido por bloques, semántica WORM, arquitectura
Maestro–Trabajador inspirada en GFS/HDFS.

* **Cliente CLI**: punto de entrada del usuario; particiona, calcula hash y
  envía/recupera bloques. Se autentica contra el NameNode y arrastra el JWT
  obtenido a las llamadas a DataNodes.
* **NameNode**: servidor central de metadatos. Mantiene namespace, mapa
  archivo→bloques→DataNodes, asigna réplicas, recibe heartbeats/block reports
  y orquesta re-replicación.
* **DataNode** (≥3): almacena bloques en disco local, ejecuta replicación
  pipeline al recibir un PUT, expone GET con verificación SHA-256, y reporta
  estado al maestro.

## 2. Componentes y responsabilidades

| Componente | Lenguaje / Stack | Responsabilidades |
|------------|------------------|-------------------|
| NameNode   | Java 17, Spring Boot 3, Spring Data JPA + H2 | Auth (BCrypt + JWT), namespace (mkdir/rmdir/ls), files (create/commit/get/delete), asignación de DataNodes, detección de fallos, re-replicación, GC de réplicas. |
| DataNode   | Java 17, Spring Boot 3 | Almacenamiento físico de bloques, validación SHA-256, replicación pipeline, heartbeat + block report, GC dirigido por el NameNode. |
| Cliente    | Java 17 + Picocli (JAR shadeado) | Particiona archivos, sube a DataNode primario con header `X-DFS-Pipeline` para arrastrar la cadena, descarga con failover entre réplicas y verifica integridad. |

## 3. Diagrama de secuencia: PUT

```
Cliente             NameNode              DataNode-A           DataNode-B
   |                   |                       |                    |
   |  POST /auth/login |                       |                    |
   |------------------>|                       |                    |
   |   200 (jwt)       |                       |                    |
   |<------------------|                       |                    |
   |                   |                       |                    |
   |  POST /files      |                       |                    |
   |  {path,size,N}    |                       |                    |
   |------------------>|                       |                    |
   |   plan: blocks[]  |                       |                    |
   |   con réplicas    |                       |                    |
   |<------------------|                       |                    |
   |                   |                       |                    |
   |  PUT /blocks/B1   (X-DFS-Hash + X-DFS-Pipeline=B)              |
   |--------------------------------------->|                       |
   |                   |                    |  PUT /blocks/B1       |
   |                   |                    |--------------------->|
   |                   |                    |     200 OK (hash)     |
   |                   |                    |<---------------------|
   |   200 OK          |                    |                       |
   |<---------------------------------------|                       |
   |                   |                       |                    |
   |  POST /files/commit                       |                    |
   |------------------>|                       |                    |
   |   200 OK          |                       |                    |
   |<------------------|                       |                    |
```

## 4. Diagrama de secuencia: GET

```
Cliente              NameNode              DataNode-A    DataNode-B
   |                    |                       |             |
   |  GET /files/meta   |                       |             |
   |------------------->|                       |             |
   |   blocks+replicas  |                       |             |
   |<-------------------|                       |             |
   |                                            |             |
   |  GET /blocks/B1   (réplica aleatoria)      |             |
   |------------------------------------------->|             |
   |     200 + bytes + X-DFS-Hash               |             |
   |<-------------------------------------------|             |
   |                                                          |
   |  (verifica SHA-256; si falla, reintenta con la otra)     |
   |                                                          |
   |  Concatena bloques en orden 0..N-1 y escribe el archivo  |
```

## 5. Detección de fallos y re-replicación

* Cada DataNode envía heartbeat cada `heartbeat-interval-seconds` (default 3 s).
* El NameNode marca `SUSPECT` tras `HB_SUSPECT_SECONDS` (10 s) y `DEAD` tras
  `HB_DEAD_SECONDS` (30 s).  Cuando un DN pasa a `DEAD`, todas sus réplicas
  `LIVE` se marcan `DELETED`, lo que bajará el factor efectivo de los bloques
  afectados.
* Un job programado en el NameNode (`reReplicate`, cada
  `rereplication-interval-seconds`) busca bloques `LIVE_count < replication`,
  selecciona un DN saludable distinto, y le envía una orden
  `POST /blocks/{id}/replicate` a un DN que aún tenga el bloque, el cual lo
  copia al destino con `PUT /blocks/{id}` (incluyendo el hash).
* Cuando el destino confirma, el NameNode actualiza la réplica como `LIVE`.

## 6. Manejo de bloques corruptos

* El cliente verifica SHA-256 en cada bloque descargado.  Ante mismatch,
  reintenta automáticamente con la siguiente réplica (failover transparente).
* Los DataNodes verifican SHA-256 al recibir un bloque; descarte ante mismatch
  con HTTP 400.
* En caso de corrupción detectada por el cliente, podría notificarse al
  NameNode para forzar re-replicación. Para mantener el alcance, esta
  notificación está cableada como endpoint placeholder; el job de
  re-replicación recoge las réplicas que ya estén marcadas `STALE/DELETED`.

## 7. Algoritmo de asignación de DataNodes

Para cada bloque, el `DataNodeService` calcula un score por DN vivo:

```
score = α · (capacidad_libre / capacidad_total) − β · asignaciones_recientes
```

con `α=1.0` y `β=0.1` por defecto. Selecciona los `replication-factor`
DataNodes con mayor score y, entre bloques consecutivos, rota cuál es el
primario para evitar hot-spots.

## 8. Persistencia de metadatos (NameNode)

H2 embebido (archivo `${DATA_DIR}/namenode-db.mv.db`).  Esquema:

| Tabla | Columnas |
|-------|---------|
| `users` | `id`, `username`, `password_hash`, `created_at` |
| `directories` | `id`, `owner_id`, `path`, `created_at` |
| `files` | `id`, `owner_id`, `path`, `size_bytes`, `block_size`, `status`, `created_at` |
| `blocks` | `id` (UUID), `file_id`, `sequence_index`, `size_bytes`, `hash_sha256` |
| `block_replicas` | `block_id`, `datanode_id`, `status` (PENDING/LIVE/STALE/DELETED), `updated_at` |
| `datanodes` | `id`, `host`, `port`, `capacity_bytes`, `used_bytes`, `last_heartbeat`, `status` |

## 9. Comunicaciones

Todas las APIs son **REST sobre HTTP** con JSON para metadatos y transferencia
binaria directa para bloques (`PUT/GET /blocks/{id}`).  El cuerpo de los PUT
de bloque es `application/octet-stream`; los headers `X-DFS-Hash` y
`X-DFS-Pipeline` controlan integridad y replicación pipeline respectivamente.

JWT HS256 con clave compartida (`JWT_SECRET`); validado localmente por cada
componente. Esto evita un cuello de botella en el NameNode al validar tokens
en cada request a DataNode.
