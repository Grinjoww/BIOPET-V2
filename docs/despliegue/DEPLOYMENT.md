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
- Red interna: biopet-backend usa la red privada de Render para conectarse
  a Postgres y Key Value (biopet-db, biopet-cache), que si aceptan trafico
  entrante privado en cualquier plan.
- **Frontend -> backend: HTTPS publico, NO red privada.** Verificado en un
  deploy real: con **Web Services en plan Free**, un servicio Free puede
  *enviar* trafico por la red privada de Render pero NO puede *recibirlo*
  -- biopet-frontend (Free) intentando llamar a biopet-backend (Free) por
  su hostname privado (`biopet-backend-dh5e`) fallo con
  `host not found in upstream`. Por eso el frontend usa la **URL HTTPS
  publica real** del backend (`BACKEND_URL` en `render.yaml`), no el
  hostname interno. Si en el futuro ambos servicios se mueven a un plan de
  pago, la red privada si seria viable entre ellos.

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

### 5.1 Frontend en Render: proxy `/api` hacia el backend

**No requiere ningun paso manual.** El proxy `/api` del frontend se
resuelve automaticamente en tiempo de arranque del contenedor
(`frontend/docker-entrypoint.sh` + `frontend/nginx.conf.template`), usando
la variable `BACKEND_URL` que `render.yaml` ya define para el servicio
`biopet-frontend` -- no hay que copiar ningun archivo ni commitear un
cambio de configuracion antes del deploy.

`BACKEND_URL` es la **URL HTTPS publica** del backend, no su hostname
privado (ver seccion 0, "Frontend -> backend: HTTPS publico, NO red
privada" -- Web Services Free de Render no pueden recibir trafico por la
red privada). Para esta instancia, la URL publica real y verificada del
backend ya desplegado es:

```
https://biopet-backend-dh5e.onrender.com
```

En Docker Compose local, el mismo mecanismo usa el default
`BACKEND_URL=http://backend:8080` (el nombre del servicio `backend` de
`docker-compose.yml`), sin necesidad de configuracion adicional.

`docs/despliegue/nginx-render.conf` queda como documentacion historica de
un enfoque anterior (copia manual de archivo); ya no se usa.

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
# Healthcheck del backend (sustituir por la URL real de tu servicio;
# para esta instancia: https://biopet-backend-dh5e.onrender.com)
curl -I https://<tu-servicio-backend>.onrender.com/actuator/health
# esperado: HTTP/1.1 200  y  body {"status":"UP"}

# Login real de humo
curl -X POST https://<tu-servicio-backend>.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@biopet.ec","password":"<password real>"}'
# esperado: 200 + cookie access_token
```

> Nota: `https://biopet-backend-dh5e.onrender.com` es la URL publica real
> de esta instancia confirmada por el equipo al desplegar `biopet-backend`
> (estado `DEPLOYED` en el dashboard de Render); esta tarea no ejecuto los
> `curl` de arriba contra esa URL (sin acceso a Internet saliente desde
> este entorno) -- la validacion aqui se hizo simulando localmente el
> mismo `BACKEND_URL` contra un contenedor `frontend` real (ver seccion
> de validacion del fix del proxy).

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