# API — DFS-UPB

Todas las llamadas (excepto `/auth/*`, `/health` y Swagger) requieren el header
`Authorization: Bearer <jwt>`.

## NameNode

### `POST /auth/register`
```json
{ "username": "daniel", "password": "secret123" }
```

### `POST /auth/login`
Request:
```json
{ "username": "daniel", "password": "secret123" }
```
Response:
```json
{ "token": "<jwt>", "expiresAt": 1715999999999, "username": "daniel" }
```

### `POST /files`
Request:
```json
{ "path": "/docs/big.bin", "sizeBytes": 134217728, "blockSize": 67108864, "numBlocks": 2 }
```
Response (plan):
```json
{
  "fileId": "42",
  "blockSize": 67108864,
  "blocks": [
    {
      "blockId": "7c6b...-uuid",
      "sequenceIndex": 0,
      "sizeBytes": 67108864,
      "replicas": [
        { "id": "datanode-1", "host": "datanode-1", "port": 8081 },
        { "id": "datanode-2", "host": "datanode-2", "port": 8082 }
      ]
    }
  ]
}
```

### `POST /files/commit?path=/docs/big.bin`
```json
{
  "blocks": [
    { "blockId": "7c6b...", "hashSha256": "ab12...", "sizeBytes": 67108864,
      "liveReplicas": ["datanode-1", "datanode-2"] }
  ]
}
```

### `GET /files/meta?path=/docs/big.bin`
Response:
```json
{
  "fileId": "42",
  "path": "/docs/big.bin",
  "sizeBytes": 134217728,
  "blockSize": 67108864,
  "status": "COMPLETE",
  "owner": "daniel",
  "blocks": [ /* BlockLocation[] con réplicas LIVE */ ]
}
```

### `DELETE /files?path=/docs/big.bin`
Marca todas las réplicas como `DELETED` y las DataNodes las recogen vía
heartbeat (campo `blocksToDelete`).

### `GET /dirs?path=/`
```json
{ "path": "/", "entries": [
   {"name": "docs", "type": "DIR",  "owner": "daniel"},
   {"name": "README.md", "type": "FILE", "sizeBytes": 1024, "owner": "daniel"}
]}
```

### `POST /dirs`
```json
{ "path": "/docs/2026" }
```

### `DELETE /dirs?path=/docs/2026`

### `POST /datanodes/heartbeat`
Request:
```json
{ "dataNodeId": "datanode-1", "host": "datanode-1", "port": 8081,
  "capacityBytes": 10737418240, "usedBytes": 5242880, "blockCount": 12 }
```
Response:
```json
{ "ok": true, "blocksToDelete": ["7c6b...", "9d44..."] }
```

### `POST /datanodes/blockreport`
```json
{ "dataNodeId": "datanode-1",
  "blocks": [ { "blockId": "7c6b...", "sizeBytes": 67108864,
                 "hashSha256": "ab12..." } ] }
```

## DataNode

### `PUT /blocks/{blockId}`
* Body: bytes crudos del bloque (`application/octet-stream`).
* Headers:
  * `Authorization: Bearer <jwt>`
  * `X-DFS-Hash: <hex sha256>` — esperado del bloque.
  * `X-DFS-Pipeline: host1:port1,host2:port2` — peers a los que se debe
     reenviar el bloque secuencialmente (cadena de replicación).

Response:
```json
{ "status": "ok", "hashSha256": "ab12...", "sizeBytes": 67108864 }
```

### `GET /blocks/{blockId}`
Response binario; headers `X-DFS-Hash` y `X-DFS-DataNode`.

### `DELETE /blocks/{blockId}`
Borra el bloque del disco.

### `POST /blocks/{blockId}/replicate`
Request:
```json
{ "blockId": "7c6b...", "target": { "id": "datanode-3", "host": "datanode-3", "port": 8083 } }
```
El DataNode que recibe la orden lee el bloque local y lo envía con
`PUT /blocks/{id}` al `target`.

## Códigos de estado

* `200 OK` — éxito.
* `400 Bad Request` — parámetros inválidos / hash mismatch.
* `401 Unauthorized` — sin token / token inválido.
* `404 Not Found` — recurso inexistente.
* `409 Conflict` — colisión (archivo/dir existente, dir no vacío en rmdir, etc.).
* `5xx` — fallo interno o de red entre nodos.
