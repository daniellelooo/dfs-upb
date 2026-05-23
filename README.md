# DFS-UPB — Sistema de Archivos Distribuido por Bloques

Implementación minimalista de un DFS por bloques estilo HDFS/GFS para el curso
**Arquitecturas de Nube y Sistemas Distribuidos** (UPB, 2026).

* Arquitectura: **Maestro–Trabajador** (NameNode + DataNodes + Cliente CLI).
* Tecnologías: Java 17, Spring Boot 3, REST/HTTP, JWT, H2, Docker Compose.
* Replicación mínima: factor 2 (configurable). Tamaño de bloque por defecto: 64 MB.
* Semántica: **WORM** (Write-Once-Read-Many). Cada cliente sólo gestiona sus propios archivos.

---

## 1. Arquitectura

```
                     +----------------+
                     |  Cliente CLI   |  (login, put, get, ls, rm,
                     |  (dfs ...)     |   mkdir, rmdir, stat)
                     +-------+--------+
                             |
                JWT + REST   |   bloques (PUT/GET binario)
                             v
                     +-------+--------+
                     |    NameNode    |  metadatos, namespace,
                     |  (Spring Boot) |  asignación, re-replicación,
                     |    H2 embed    |  detección de fallos
                     +---+--------+---+
                         |        |
                heartbeats|        |órdenes de replicación
                         |        v
        +----------------+----+   +---------------------+
        |  DataNode 1         |<->|  DataNode 2 ... N   |  almacenamiento
        |  (Spring Boot)      |   |                     |  + pipeline replicación
        |  /var/dfs/blocks/*  |   |                     |
        +---------------------+   +---------------------+
```

Ver [docs/arquitectura.md](docs/arquitectura.md) para el detalle de componentes,
diagramas de secuencia (PUT/GET) y manejo de fallos.

---

## 2. Requisitos

* Docker ≥ 24.x con Docker Compose v2.
* (Opcional) Java 17 + Maven 3.9 si quieres construir/ejecutar sin Docker.

No necesitas tener Java en el host: la build se realiza dentro del contenedor
mediante un Dockerfile multi-stage.

---

## 3. Levantar el clúster

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

Verifica:

```bash
docker compose ps
curl http://localhost:8080/health
curl http://localhost:8081/health
```

Swagger UI del NameNode: <http://localhost:8080/swagger-ui.html>

---

## 4. Uso del Cliente CLI

El cliente está disponible como un servicio bajo el perfil `tools` de Compose,
que ejecuta el JAR shadeado dentro de la red interna del clúster:

```bash
# Wrapper conveniente
alias dfs='docker compose run --rm -v "$PWD/client-data:/data" client'

# Registrar usuario y autenticarse
dfs register -u daniel -p secret123
dfs login    -u daniel -p secret123

# Operaciones
dfs mkdir /docs
dfs put /data/grande.bin /docs/grande.bin --block-size-mb 16
dfs ls /docs
dfs stat /docs/grande.bin
dfs get /docs/grande.bin /data/grande_recuperado.bin
dfs rm /docs/grande.bin
dfs rmdir /docs
```

Si prefieres ejecutar el cliente desde tu host (sin Docker), construye con
Maven (`mvn -pl client -am package`) y usa el JAR shadeado en `client/target/dfs-client.jar`:

```bash
java -jar client/target/dfs-client.jar --namenode http://localhost:8080 login -u daniel
```

Variables de entorno reconocidas:

| Variable | Descripción |
|----------|-------------|
| `DFS_NAMENODE_URL` | Base URL del NameNode (default `http://localhost:8080`). |
| `DFS_HOME` | Directorio donde se guarda `session.json` (default `~/.dfs`). |

---

## 5. Demostración rápida

```bash
docker compose up -d
docker compose run --rm client register -u daniel -p secret123
docker compose run --rm client login    -u daniel -p secret123
dd if=/dev/urandom of=client-data/big.bin bs=1M count=120
docker compose run --rm client put /data/big.bin /big.bin --block-size-mb 32
docker compose run --rm client stat /big.bin

# Tumba un DataNode y comprueba que el GET sigue funcionando
docker compose stop datanode-2
docker compose run --rm client get /big.bin /data/big_recovered.bin

# El NameNode iniciará re-replicación automáticamente cuando haya un DN libre.
docker compose start datanode-2
sleep 30
docker compose run --rm client stat /big.bin   # bloques vuelven a tener factor 2
```

---

## 6. Pruebas y verificación

Ver [docs/pruebas.md](docs/pruebas.md) para la matriz de pruebas (RF/RNF), y
[docs/autoevaluacion.md](docs/autoevaluacion.md) para la plantilla de auto-evaluación
contra los criterios de la rúbrica.

---

## 7. Estructura del repositorio

```
.
├── pom.xml                    # Parent Maven (gestiona versiones)
├── common/                    # DTOs, JwtUtil, HashUtil
├── namenode/                  # Servicio Spring Boot del NameNode
├── datanode/                  # Servicio Spring Boot del DataNode
├── client/                    # CLI Java (Picocli, JAR shadeado)
├── Dockerfile                 # Multi-stage para namenode|datanode (ARG MODULE)
├── Dockerfile.client          # Imagen cliente
├── docker-compose.yml         # Orquestación completa: 1 NN + 3 DN + cliente
├── .env.example
└── docs/
    ├── arquitectura.md
    ├── api.md
    ├── pruebas.md
    └── autoevaluacion.md
```

---

## 8. Configuración

Todas las variables se inyectan vía entorno (ver [`.env.example`](.env.example) y
[`docker-compose.yml`](docker-compose.yml)).  Los parámetros del DFS se leen
desde `application.yml` y pueden sobreescribirse en runtime:

| Variable | Componente | Default | Descripción |
|----------|------------|---------|-------------|
| `JWT_SECRET` | NameNode + DataNode | … | Clave HMAC compartida (≥32 bytes). |
| `BLOCK_SIZE_MB` | NameNode | 64 | Tamaño por defecto de bloque. |
| `REPLICATION_FACTOR` | NameNode | 2 | Réplicas mínimas por bloque. |
| `HEARTBEAT_INTERVAL_SECONDS` | DataNode | 3 | Frecuencia heartbeat. |
| `BLOCKREPORT_INTERVAL_SECONDS` | DataNode | 60 | Frecuencia block report. |
| `HB_SUSPECT_SECONDS` | NameNode | 10 | Sin heartbeat → estado SUSPECT. |
| `HB_DEAD_SECONDS` | NameNode | 30 | Sin heartbeat → estado DEAD + re-replicación. |
| `DATANODE_CAPACITY_MB` | DataNode | 10240 | Capacidad reportada al maestro. |

---

## 9. Endpoints principales

NameNode (`http://namenode:8080`):

| Método | Ruta | Auth | Descripción |
|-------:|------|:----:|-------------|
| POST | `/auth/register` | No | Registrar usuario |
| POST | `/auth/login` | No | Login → JWT |
| POST | `/files` | Sí | Crear archivo + plan de bloques |
| POST | `/files/commit?path=` | Sí | Confirmar subida |
| GET  | `/files/meta?path=` | Sí | Obtener metadatos + ubicaciones |
| DELETE | `/files?path=` | Sí | Borrar archivo |
| GET  | `/dirs?path=` | Sí | Listar |
| POST | `/dirs` | Sí | mkdir |
| DELETE | `/dirs?path=` | Sí | rmdir |
| POST | `/datanodes/heartbeat` | Sí | Heartbeat (DN) |
| POST | `/datanodes/blockreport` | Sí | Block report (DN) |

DataNode (`http://datanode-X:808X`):

| Método | Ruta | Auth | Descripción |
|-------:|------|:----:|-------------|
| PUT  | `/blocks/{id}` | Sí | Recibe bloque + pipeline a peer |
| GET  | `/blocks/{id}` | Sí | Entrega bloque (con header `X-DFS-Hash`) |
| DELETE | `/blocks/{id}` | Sí | Borra bloque |
| POST | `/blocks/{id}/replicate` | Sí | Orden de replicación dirigida por NameNode |
| GET  | `/health` | No | Health-check |

Ver [docs/api.md](docs/api.md) para los esquemas request/response detallados.

---

## 10. Licencia / Créditos

Proyecto académico, UPB Facultad de Ingeniería de Sistemas e Informática.
Inspirado en GFS (Ghemawat et al., 2003) y HDFS (Shvachko et al., 2010).
