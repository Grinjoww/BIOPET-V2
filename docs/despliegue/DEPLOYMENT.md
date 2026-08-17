# DEPLOYMENT — BIOPET en Render (produccion)

Este documento permite a un tercero reproducir el despliegue completo de
BIOPET en **Render** (https://render.com) con HTTPS valido, healthcheck real
y sin secretos versionados. Es el punto de entrada de la rama
`fred/f08-f11-produccion-despliegue` (F08-F09).

## 0. Arquitectura de produccion

```
                    HTTPS (valido, automatico, *.onrender.com)
                                   |
                    +--------------+---------------+
                    |   Render (Blueprint render.yaml)   |
                    |                                  |
                    |  biopet-frontend (docker nginx)  |
                    |        |  /api -> proxy interno  |
                    |        v                         |
                    |  biopet-backend  (docker, JVM 21)|
                    |        |                         |
                    |   +----+----+                    |
                    |   | biopet-db  |  biopet-cache  |
                    |   | Postgres   |  Key Value     |
                    |   | (gestionado)| (Valkey/Redis)|
                    |   +------------+-----------------+
```

- HTTPS valido: Render emite y renueva certificados automaticamente para el
  subdominio `<servicio>.onrender.com` (sin configuracion TLS en la app).
- Healthcheck: `GET /actuator/health` -> `{"status":"UP"}` (HTTP 200).
- Red interna: los servicios se comunican por sus nombres internos de Render
  (`biopet-backend`, `biopet-frontend`) en la misma region.

## 1. Requisitos

- Cuenta en Render con metodo de pago (el plan `free` de Postgres se elimina
  a los 30 dias; para produccion permanente usar `starter` o superior).
- Repositorio: https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET
- Rama de despliegue: `fred/f08-f11-produccion-despliegue` (o `main` tras el merge).

## 2. Recursos que crea el Blueprint (render.yaml)

| Recurso            | Tipo        | Plan   | Nota                                          |
|--------------------|-------------|--------|-----------------------------------------------|
| `biopet-backend`   | Web docker  | free   | Spring Boot, puerto 8080 interno              |
| `biopet-frontend`  | Web docker  | free   | nginx, puerto 80 interno                      |
| `biopet-cache`     | Key Value   | free   | Valkey (Redis compatible), cache Spring       |
| `biopet-db`        | Postgres    | free*  | base `biopet_db`, usuario `biopet_user`       |

*\*Plan free de Postgres: los datos se eliminan a los 30 dias. Para datos
permanentes seleccionar plan `starter` (p.ej. `basic-256mb`) al crear el
Blueprint.*

## 3. Red y conectividad

- El backend se conecta a Postgres y Key Value por la red interna de Render
  (no expone esos puertos a Internet).
- `render.yaml` enlaza las variables automaticamente:
  - `fromDatabase` -> `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`,
    `DB_PASSWORD`, `DB_APP_USER`, `DB_APP_PASSWORD`.
  - `fromService` (keyvalue) -> `REDIS_HOST`, `REDIS_PORT`.
- El `dockerCommand` del backend construye la URL JDBC en runtime:
  `DB_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`.

## 4. Variables de entorno

### Enlazadas automaticamente por Render (no se configuran a mano)

| Variable           | Origen                          |
|--------------------|---------------------------------|
| `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` | Postgres gestionado |
| `DB_APP_USER`/`DB_APP_PASSWORD` | rol de la BD gestionada |
| `REDIS_HOST`/`REDIS_PORT`       | Key Value gestionado |

### Se completan en el dashboard de Render (sync: false en render.yaml)

| Variable                  | Descripcion                                  | Ejemplo de generacion |
|---------------------------|----------------------------------------------|-----------------------|
| `JWT_SECRET`              | Secreto HMAC de firma de tokens JWT          | `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS`    | Origen del frontend de produccion            | `https://biopet-frontend.onrender.com` |

> En Render, los valores de `sync: false` se piden una vez al crear el
> Blueprint (o se editan en Dashboard -> Environment). No se versionan.

### Fijas en render.yaml (no requieren accion)

`JWT_EXPIRATION_MS=3600000`, `JWT_REFRESH_EXPIRATION_MS=604800000`,
`JWT_ISSUER=biopet-api`, `JWT_AUDIENCE=biopet-frontend`,
`CACHE_TTL_MS=300000`.

### Rol de aplicacion (importante)

En desarrollo la app usa el rol `biopet_app` creado por `db/roles.sql`. En
Render no existe ese rol: el backend usa el rol de la BD gestionada
(`biopet_user`), que tiene privilegios completos sobre `biopet_db`. Esto
esta reflejado en `render.yaml` (`DB_APP_USER`/`DB_APP_PASSWORD` apuntan al
mismo rol gestionado).

## 5. Pasos exactos del despliegue

### 5.1 Preparar el frontend para Render

El nginx local (`frontend/nginx.conf`) apunta al servicio docker-compose
`backend`. En Render el servicio se llama `biopet-backend`:

```bash
cp docs/despliegue/nginx-render.conf frontend/nginx.conf
```

Commitear ese cambio (es parte de esta rama de despliegue).

### 5.2 Crear el Blueprint

1. En Render: **New -> Blueprint** -> conectar el repositorio
   `Grinjoww/ENTREGA-FINAL-BIOPET` (rama `fred/f08-f11-produccion-despliegue`).
2. Render detecta `render.yaml` en la raiz y muestra los 4 recursos.
3. Elegir plan de Postgres (recomendado: `starter`).
4. Al crear, Render pide los valores `sync: false`:
   - `JWT_SECRET`: pegarlo generado con `openssl rand -base64 48`.
   - `CORS_ALLOWED_ORIGINS`: `https://biopet-frontend.onrender.com`
     (ajustar al nombre real que Render asigne).
5. **Apply / Deploy**.

### 5.3 Verificar el despliegue

```bash
# Healthcheck real del backend (URL real de tu servicio)
curl -I https://biopet-backend.onrender.com/actuator/health
# esperado: HTTP/1.1 200  y  body {"status":"UP"}

# Login real de humo
curl -X POST https://biopet-backend.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@biopet.ec","password":"<password real>"}'
# esperado: 200 + cookie access_token
```

### 5.4 CORS del frontend

El navegador solo llama al backend via proxy `/api/` del frontend (mismo
origen), por lo que `CORS_ALLOWED_ORIGINS` del backend debe incluir el origen
del frontend si se llama directo. El valor real lo define el operador.

## 6. Alternativa: VPS propio (docker-compose.prod.yml)

Si no se usa Render (p.ej. DigitalOcean/Hetzner), el stack equivalente es:

1. Apuntar el dominio al VPS (registro A).
2. Crear `.env` desde `.env.example` con valores reales (DOMAIN, JWT_SECRET,
   credenciales de BD, APP_EXTERNAL_API_KEY).
3. Levantar:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

4. Verificar:

```bash
curl -I https://<tu-dominio>/actuator/health
```

El proxy `caddy` (docker-compose.prod.yml) emite y renueva HTTPS con Let's
Encrypt automaticamente (Caddyfile en `docs/despliegue/`).

## 7. Credenciales de acceso inicial

- La app crea el admin inicial (`admin@biopet.ec`) via `DataInitializer` al
  primer arranque (solo si no existe). La contrasena inicial la define el
  codigo de desarrollo; en produccion debe cambiarse inmediatamente
  (ver RUNBOOK.md).

## 8. Referencias

- Blueprint YAML: https://render.com/docs/blueprint-spec
- Blueprints (IaC): https://render.com/docs/infrastructure-as-code
- Key Value (Valkey/Redis): https://render.com/docs/key-value
- Healthchecks en Render: Dashboard del servicio -> Settings -> Health Check Path = `/actuator/health`