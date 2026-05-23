# Plan de pruebas — DFS-UPB

| Caso | Tipo | Procedimiento | Esperado |
|------|------|---------------|----------|
| Auth-1 | RF-06 | `register` + `login` | JWT retornado con expiración futura. |
| Auth-2 | RF-07 | Usuario A intenta `stat` archivo de B | `404 Not Found`. |
| Auth-3 | Seguridad | Llamada sin `Authorization` a `/files` | `401 Unauthorized`. |
| NS-1 | RF-05 | `mkdir /docs/2026` | Aparece en `ls /docs`. |
| NS-2 | RF-05 | `rmdir` directorio no vacío | `409 Conflict`. |
| Put-1 | RF-01,RF-03,RF-04 | `put` archivo 100 MB con block-size 16 MB | 7 bloques, factor=2, distribuidos en al menos 2 DN distintos. |
| Put-2 | RNF-03 | Forzar mismatch de hash en cliente y enviar | DN responde `400`, archivo no se commitea. |
| Put-3 | RF-04 | `stat` después de `put` | Cada bloque lista 2 réplicas LIVE en DN distintos. |
| Get-1 | RF-02 | `get` archivo subido en Put-1 | Bytes idénticos al original (compara `sha256sum`). |
| Get-2 | RNF-01 | Detener `datanode-2`, ejecutar `get` | Cliente hace failover a otra réplica → archivo recuperado OK. |
| Repl-1 | RNF-02 | Detener `datanode-2`, esperar > `HB_DEAD_SECONDS+rereplication-interval-seconds` | NameNode dispara re-replicación; `stat` muestra factor=2 en otros nodos. |
| Repl-2 | RNF-01 | Tras Repl-1, levantar `datanode-2`, esperar block-report | Bloques antiguos no LIVE; nuevas asignaciones priorizan a DN-2 si tiene más capacidad. |
| Rm-1 | RF-05 | `rm /docs/big.bin` y `ls /docs` | Archivo desaparece. |
| Rm-2 | GC | Verificar que en cada DataNode el archivo de bloque correspondiente desaparece tras siguiente heartbeat | Bloque borrado del disco. |
| Logs | RNF-05 | Revisar `docker compose logs namenode datanode-1` durante un PUT | Trazas con `blockId`, `dataNodeId`, latencia razonable. |
| Docs | RNF-06 | Abrir `/swagger-ui.html` | UI lista todos los endpoints. |

## Comandos de verificación de integridad

```bash
sha256sum client-data/big.bin
docker compose run --rm client get /big.bin /data/big_recovered.bin
sha256sum client-data/big_recovered.bin
diff -q client-data/big.bin client-data/big_recovered.bin
```

## Inspección directa de DataNodes

```bash
docker compose exec datanode-1 ls -la /var/dfs/blocks
docker compose exec datanode-2 ls -la /var/dfs/blocks
docker compose exec datanode-3 ls -la /var/dfs/blocks
```
La intersección de los 3 listados debe sumar (factor × N_bloques) entradas
únicas, distribuidas entre los DN.
