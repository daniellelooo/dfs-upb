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

## 3. Diagrama de componentes

```mermaid
flowchart TB
    subgraph Cliente
        CLI["CLI Java<br/>(Picocli JAR)"]
    end

    subgraph Master["Plano de control"]
        NN["NameNode<br/>Spring Boot 3<br/>JPA + H2 + JWT<br/>@Scheduled re-replicación"]
    end

    subgraph Workers["Plano de datos"]
        DN1["DataNode 1<br/>/var/dfs/blocks"]
        DN2["DataNode 2<br/>/var/dfs/blocks"]
        DN3["DataNode 3<br/>/var/dfs/blocks"]
    end

    CLI -- "JWT + REST<br/>metadatos" --> NN
    CLI -- "PUT/GET binario<br/>(octet-stream)" --> DN1
    CLI -- "PUT/GET binario" --> DN2
    CLI -- "PUT/GET binario" --> DN3

    DN1 -- "heartbeat 3s<br/>block report 60s" --> NN
    DN2 -- "heartbeat 3s" --> NN
    DN3 -- "heartbeat 3s" --> NN

    NN -. "órdenes de<br/>replicación" .-> DN1
    NN -. "órdenes de<br/>replicación" .-> DN2
    NN -. "órdenes de<br/>replicación" .-> DN3

    DN1 -- "pipeline replication<br/>PUT /blocks/{id}" --> DN2
    DN2 -- "pipeline replication" --> DN3

    classDef client fill:#dbeafe,stroke:#1e40af,color:#1e3a8a
    classDef master fill:#fef3c7,stroke:#92400e,color:#78350f
    classDef worker fill:#dcfce7,stroke:#166534,color:#14532d
    class CLI client
    class NN master
    class DN1,DN2,DN3 worker
```

## 4. Diagrama de secuencia: PUT

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente CLI
    participant NN as NameNode
    participant DA as DataNode A (primario)
    participant DB as DataNode B (secundario)

    C->>NN: POST /auth/login {user, pass}
    NN-->>C: 200 OK { token JWT }

    C->>NN: POST /files { path, sizeBytes, blockSize, numBlocks }
    Note over NN: chooseTargets() asigna<br/>réplicas por bloque
    NN-->>C: 200 OK plan: blocks[] con DataNodes

    rect rgb(245, 245, 245)
    Note over C,DB: Subida de un bloque con replicación pipeline
    C->>DA: PUT /blocks/B1<br/>X-DFS-Hash + X-DFS-Pipeline=B
    Note over DA: Verifica SHA-256<br/>Escribe a disco local
    DA->>DB: PUT /blocks/B1<br/>X-DFS-Hash
    Note over DB: Verifica SHA-256<br/>Escribe a disco local
    DB-->>DA: 200 OK
    DA-->>C: 200 OK { hash, size }
    end

    C->>NN: POST /files/commit?path=...
    Note over NN: status = COMPLETE<br/>replicas → LIVE
    NN-->>C: 200 OK
```

## 5. Diagrama de secuencia: GET

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente CLI
    participant NN as NameNode
    participant DA as DataNode A
    participant DB as DataNode B

    C->>NN: GET /files/meta?path=...
    NN-->>C: 200 OK { blocks[], replicas[] }

    loop Por cada bloque en orden 0..N-1
        Note over C: shuffle(replicas)<br/>elige una réplica al azar
        C->>DA: GET /blocks/Bk
        alt DataNode responde 200
            DA-->>C: 200 + bytes + X-DFS-Hash
            Note over C: Verifica SHA-256<br/>contra metadatos
            alt Hash coincide
                Note over C: Bloque aceptado
            else Hash no coincide o error
                C->>DB: GET /blocks/Bk (failover)
                DB-->>C: 200 + bytes + X-DFS-Hash
                Note over C: Verifica SHA-256
            end
        else DataNode caído o 5xx
            C->>DB: GET /blocks/Bk (failover)
            DB-->>C: 200 + bytes + X-DFS-Hash
        end
    end

    Note over C: Concatena bloques 0..N-1<br/>→ archivo local reconstruido
```

## 6. Detección de fallos y re-replicación

```mermaid
stateDiagram-v2
    [*] --> LIVE: primer heartbeat
    LIVE --> SUSPECT: sin heartbeat > 10 s
    SUSPECT --> LIVE: heartbeat recibido
    SUSPECT --> DEAD: sin heartbeat > 30 s
    DEAD --> LIVE: vuelve a enviar heartbeat
    DEAD --> [*]: removido del clúster

    note right of DEAD
        Al pasar a DEAD:
        - Réplicas LIVE/PENDING → DELETED
        - Job reReplicate() crea
          nuevas réplicas en DNs sanos
    end note
```

* Cada DataNode envía heartbeat cada `heartbeat-interval-seconds` (default 3 s).
* El NameNode marca `SUSPECT` tras `HB_SUSPECT_SECONDS` (10 s) y `DEAD` tras
  `HB_DEAD_SECONDS` (30 s). Cuando un DN pasa a `DEAD`, todas sus réplicas
  `LIVE` se marcan `DELETED`, lo que baja el factor efectivo de los bloques
  afectados.
* Un job programado en el NameNode (`reReplicate`, cada
  `rereplication-interval-seconds`) busca bloques `LIVE_count < replication`,
  selecciona un DN saludable distinto, y le envía una orden
  `POST /blocks/{id}/replicate` a un DN que aún tenga el bloque, el cual lo
  copia al destino con `PUT /blocks/{id}` (incluyendo el hash).
* Cuando el destino confirma, el NameNode actualiza la réplica como `LIVE`.

## 7. Ciclo de vida de una réplica

```mermaid
stateDiagram-v2
    [*] --> PENDING: NameNode asigna<br/>DataNode al bloque
    PENDING --> LIVE: DataNode confirma<br/>recepción (commit)
    LIVE --> STALE: heartbeat reporta<br/>bloque no presente
    LIVE --> DELETED: DataNode marcado DEAD<br/>o rm /files
    STALE --> LIVE: block report posterior<br/>vuelve a reportarlo
    STALE --> DELETED: queda obsoleto
    DELETED --> [*]: GC vía heartbeat<br/>(blocksToDelete)
```

## 8. Manejo de bloques corruptos

* El cliente verifica SHA-256 en cada bloque descargado. Ante mismatch,
  reintenta automáticamente con la siguiente réplica (failover transparente).
* Los DataNodes verifican SHA-256 al recibir un bloque; descartan ante
  mismatch con HTTP 400.
* En caso de corrupción detectada por el cliente, podría notificarse al
  NameNode para forzar re-replicación. Para mantener el alcance, esta
  notificación está cableada como endpoint placeholder; el job de
  re-replicación recoge las réplicas que ya estén marcadas `STALE/DELETED`.

## 9. Algoritmo de asignación de DataNodes

Para cada bloque, el `DataNodeService` calcula un score por DN vivo:

```
score = α · (capacidad_libre / capacidad_total) − β · asignaciones_recientes
```

con `α=1.0` y `β=0.1` por defecto. Selecciona los `replication-factor`
DataNodes con mayor score y, entre bloques consecutivos, rota cuál es el
primario para evitar hot-spots.

## 10. Persistencia de metadatos (NameNode)

H2 embebido (archivo `${DATA_DIR}/namenode-db.mv.db`). Modelo entidad-relación:

```mermaid
erDiagram
    USERS ||--o{ DIRECTORIES : owns
    USERS ||--o{ FILES : owns
    FILES ||--|{ BLOCKS : contains
    BLOCKS ||--|{ BLOCK_REPLICAS : has
    DATANODES ||--o{ BLOCK_REPLICAS : stores

    USERS {
        bigint id PK
        string username UK
        string password_hash
        timestamp created_at
    }
    DIRECTORIES {
        bigint id PK
        bigint owner_id FK
        string path
        timestamp created_at
    }
    FILES {
        bigint id PK
        bigint owner_id FK
        string path
        long size_bytes
        long block_size
        string status
        timestamp created_at
    }
    BLOCKS {
        uuid id PK
        bigint file_id FK
        int sequence_index
        long size_bytes
        string hash_sha256
    }
    BLOCK_REPLICAS {
        bigint id PK
        uuid block_id FK
        string datanode_id FK
        string status
        timestamp updated_at
    }
    DATANODES {
        string id PK
        string host
        int port
        long capacity_bytes
        long used_bytes
        timestamp last_heartbeat
        string status
    }
```

## 11. Comunicaciones

Todas las APIs son **REST sobre HTTP** con JSON para metadatos y transferencia
binaria directa para bloques (`PUT/GET /blocks/{id}`). El cuerpo de los PUT
de bloque es `application/octet-stream`; los headers `X-DFS-Hash` y
`X-DFS-Pipeline` controlan integridad y replicación pipeline respectivamente.

JWT **HS384** con clave compartida (`JWT_SECRET`); validado localmente por
cada componente. Esto evita un cuello de botella en el NameNode al validar
tokens en cada request a DataNode.
