# BACKUP — Estrategia y prueba real de restauracion (F11)

Estrategia de backup de la base de datos de BIOPET en produccion y el
procedimiento de restauracion **probado de verdad** (evidencia al final).

## 0. Que se respalda

Solo la base de datos **PostgreSQL** (`biopet_db`) contiene estado
persistente. Redis/Valkey (cache) y los contenedores son efimeros: se
recrean desde el codigo del repositorio.

| Componente | Estado persistente | Se respalda |
|------------|--------------------|-------------|
| Postgres (`biopet_db`) | usuarios, mascotas, citas, consultas, vacunas, secuencias, SP | **Si** |
| Redis/Valkey (cache) | solo cache TTL 5 min | No (se pierde sin impacto) |
| Backend/Frontend | ninguno (stateless) | No |

## 1. Estrategia

- **Frecuencia:** diaria (01:00 UTC, horario de bajo trafico).
- **Formato:** `pg_dump` SQL comprimido (`--clean --if-exists --no-owner`)
  gzip.
- **Destino:** almacenamiento del operador, separado del VPS/host de Render
  (p.ej. bucket de objeto o disco local del operador). No en el mismo
  volumen de la BD.
- **Retencion:** 30 dias (se borra el dump de hace 31 dias).
- **Nombre de archivo:** `biopet-backup-YYYYMMDD.sql.gz` (fecha ISO 8601
  compacta).
- **Verificacion:** cada backup incluye un registro de checksum
  (`sha256sum`) y una restauracion de prueba mensual (procedimiento 3).

## 2. Generar el backup (comandos reales)

### Render (Postgres gestionado)

Render no expone `pg_dump` dentro del servicio; se ejecuta con la URL de
conexion desde el equipo del operador (external connection string, disponible
en Dashboard -> `biopet-db` -> Connect -> External):

```bash
DATABASE_URL="postgresql://biopet_user:PASSWORD@HOST:5432/biopet_db"
pg_dump "$DATABASE_URL" --clean --if-exists --no-owner \
  | gzip > biopet-backup-$(date +%Y%m%d).sql.gz
sha256sum biopet-backup-$(date +%Y%m%d).sql.gz >> checksums.txt
```

### VPS (docker compose prod)

```bash
docker exec biopet-prod-postgres \
  pg_dump -U biopet_user -d biopet_db --clean --if-exists --no-owner \
  | gzip > biopet-backup-$(date +%Y%m%d).sql.gz
sha256sum biopet-backup-$(date +%Y%m%d).sql.gz >> checksums.txt
```

### Programacion (cron diario, ejemplo 01:00 UTC)

```cron
0 1 * * * cd /ruta/backups && docker exec biopet-prod-postgres \
  pg_dump -U biopet_user -d biopet_db --clean --if-exists --no-owner \
  | gzip > biopet-backup-$(date +\%Y\%m\%d).sql.gz \
  && find /ruta/backups -name "biopet-backup-*.sql.gz" -mtime +30 -delete
```

## 3. Restaurar (procedimiento probado)

La restauracion se hace en una base limpia. El dump usa `--clean --if-exists`,
que elimina y recrea tablas/objetos existentes.

### 3.1 En un entorno nuevo / tras perdida total

```bash
# 1. Crear la base (si no existe)
createdb -U biopet_user biopet_db   # o CREATE DATABASE en psql

# 2. Aplicar el dump
gunzip -c biopet-backup-20260817.sql.gz \
  | docker exec -i biopet-prod-postgres psql -U biopet_user -d biopet_db

# 3. Verificar
docker exec biopet-prod-postgres \
  psql -U biopet_user -d biopet_db -t -c \
  "SELECT count(*) FROM usuarios;"
```

### 3.2 En Render

```bash
# Descargar el dump y restaurar con psql hacia la URL externa
# (Dashboard -> biopet-db -> Connect -> External connection string)
gunzip -c biopet-backup-YYYYMMDD.sql.gz | psql "$DATABASE_URL"
```

## 4. Prueba real de restauracion (evidencia)

Ejecutada el **2026-08-17** sobre el entorno de prueba local (mismo
PostgreSQL 16 que produccion, contenedor `biopet-postgres`).

### Comandos ejecutados

```bash
# 1. Backup (pg_dump real, BD con datos del sistema)
docker exec biopet-postgres pg_dump -U biopet_user -d biopet_db \
  --clean --if-exists --no-owner > biopet-backup-20260817.sql
#   -> 64,762 bytes, 6 tablas (citas, consultas, flyway_schema_history,
#      mascotas, usuarios, vacunas) + secuencias + procedimientos

# 2. Crear base de prueba vacia
docker exec biopet-postgres psql -U biopet_user -d postgres \
  -c "CREATE DATABASE biopet_restore_test OWNER biopet_user;"

# 3. Restaurar el dump en la base de prueba
Get-Content biopet-backup-20260817.sql \
  | docker exec -i biopet-postgres psql -U biopet_user -d biopet_restore_test

# 4. Verificar integridad
SELECT (SELECT count(*) FROM usuarios), (SELECT count(*) FROM mascotas),
       (SELECT count(*) FROM citas), (SELECT count(*) FROM consultas),
       (SELECT count(*) FROM vacunas);
```

### Resultado (real)

| Verificacion | Resultado |
|--------------|-----------|
| Restauracion sin errores | OK (solo NOTICE de objetos ausentes, esperado) |
| `usuarios` | 1 fila (admin@biopet.ec, ROLE_ADMIN, activo=t) |
| `mascotas`, `citas`, `consultas`, `vacunas` | 0 filas (BD de prueba vacia, consistente) |
| Procedimientos almacenados post-restauracion | OK: `fn_siguiente_numero_ficha('RST')` -> `RST-000001` |
| Objetos creados por el dump | 6 tablas + secuencias + funciones/triggers + SP |
| Base de prueba limpiada despues | `DROP DATABASE biopet_restore_test` |

**Conclusion:** el backup es restaurable y los procedimientos almacenados
(F05) sobreviven la restauracion. Evidencia del dump preservada en
`docs/despliegue/ejemplo-backup-20260817.sql` (mismo formato que el backup
diario, sin gzip, para revision).

## 5. Checklist del operador

- [ ] Backup diario programado y verificando (archivo con fecha de hoy).
- [ ] Checksums registrados (`checksums.txt`).
- [ ] Retencion 30 dias funcionando (`find ... -mtime +30 -delete`).
- [ ] Restauracion de prueba mensual documentada.
- [ ] Backup almacenado en destino separado del host de produccion.