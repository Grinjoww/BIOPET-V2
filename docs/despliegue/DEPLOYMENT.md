# DEPLOYMENT — BIOPET V2 en Render (producción)

Este documento permite reproducir el despliegue real de **BIOPET V2** en
**Render** (https://render.com), con HTTPS válido, healthcheck real y sin
secretos versionados. El despliegue oficial de V2 vive en `main` del
repositorio actual — no hay una rama separada de despliegue.

> BIOPET V2 es una iteración distinta e independiente de BIOPET v1.0.0 (la
> entrega académica original), que sigue desplegada por separado en el mismo
> workspace de Render con recursos SIN el sufijo `-v2-` y no debe tocarse
> desde este documento.

## 0. Arquitectura de producción

```
                    HTTPS (valido, automatico, *.onrender.com)
                                   |
                    +--------------+---------------------+
                    |     Render (Blueprint render.yaml)      |
                    |                                          |
                    |  biopet-v2-frontend (docker, nginx)      |
                    |        |  /api/ -> proxy same-origin     |
                    |        v                                 |
                    |  biopet-v2-backend  (docker, JVM 21)      |
                    |        |                                 |
                    |   +----+------+                          |
                    |   | biopet-v2-db |  biopet-v2-cache      |
                    |   | Postgres     |  Key Value            |
                    |   | (gestionado) |  (Valkey/Redis)       |
                    |   +--------------+------------------------+
```

- **HTTPS válido**: Render emite y renueva certificados automáticamente
  para el subdominio `<servicio>.onrender.com` (sin configuración TLS en la
  app).
- **Healthcheck**: `GET /actuator/health` → `{"status":"UP"}` (HTTP 200),
  configurado como `healthCheckPath` del backend en `render.yaml`.
- **Red interna**: `biopet-v2-backend` usa la red privada de Render para
  conectarse a `biopet-v2-db` y `biopet-v2-cache`, que sí aceptan tráfico
  entrante privado en el plan `free`.
- **Frontend → backend: HTTPS público, NO red privada.** Verificado en un
  deploy real: con **Web Services en plan Free**, un servicio Free puede
  *enviar* tráfico por la red privada de Render pero NO puede *recibirlo* —
  un frontend Free llamando a un backend Free por su hostname privado falla
  con `host not found in upstream`. Por eso `biopet-v2-frontend` usa la
  **URL HTTPS pública** de `biopet-v2-backend` (`BACKEND_URL`), no un
  hostname interno. Si ambos servicios pasan a un plan de pago, la red
  privada sí sería viable entre ellos.
- El navegador solo habla con `biopet-v2-frontend`: nginx reenvía `/api/`
  al backend real, por lo que frontend y backend funcionan **same-origin**
  desde el navegador (ver [sección 6](#6-proxy-nginx-api)). La cookie de
  sesión sigue configurada `SameSite=Strict` (`Backend/src/main/resources/application.yml`,
  `security.cookie.same-site`) — same-origin es justamente lo que permite
  mantener `Strict` sin romper el login desde el frontend.

## 1. Requisitos

- Cuenta en Render con método de pago (el plan `free` de Postgres se
  elimina a los 30 días; para datos permanentes usar `starter` o superior).
- Repositorio: https://github.com/Grinjoww/BIOPET-V2
- Rama de despliegue: `main`.

## 2. Recursos que crea el Blueprint (`render.yaml`)

| Recurso | Tipo | Plan | Nota |
|---|---|---|---|
| `biopet-v2-backend` | Web docker | free | Spring Boot, puerto 8080 interno |
| `biopet-v2-frontend` | Web docker | free | nginx, puerto 80 interno |
| `biopet-v2-cache` | Key Value | free | Valkey (Redis compatible) |
| `biopet-v2-db` | Postgres | free* | base `biopet_db`, usuario `biopet_user` |

*\*Plan free de Postgres: los datos se eliminan a los 30 días. Para datos
permanentes seleccionar plan `starter` (p.ej. `basic-256mb`) al crear el
Blueprint.*

Los cuatro recursos se crean en una sola operación (**New → Blueprint**) —
no hace falta desplegar el backend primero, copiar su URL y pegarla en el
frontend a mano (ver sección 5).

## 3. Red y conectividad

- El backend se conecta a Postgres y Key Value por la red interna de
  Render (no expone esos puertos a Internet).
- `render.yaml` enlaza las variables automáticamente vía `fromDatabase` /
  `fromService` (ver tabla de la sección 4).
- El `dockerCommand` del backend (`Backend/render-entrypoint.sh`) construye
  la URL JDBC en runtime a partir de `DB_HOST`/`DB_PORT`/`DB_NAME`.

## 4. Variables de entorno

### Enlazadas automáticamente por Render (no se configuran a mano)

| Variable | Origen | Uso |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `fromDatabase: biopet-v2-db` | URL JDBC (construida por `render-entrypoint.sh`) |
| `DB_USER` / `DB_PASSWORD` | `fromDatabase: biopet-v2-db` | Credenciales de Flyway |
| `DB_APP_USER` / `DB_APP_PASSWORD` | `fromDatabase: biopet-v2-db` | Credenciales de la app (Hibernate). En Render no existe el rol de bajos privilegios `biopet_app` de desarrollo local: se reutiliza el rol gestionado (`biopet_user`), con privilegios completos sobre `biopet_db`. |
| `SPRING_DATA_REDIS_URL` | `fromService (keyvalue): biopet-v2-cache`, `property: connectionString` | Redis/Valkey. Render solo expone una `connectionString` completa (`redis://...` con credenciales) para servicios `keyvalue`, no `host`/`port` sueltos — por eso el backend usa `spring.data.redis.url` (vía `SPRING_DATA_REDIS_URL`) en vez de `REDIS_HOST`/`REDIS_PORT`. |
| `BACKEND_URL` (solo frontend) | `fromService (web): biopet-v2-backend`, `envVarKey: RENDER_EXTERNAL_URL` | URL pública HTTPS real del backend, resuelta automáticamente por Render — sin hardcodear, ver [sección 5](#5-pasos-exactos-del-despliegue). |

### Se completan en el dashboard de Render (`sync: false` en `render.yaml`)

| Variable | Descripción | Ejemplo de generación |
|---|---|---|
| `JWT_SECRET` | Secreto HMAC de firma de tokens JWT | `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | Origen(es) permitido(s) por CORS | `https://biopet-v2-frontend.onrender.com` (solo relevante si algo llama al backend fuera del proxy same-origin; ver sección 5.4) |
| `ADMIN_SEED_EMAIL` | Email del ADMIN de bootstrap | email real controlado por el operador |
| `ADMIN_SEED_PASSWORD` | Contraseña del ADMIN de bootstrap | secreto fuerte, p.ej. `openssl rand -base64 24` |

> En Render, los valores `sync: false` se piden una vez al crear el
> Blueprint (o se editan en Dashboard → Environment). No se versionan.

### Fijas en `render.yaml` (no requieren acción)

`JWT_EXPIRATION_MS=3600000`, `JWT_REFRESH_EXPIRATION_MS=604800000`,
`JWT_ISSUER=biopet-api`, `JWT_AUDIENCE=biopet-frontend`,
`CACHE_TTL_MS=300000`, `ADMIN_SEED_ENABLED=false` (editable en el dashboard,
ver [sección 7](#7-bootstrap-del-admin-inicial)), `ADMIN_SEED_NAME=Administrador BIOPET`.

### No definida en `render.yaml` (opcional)

`APP_EXTERNAL_API_KEY` — clave de la API externa (Animal API Ninjas) que
consume `GET /api/externa/especies`. No forma parte del Blueprint: por
defecto llega vacía (`app.external-api.key: ${APP_EXTERNAL_API_KEY:}` en
`application.yml`), lo que **no** impide que el backend arranque, pero sí
hace que esa llamada externa falle contra el proveedor (una API key vacía
es rechazada por Animal API Ninjas). Si se necesita esa integración en
producción, agregar `APP_EXTERNAL_API_KEY` manualmente en el dashboard de
Render (Environment del servicio `biopet-v2-backend`) con una clave real.

## 5. Pasos exactos del despliegue

### 5.1 Frontend en Render: proxy `/api` hacia el backend

**No requiere ningún paso manual.** `BACKEND_URL` se resuelve
automáticamente para el servicio `biopet-v2-frontend` vía
`fromService`/`RENDER_EXTERNAL_URL` apuntando a `biopet-v2-backend` (ver
tabla de la sección 4) — Render inyecta esa variable con la URL HTTPS
pública real del backend en cuanto el servicio existe, sin esperar a que
termine de compilar. El proxy en sí se genera en tiempo de arranque del
contenedor frontend (`frontend/docker-entrypoint.sh` +
`frontend/nginx.conf.template`), sin copiar ningún archivo ni commitear un
cambio antes del deploy.

En Docker Compose local, el mismo mecanismo usa el default
`BACKEND_URL=http://backend:8080` (el nombre del servicio `backend` de
`docker-compose.yml`), sin configuración adicional.

`docs/despliegue/nginx-render.conf` queda como documentación histórica de
dos enfoques anteriores abandonados (copia manual de archivo; hostname
privado de Render); ninguno de los dos se usa hoy.

### 5.2 Crear el Blueprint

1. En Render: **New → Blueprint** → conectar el repositorio
   `Grinjoww/BIOPET-V2` (rama `main`).
2. Render detecta `render.yaml` en la raíz y muestra los 4 recursos.
3. Elegir plan de Postgres (recomendado: `starter` para datos
   permanentes).
4. Al crear, Render pide los valores `sync: false` (ver sección 4):
   `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `ADMIN_SEED_EMAIL`,
   `ADMIN_SEED_PASSWORD`.
5. **Apply / Deploy.**

### 5.3 Verificar el despliegue

```bash
# Healthcheck del backend — la URL exacta la asigna Render al crear el
# servicio biopet-v2-backend; confirmarla en su dashboard (Settings -> URL).
curl -I https://<tu-servicio-biopet-v2-backend>.onrender.com/actuator/health
# esperado: HTTP/1.1 200  y  body {"status":"UP"}

# Frontend en producción (URL pública confirmada de esta instancia):
curl -I https://biopet-v2-frontend.onrender.com/

# Login real de humo, a través del proxy same-origin del frontend (recomendado
# sobre llamar al backend directo, porque valida también nginx/BACKEND_URL):
curl -X POST https://biopet-v2-frontend.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<email real del ADMIN>","password":"<password real>"}'
# esperado: 200 + cookie access_token
```

No se documenta aquí una URL fija de `biopet-v2-backend`: a diferencia del
frontend, su hostname público exacto no está versionado en ningún archivo
de este repositorio — se confirma en el dashboard de Render al desplegar.

### 5.4 CORS del frontend

El navegador solo llama al backend vía el proxy `/api/` del frontend
(mismo origen), por lo que en el camino normal de uso `CORS_ALLOWED_ORIGINS`
no entra en juego. Sigue siendo necesaria para cualquier cliente que llame
al backend directo (Swagger UI en producción, un cliente de pruebas, etc.);
el valor real lo define el operador.

## 6. Proxy nginx (`/api`)

El frontend expone `/api/` como reverse proxy hacia el backend
(`frontend/nginx.conf.template`, generado en runtime por
`frontend/docker-entrypoint.sh`). La decisión operativa relevante para
Render:

```nginx
proxy_set_header Host $proxy_host;
```

`$proxy_host` es el host:puerto real de destino de `proxy_pass` (derivado
de `BACKEND_URL`). Usar en su lugar `$host` (el Host que el navegador
envió al *frontend*) hace que el edge HTTP de Render, que enruta por esa
cabecera, vuelva a despachar la petición al propio frontend — un bucle que
Render corta devolviendo `508 Loop Detected` (`X-Render-Routing: loop`).
`proxy_ssl_server_name on;` se mantiene para el SNI del handshake TLS hacia
el backend; no se fija `proxy_ssl_name` explícitamente porque su default ya
es `$proxy_host`, consistente con el `Host` de arriba.

Este documento no repite el archivo completo — ver
`frontend/nginx.conf.template` para el bloque `/api/` íntegro y comentado.

## 7. Bootstrap del ADMIN inicial

`DataInitializer` (`CommandLineRunner "seedAdmin"`) no crea ninguna cuenta
por defecto ni trae contraseña hardcodeada. El bootstrap del primer ADMIN
es **opt-in**, vía 3 variables de entorno (ver también sección 4):

1. Definir `ADMIN_SEED_EMAIL` y `ADMIN_SEED_PASSWORD` en el dashboard de
   Render con datos reales (no la contraseña de desarrollo — ver nota
   abajo).
2. Poner `ADMIN_SEED_ENABLED=true`.
3. Desplegar/redeploy el backend (`biopet-v2-backend`) para que
   `DataInitializer` corra con esas variables.
4. Confirmar en los logs o vía login que la cuenta ADMIN quedó creada.
5. Volver a poner `ADMIN_SEED_ENABLED=false`.

Si `ADMIN_SEED_ENABLED=true` y falta `ADMIN_SEED_EMAIL` o
`ADMIN_SEED_PASSWORD`, el arranque falla explícitamente (*fail-fast*):
nunca se crea un admin con contraseña vacía o conocida. El seed es
idempotente — si el admin ya existe, un restart posterior con
`ADMIN_SEED_ENABLED=true` no lo duplica ni le resetea la contraseña — pero
desactivarlo evita reevaluar esas variables en cada arranque.

> **`admin@biopet.ec` / `Admin123*`** (visible en `.env.example` y
> `db/seed.sql`) es exclusivamente el ADMIN de **desarrollo local** —
> ficticio, público, documentado como tal. Nunca debe usarse como
> `ADMIN_SEED_EMAIL`/`ADMIN_SEED_PASSWORD` en Render.

En desarrollo local (`docker-compose.yml`), `.env.example` ya trae esas 3
variables con el email/contraseña ficticios de arriba — copiar a `.env`
reproduce el mismo admin de siempre sin pasos adicionales.

## 8. Comportamiento del plan Free (cold start)

`biopet-v2-backend` y `biopet-v2-frontend` corren en el plan `free` de
Render Web Services, que **duerme el servicio por inactividad** y lo
despierta en la siguiente petición entrante. Mientras el backend todavía
está despertando:

- la primera petición tras un período de inactividad puede tardar
  notablemente más que las siguientes;
- nginx (frontend) puede devolver `502` de forma transitoria si intenta
  proxyar `/api/` antes de que el backend esté aceptando conexiones;
- `GET /actuator/health` contra el backend permite confirmar si ya está
  `UP`.

Esto es un comportamiento conocido del plan Free de Render, no un defecto
de BIOPET. Este documento no fija tiempos exactos de arranque/despertar:
no están medidos ni versionados en este repositorio. Pasar a un plan de
pago elimina el *cold start*.

## 9. Alternativa: VPS propio (`docker-compose.prod.yml`)

Render es el despliegue oficial de BIOPET V2. Si en cambio se despliega en
un VPS propio (p.ej. DigitalOcean/Hetzner), el stack equivalente es:

1. Apuntar el dominio al VPS (registro A).
2. Crear `.env` desde `.env.example` con valores reales (`DOMAIN`,
   `JWT_SECRET`, credenciales de BD, `APP_EXTERNAL_API_KEY`).
3. Levantar:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

4. Verificar:

```bash
curl -I https://<tu-dominio>/actuator/health
```

`docker-compose.prod.yml` construye backend y frontend **directamente
desde este repositorio** (`build: ./Backend`, `build: ./frontend`) — no
depende de ninguna imagen publicada en GHCR ni de ningún otro registro
externo. El proxy `caddy` emite y renueva HTTPS con Let's Encrypt
automáticamente (`Caddyfile` en este mismo directorio).

## 10. Referencias

- Blueprint YAML: https://render.com/docs/blueprint-spec
- Blueprints (IaC): https://render.com/docs/infrastructure-as-code
- Key Value (Valkey/Redis): https://render.com/docs/key-value
- Healthchecks en Render: Dashboard del servicio → Settings → Health Check
  Path = `/actuator/health`
