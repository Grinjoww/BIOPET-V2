# RUNBOOK — Operacion de BIOPET en produccion (F10)

Procedimientos operativos reales para el despliegue de BIOPET. Cada seccion
tiene pasos ejecutables de verdad (no genericos) sobre Render o el VPS.

## 0. Inventario de produccion

| Recurso            | Donde                          | Identificador                        |
|--------------------|--------------------------------|--------------------------------------|
| Backend (API)      | Render web service             | `biopet-backend`                     |
| Frontend (nginx)   | Render web service             | `biopet-frontend`                    |
| Cache (Valkey)     | Render Key Value               | `biopet-cache`                       |
| Base de datos      | Render Postgres                | `biopet-db` (`biopet_db`)            |
| Alternativa VPS    | docker compose prod            | contenedores `biopet-prod-*`         |

## 1. Arranque y parada

### Render

```bash
# Parar el backend (el frontend deja de servir /api)
# Dashboard -> biopet-backend -> Stop
# Reiniciar (redeploy con la misma imagen)
# Dashboard -> biopet-backend -> Restart
```

### VPS (docker compose prod)

```bash
cd <repo> && docker compose -f docker-compose.prod.yml up -d --build   # arrancar
docker compose -f docker-compose.prod.yml stop                         # parar
docker compose -f docker-compose.prod.yml restart backend              # solo backend
```

## 2. Verificar salud (healthcheck)

```bash
# Publico (debe responder 200 + {"status":"UP"})
curl -I https://biopet-backend.onrender.com/actuator/health

# Detallado (incluye redis y postgres)
curl -s https://biopet-backend.onrender.com/actuator/health | jq .
# esperado: {"status":"UP","components":{"db":{"status":"UP"},...}}

# En VPS, logs en caso de fallo:
docker compose -f docker-compose.prod.yml logs --tail=100 backend
```

## 3. Rotacion de JWT_SECRET

El backend firma tokens con `JWT_SECRET` (HMAC). Rotarlo invalida todos los
tokens existentes (los usuarios deben volver a iniciar sesion).

1. Generar el nuevo secreto:

```bash
openssl rand -base64 48
```

2. Render: Dashboard -> `biopet-backend` -> Environment -> editar
   `JWT_SECRET` -> Save Changes -> **Deploy** (reinicia el servicio).
3. VPS: editar `.env` (JWT_SECRET) y reiniciar:

```bash
docker compose -f docker-compose.prod.yml up -d --force-recreate backend
```

4. Verificar que el login sigue funcionando y que los tokens viejos dan 401.

## 4. Rotacion de credenciales de base de datos (Render)

Render gestiona las contrasenas de Postgres; se rotan desde el dashboard:

1. Dashboard -> `biopet-db` -> **Reset Database Password**.
2. Render actualiza automaticamente las variables `DB_PASSWORD` /
   `DB_APP_PASSWORD` enlazadas (fromDatabase) en el backend.
3. Confirmar en Dashboard -> `biopet-backend` -> Environment que
   `DB_PASSWORD` refleja la nueva contrasena.
4. Reiniciar el backend (Restart) y verificar `/actuator/health` en UP.

> En VPS: cambiar `DB_PASSWORD` en `.env` y en Postgres
> (`ALTER USER biopet_user WITH PASSWORD '...';`) y reiniciar el backend.

## 5. Actualizacion de contenedores (nueva version)

### Render (deploy desde git)

1. Mergear la rama nueva en la rama de despliegue (o `main`).
2. Render redeploya automaticamente (Git push) al servicio conectado.
3. Verificar: `curl -I .../actuator/health` -> 200.

### VPS

```bash
cd <repo>
git pull origin <rama-deploy>
docker compose -f docker-compose.prod.yml up -d --build backend frontend
docker compose -f docker-compose.prod.yml restart caddy
```

## 6. Restauracion ante fallo

### Backend caido (healthcheck en DOWN)

```bash
# 1. Ver logs
docker compose -f docker-compose.prod.yml logs --tail=200 backend   # VPS
#    o Render -> Logs -> Live

# 2. Reiniciar
docker compose -f docker-compose.prod.yml restart backend            # VPS
#    o Render -> biopet-backend -> Restart

# 3. Verificar
curl -s https://biopet-backend.onrender.com/actuator/health
```

### Perdida total de datos (restauracion desde backup)

Ver `docs/despliegue/BACKUP.md` (procedimiento probado, seccion "Restaurar").

Procedimiento resumido:

```bash
# Render: usar el dump mas reciente y la herramienta de restauracion del
# dashboard de Postgres, o psql remoto con la URL interna/externa.
gunzip -c backup-YYYYMMDD.sql.gz | psql "$DATABASE_URL"
# VPS:
docker exec -i biopet-prod-postgres \
  psql -U biopet_user -d biopet_db < backup-YYYYMMDD.sql
```

### Rollback de una version rota

- Render: Deploys -> seleccionar el deploy anterior -> **Rollback**.
- VPS: checkout del commit anterior y `up -d --build`.

## 7. Tareas programadas (recordatorio)

- Backup diario: ver BACKUP.md (cron del operador; Render no programa backups
  automaticos en planes basicos).
- Revision semanal: `curl -I .../actuator/health` + revisar logs de errores
  5xx.
- Rotacion de `JWT_SECRET`: cada 90 dias o ante sospecha de filtracion.