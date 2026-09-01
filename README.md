## Versiones del proyecto

Este repositorio corresponde a la **Entrega Final académica de BIOPET** y
conserva la trazabilidad, evidencias, documentación y correcciones realizadas
a partir de la retroalimentación docente.

El tag histórico `v1.0.0` se mantiene inmutable para preservar la
reproducibilidad de la versión evaluada. Las correcciones documentales y de
trazabilidad posteriores se encuentran incorporadas en `main` sin alterar
dicho tag.

### Versión funcional evolucionada

De forma paralela, el desarrollo funcional y visual del sistema continuó en:

**BIOPET-V2:**  
https://github.com/Grinjoww/BIOPET-V2

Esta versión incorpora mejoras posteriores de interfaz, experiencia de
usuario y funcionalidades adicionales, entre ellas el módulo de facturación.

La separación entre ambos repositorios fue intencional: se mantuvo este
repositorio como referencia académica y reproducible de la entrega, mientras
que BIOPET-V2 se utilizó para continuar la evolución del producto sin
reescribir ni alterar los artefactos históricos de la evaluación.

Para revisión de evidencias académicas, reproducibilidad, informe y
retroalimentación: **ENTREGA-FINAL-BIOPET**.

Para revisión del estado funcional y visual más reciente del sistema:
**BIOPET-V2**.

# BIOPET V2 — Sistema Web de Gestión Veterinaria

Repositorio oficial: [github.com/Grinjoww/BIOPET-V2](https://github.com/Grinjoww/BIOPET-V2)

> BIOPET V2 es la versión actual del sistema, desplegada y funcional en
> producción (Render). El proyecto académico original, BIOPET v1.0.0, quedó
> cerrado y separado — ver [Historia académica](#historia-académica).

---

## Qué es BIOPET V2

BIOPET es un sistema web de gestión veterinaria: administra usuarios,
mascotas, citas, consultas y vacunas, con control de acceso por roles y
trazabilidad de la información clínica. El backend expone una API REST con
Spring Boot sobre PostgreSQL; el frontend es una aplicación Angular que
consume esa API a través de un proxy nginx. Redis (Valkey en producción) se
usa como caché de datos de consulta frecuente y como lista negra de tokens
JWT invalidados.

Esta iteración (V2) es un rediseño completo del frontend sobre el mismo
backend real: interfaz moderna, exposición de funcionalidades que antes no
tenían pantalla propia (Panel operativo, ficha clínica, Perfil) y una
arquitectura de despliegue reproducible en Render.

## Funcionalidades actuales

- **Panel operativo** — resumen de citas/consultas para ADMIN, VETERINARIO
  y AUXILIAR (`GET /api/dashboard/resumen`).
- **Mascotas** — alta, edición, baja lógica.
- **Ficha clínica por mascota** — historial de consultas y vacunas de una
  mascota en una sola vista.
- **Vacunas** — registro y seguimiento de aplicaciones.
- **Citas** — programación y actualización de estado.
- **Consultas** — registro clínico (mascota activa, veterinario autorizado).
- **Usuarios** — alta y administración de cuentas (solo ADMIN).
- **Perfil** — datos de la cuenta autenticada.
- **Resumen por especies** — agregado de mascotas por especie
  (`GET /api/mascotas/resumen-especies`).
- **Integración externa de especies** — información complementaria de una
  especie consultada a una API externa, cacheada en Redis
  (`GET /api/externa/especies`).

No se documentan aquí funcionalidades que el backend no expone.

## Roles

| Rol | Alcance |
|---|---|
| `ROLE_ADMIN` | Administración completa, incluida gestión de Usuarios. |
| `ROLE_VETERINARIO` | Operación clínica: mascotas, citas, consultas, vacunas, Panel. |
| `ROLE_AUXILIAR` | Igual que Veterinario en alcance operativo, sin funciones exclusivas de veterinario. |
| `ROLE_DUENO` | Solo sus propias mascotas, citas, consultas y vacunas. Sin Panel ni Usuarios. |

La navegación del frontend (sidebar, guards de ruta) es únicamente
*wayfinding*: la autorización real se aplica en el backend con
`@PreAuthorize` en cada endpoint, y el ownership de `ROLE_DUENO` se filtra
en el servicio, no en el cliente.

## Arquitectura y stack

```
Browser
  → biopet-v2-frontend  (Angular, servido por nginx)
      → nginx /api/ (reverse proxy, mismo origen)
        → biopet-v2-backend  (Spring Boot)
            → biopet-v2-db     (PostgreSQL, gestionado por Render)
            → biopet-v2-cache  (Redis/Valkey, gestionado por Render)
```

| Componente | Versión |
|---|---|
| Java | 21 (Eclipse Temurin) |
| Spring Boot | 3.2.12 |
| Angular | 17.3.x (standalone components) |
| TypeScript | 5.4.5 |
| PostgreSQL (local/Docker) | 16-alpine |
| Redis (local/Docker) | 7-alpine |
| Maven | 3.9 |
| Node / npm | 20 (build), Karma/Jasmine para tests |
| Docker / Docker Compose | soporte `docker compose` |

En producción, PostgreSQL y Redis/Valkey son servicios **gestionados por
Render** (versión administrada por el proveedor, no fijada en este repo).

Tecnologías relevantes: migraciones **Flyway** (`V1`→`V6`,
`Backend/src/main/resources/db/migration/`, catálogo de procedimientos en
[`docs/basedatos/CATALOGOSP.md`](docs/basedatos/CATALOGOSP.md)), caché
**Redis** vía Spring Cache, errores estandarizados **RFC 7807**
(`ProblemDetail`), **Testcontainers** en la suite de integración del
backend, **JaCoCo** para cobertura, **Karma/Jasmine** en el frontend,
**nginx** como reverse proxy del frontend, y **Render** como plataforma de
despliegue.

## Seguridad

- Autenticación JWT en cookies `HttpOnly` + `Secure` + `SameSite=Strict`
  (`access_token` / `refresh_token`); el token nunca es accesible desde
  JavaScript del navegador.
- El backend es la única fuente de autorización: cada endpoint declara sus
  roles permitidos con `@PreAuthorize`, y `ROLE_DUENO` solo puede leer/editar
  sus propios recursos (filtrado en el servicio, no en el cliente).
- Lista negra de tokens en Redis: el logout invalida el JWT vigente aunque
  no haya expirado.
- *Rate limiting* de intentos de login y de registro.
- Una cuenta marcada inactiva pierde acceso de inmediato, incluso con un
  JWT válido y no expirado (se re-verifica en cada request).
- Secretos obligatorios y sin valor por defecto en producción (`JWT_SECRET`,
  `DB_APP_PASSWORD`, `DB_PASSWORD`): el arranque falla explícitamente si
  faltan (*fail-fast*), en vez de usar un secreto conocido.
- El bootstrap del ADMIN inicial es **opt-in** (ver siguiente sección), no
  automático.

## Bootstrap del ADMIN (seed)

El primer usuario ADMIN no se crea por defecto. Se controla con 4 variables
de entorno:

| Variable | Rol |
|---|---|
| `ADMIN_SEED_ENABLED` | Habilita el bootstrap (por defecto `false`). |
| `ADMIN_SEED_EMAIL` | Email del ADMIN a crear. |
| `ADMIN_SEED_PASSWORD` | Contraseña del ADMIN a crear. |
| `ADMIN_SEED_NAME` | Nombre a mostrar (opcional). |

En producción, `ADMIN_SEED_EMAIL`/`ADMIN_SEED_PASSWORD` se completan con
valores reales en el dashboard de Render — **nunca** se versiona una
contraseña en el repositorio, y el par `admin@biopet.ec` / `Admin123*` que
aparece en `.env.example` es exclusivamente un dato de **desarrollo local**,
público y documentado como tal. Tras confirmar que el ADMIN quedó creado en
producción, se recomienda volver a poner `ADMIN_SEED_ENABLED=false` (el seed
es idempotente y no lo duplica ni resetea la contraseña, pero conviene no
reevaluar esas variables en cada arranque).

Detalle operativo completo en
[`docs/despliegue/DEPLOYMENT.md`](docs/despliegue/DEPLOYMENT.md).

## Desarrollo local

Requisitos: Docker Desktop (o Docker Engine + Compose), Java 21 + Maven
(si se corre el backend fuera de contenedor), Node 20+ (si se corre el
frontend fuera de contenedor), GNU Make, y Bash (en Windows: Git Bash en el
`PATH`).

```bash
cp .env.example .env
make up      # docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d
```

`.env.example` ya trae valores de desarrollo (incluido el ADMIN semilla
ficticio descrito arriba) — no requiere completar secretos reales para
levantar el entorno local.

```bash
make down    # detiene los contenedores sin borrar los volúmenes de datos
```

Para trabajar solo en el frontend contra el backend en Docker:

```bash
cd frontend
npm ci
npm start    # ng serve --host 0.0.0.0 --port 4200
```

## Tests

Última validación completa: **298 tests backend** y **94 tests frontend**,
todos verdes.

```bash
# Backend — pruebas + gate de cobertura JaCoCo (Testcontainers incluido)
cd Backend && mvn clean verify

# Frontend — pruebas (Karma/Jasmine)
cd frontend && npm run test:ci

# Frontend — build de producción
cd frontend && npm run build
```

CI (`.github/workflows/ci.yml`, GitHub Actions sobre `main`) ejecuta en cada
push/PR: `backend-test` (`mvn clean verify`), `frontend-build` (`npm ci` +
build de producción), `traceability`, `sql-audit`, `security-static`
(SpotBugs + Find Security Bugs) y `zap-baseline` (OWASP ZAP), todos verdes.

## Despliegue

BIOPET V2 está desplegado en **Render** mediante un Blueprint IaC
(`render.yaml`), que crea cuatro recursos:

| Recurso | Tipo |
|---|---|
| `biopet-v2-frontend` | Web service Docker (Angular + nginx) |
| `biopet-v2-backend` | Web service Docker (Spring Boot) |
| `biopet-v2-db` | PostgreSQL gestionado |
| `biopet-v2-cache` | Redis/Valkey gestionado (Key Value) |

El navegador solo habla con `biopet-v2-frontend`: nginx reenvía `/api/` al
backend real (`BACKEND_URL`, resuelto automáticamente por Render), por lo
que frontend y backend funcionan **same-origin** desde el navegador — sin
configuración CORS adicional en el camino normal de uso.

Frontend en producción: [biopet-v2-frontend.onrender.com](https://biopet-v2-frontend.onrender.com)
(healthcheck del backend: `GET /actuator/health`).

Estos nombres de recursos (`biopet-v2-*`) son intencionalmente distintos de
los del proyecto académico original (`biopet-*`, sin sufijo), que sigue
desplegado por separado y no debe tocarse.

Guía operativa completa (variables de entorno, pasos exactos, alternativa
VPS) en [`docs/despliegue/DEPLOYMENT.md`](docs/despliegue/DEPLOYMENT.md).

## Estructura del proyecto

```
Backend/    API Spring Boot (controllers, services, security, migraciones Flyway)
frontend/   Aplicación Angular (standalone components, lazy-loaded features)
db/         Catálogo de procedimientos PostgreSQL y bootstrap de roles
docs/       Documentación técnica y académica (despliegue, seguridad, ADR, SRS...)
scripts/    Scripts de auditoría y validación usados por Makefile/CI
k6/         Pruebas de rendimiento
render.yaml               Blueprint de despliegue en Render
docker-compose.yml         Entorno de desarrollo local
docker-compose.prod.yml    Alternativa de despliegue en VPS propio
Makefile                   make up / make down / make all
```

## Estado del proyecto

BIOPET V2 es la versión actual del sistema: desplegada en Render y
funcional (login y cookies verificados en producción para los 4 roles). No
está "en construcción".

Mejoras no bloqueantes consideradas a futuro: perfil self-service, foto de
mascota, filtros avanzados de búsqueda, pruebas end-to-end, y rotación de
refresh tokens.

## Historia académica

BIOPET V2 evolucionó desde **BIOPET v1.0.0**, proyecto de Fin de Curso
(Aplicaciones Web) de la Universidad Técnica Estatal de Quevedo, cuya
entrega académica ya está cerrada (tag `v1.0.0`, DOI de software
[10.5281/zenodo.21988746](https://doi.org/10.5281/zenodo.21988746) y de
dataset [10.5281/zenodo.21988785](https://doi.org/10.5281/zenodo.21988785)).
Ese repositorio y esa evaluación quedan como referencia histórica, no como
el producto actual.

Equipo original: Beltrán Montiel, Fred Adrián · Mariscal Cabrera, Jaime
Josué · Taipe Mora, Zaida Melissa (UTEQ). Detalle de autoría por persona
(taxonomía CRediT) en [`CONTRIBUTORS.md`](CONTRIBUTORS.md); metadatos de
citación en [`CITATION.cff`](CITATION.cff).

## Documentación adicional

| Tema | Documento |
|---|---|
| Despliegue en Render | [`docs/despliegue/DEPLOYMENT.md`](docs/despliegue/DEPLOYMENT.md) |
| Backups y runbook operativo | [`docs/despliegue/BACKUP.md`](docs/despliegue/BACKUP.md), [`docs/despliegue/RUNBOOK.md`](docs/despliegue/RUNBOOK.md) |
| Procedimientos PostgreSQL | [`docs/basedatos/CATALOGOSP.md`](docs/basedatos/CATALOGOSP.md) |
| Requisitos (SRS, histórico) | [`docs/requisitos/SRS.md`](docs/requisitos/SRS.md) |
| Decisiones de arquitectura (ADR) | [`docs/adr/`](docs/adr/) |
| Ética y datos de prueba | [`docs/etica/ETHICS.md`](docs/etica/ETHICS.md) |

## Licencia

Este proyecto se distribuye bajo licencia **MIT** — ver [`LICENSE`](LICENSE).
