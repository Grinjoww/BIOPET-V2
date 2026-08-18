# DRAFT-ADR-007 (handoff Fred) — Despliegue: Render como proveedor, alternativas descartadas

> **Handoff para Jaime** — borrador de contenido, NO normalizado. Jaime decide
> numeración final y posible fusión con `docs/adr/ADR-005-despliegue.md`
> (entrega anterior: reproducibilidad Docker local, complementario, no
> reemplazado por este). Este draft aporta la decisión de la rama
> `fred/f08-f11-produccion-despliegue` (F08–F11): el proveedor de producción
> elegido, las alternativas descartadas y la evidencia real.
> Formato Nygard: contexto, decisión, alternativas, consecuencias, evidencia.

## Contexto

BIOPET hasta la Tercera Entrega solo se desplegaba en el entorno de desarrollo
local con Docker Compose (`Makefile`, digests sha256, ADR-005). La guía de la
Entrega Final (bloques de despliegue) exige: (1) un despliegue real en un
proveedor de nube con HTTPS válido (no autofirmado), (2) healthcheck público
verificable, (3) procedimientos operativos (runbook) y (4) estrategia de backup
probada con restauración real.

Restricciones del equipo: presupuesto cero (plan gratuito), el frontend de
Zaida aún no estaba listo para desplegarse (el backend no depende de él) y no
se permitía inventar URLs ni secretos en la documentación.

## Decisión

**Se adopta Render (render.com) como proveedor de producción, con Blueprint
Infrastructure-as-Code (`render.yaml` en la raíz del repo).**

Recursos definidos en `render.yaml`:

| Recurso | Tipo | Nota |
|---|---|---|
| `biopet-backend` | Web docker | JVM 21, puerto 8080 interno, healthcheck `GET /actuator/health`, plan free |
| `biopet-frontend` | Web docker | nginx, plan free (se crea cuando Zaida lo tenga listo; no es requisito para el backend) |
| `biopet-cache` | Key Value | Valkey (Redis compatible), usado por `REDIS_HOST`/`REDIS_PORT` |
| `biopet-db` | Postgres gestionado | `biopet_db`, usuario `biopet_user`, plan free (caduca a 30 días) |

Mecanismos de la decisión:
- **HTTPS válido automático**: Render emite/renueva certificados para
  `<servicio>.onrender.com`; el backend corre en HTTP 8080 interno y Render
  termina TLS en el edge (no se usa el perfil `tls` autofirmado de desarrollo).
- **Sin secretos versionados**: `JWT_SECRET` y `CORS_ALLOWED_ORIGINS` con
  `sync: false` (se completan en el dashboard, nunca en el repo); credenciales
  de BD y Redis enlazadas por `fromDatabase`/`fromService`.
- **URL JDBC construida en runtime** (`dockerCommand`): Render no entrega una
  URL JDBC lista, solo host/port/name — se compone con
  `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`.
- **Rol de aplicación**: el Postgres gestionado no ejecuta `db/roles.sql`, así
  que el rol `biopet_app` no existe en Render; el backend usa el rol de la BD
  gestionada como `DB_APP_USER`/`DB_APP_PASSWORD` (documentado en DEPLOYMENT.md).
- **Operación y backup**: `docs/despliegue/DEPLOYMENT.md` (pasos exactos),
  `RUNBOOK.md` (rotación JWT/credenciales, actualización, rollback) y
  `BACKUP.md` con prueba real de `pg_dump`/`pg_restore` ejecutada el 2026-08-17
  (dump 64,762 bytes, 6 tablas restauradas en BD limpia, admin verificado,
  `fn_siguiente_numero_ficha` operativo post-restauración; evidencia en
  `docs/despliegue/ejemplo-backup-20260817.sql`).

## Alternativas consideradas

**A — VPS propio con Docker Compose + Caddy (`docker-compose.prod.yml`).**
Considerada y dejada como alternativa documentada, no como elección primaria.
Ventajas: control total, sin límite de 30 días de Postgres free. Desventajas:
requiere un VPS pagado (o IP pública propia), administración del SO, DNS y
renovación de Let's Encrypt (Caddy la automatiza pero el operador debe operar
el VPS); el repo no tenía ningún VPS real disponible al momento de decidir.

**B — Render con servicios creados manualmente (sin Blueprint).** Descartada:
el Blueprint (`render.yaml`) permite a un tercero reproducir el despliegue
completo con un clic (criterio de reproducibilidad), enlaza credenciales
automáticamente y evita 20+ campos manuales propensos a error.

**C — PaaS alternativo (Heroku, Railway, Fly.io) o cloud generalista (AWS,
GCP, Azure).** Descartadas: Heroku dejó su free tier; Railway/Fly exigían
tarjeta y configuración adicional; AWS/GCP/Azure sobrepasan la curva y el
presupuesto del proyecto académico. Render ofrece free tier funcional para los
4 recursos y Blueprint IaC nativo.

**D — TLS autofirmado en el backend (perfil `tls` de desarrollo) como
"producción".** Descartada explícitamente: la guía exige HTTPS válido; el
perfil `tls` es exclusivamente académico/local (ADR-006-autenticacion-seguridad,
REQ-NF-002).

## Consecuencias

**Positivas:**
- HTTPS válido automático sin configuración TLS en la app (Render edge).
- Reproducibilidad: un tercero crea el despliegue conectar el repo y elegir
  plan (pasos exactos en DEPLOYMENT.md).
- Healthcheck real y público (`/actuator/health`), verificación final del
  Paso 7 pendiente solo de la URL real del usuario.
- Backup probado de verdad y procedimiento documentado (BACKUP.md + RUNBOOK.md).
- Cero secretos en el repo (verificado); la API key externa real hallada en
  `.env.example` fue retirada y se documentó la rotación como pendiente del
  operador.

**Negativas y compromisos:**
- Plan free de Render: Postgres se elimina a los 30 días (para producción
  permanente debe pagarse `starter`); los web services free se suspenden por
  inactividad (spin-down) — latencia de "cold start" tras periodos sin tráfico.
- El rol `biopet_app` (privilegios mínimos, ADR-004) no aplica en el Postgres
  gestionado: en Render la app usa el rol de la BD gestionada (menos
  segregación que en local/VPS).
- El frontend local (`frontend/nginx.conf`) proxy a `backend:8080`; en Render
  se usa `docs/despliegue/nginx-render.conf` (`biopet-backend:8080`) — swap
  manual a coordinar con Zaida al desplegar el frontend.
- `DB_APP_USER`/`DB_PASSWORD` en `.env.example` quedaron comentados para no
  romper el CI (un `env_file` toma la última asignación; valores vacíos
  rompían `JwtService` — fix `3b6d893` en PR #8).

## Archivos y evidencia de respaldo

- `render.yaml` (Blueprint IaC: backend, frontend, keyvalue, postgres).
- `docs/despliegue/DEPLOYMENT.md` (pasos exactos para un tercero, incluye
  alternativa VPS), `RUNBOOK.md`, `BACKUP.md`, `nginx-render.conf`,
  `ejemplo-backup-20260817.sql` (evidencia de restauración real).
- `docker-compose.prod.yml` + `docs/despliegue/Caddyfile` (alternativa VPS).
- `.env.example` (secciones desarrollo/producción, sin secretos).
- PR #8 (`fred/f08-f11-produccion-despliegue`) mergeado a main (`112b5c0`).

## Referencias

- `ADR-005-despliegue.md` (reproducibilidad local — complementario, vigente).
- `ADR-006-autenticacion-seguridad.md` (TLS autofirmado solo académico).
- `docs/despliegue/DEPLOYMENT.md` (guía operativa completa).