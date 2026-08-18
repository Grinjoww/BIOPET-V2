# BIOPET — Sistema Web de Gestión Veterinaria

**Universidad Técnica Estatal de Quevedo**
Proyecto Fin de Curso — Aplicaciones Web
**Entrega Final — versión objetivo `v1.0.0`**

> Estado: preparación final de `v1.0.0`. El tag `v1.0.0` todavía no ha sido
> publicado (ver [Historial de entregas / tags](#historial-de-entregas--tags)).

---

## Integrantes

- **Beltrán Montiel, Fred Adrián** — Universidad Técnica Estatal de Quevedo
- **Mariscal Cabrera, Jaime Josué** — Universidad Técnica Estatal de Quevedo (`jmariscalc@uteq.edu.ec`)
- **Taipe Mora, Zaida Melissa** — Universidad Técnica Estatal de Quevedo

Docente responsable (evaluación, no autoría del software): Dr. Gleiston
Cicerón Guerrero Ulloa, Ph.D.

---

## Descripción

BIOPET es un sistema web de gestión veterinaria que permite administrar
usuarios (dueños, veterinarios y administradores), mascotas, citas,
consultas y vacunas, con control de acceso por roles y trazabilidad de la
información clínica. El backend expone una API REST con Spring Boot sobre
PostgreSQL; el frontend es una aplicación Angular que consume esa API.
Redis se usa como caché de datos de consulta frecuente y para el manejo de
tokens de sesión (lista negra de JWT invalidados).

El proyecto se desarrolla siguiendo un ciclo de entregas incrementales
(ver [Historial de entregas / tags](#historial-de-entregas--tags)), con
pruebas automatizadas, análisis estático de seguridad y validación
dinámica (OWASP ZAP) integrados al flujo de trabajo.

---

## Arquitectura y tecnologías

Versiones verificadas en [`docs/entorno/versions.txt`](docs/entorno/versions.txt)
y en la configuración real del proyecto (`Backend/pom.xml`,
`frontend/package.json`, `docker-compose.yml`):

| Componente | Versión |
|---|---|
| Java | 21 (Eclipse Temurin) |
| Spring Boot | 3.2.12 |
| Angular | 17.3.x (CLI 17.3.17, core 17.3.12) |
| TypeScript | 5.4.5 |
| PostgreSQL | 16-alpine |
| Redis | 7-alpine |
| Docker / Docker Compose | 29.6.x / v5.3.0 |
| Maven | 3.9 (imagen de build `maven:3.9-eclipse-temurin-21`) |
| Node / npm | 20-alpine (imagen de build), local 24.18.0 / 11.16.0 |
| Flyway | integrado vía Spring Boot (migraciones `V1`→`V6`) |
| JaCoCo | 0.8.12 |
| SpotBugs / Find Security Bugs | 4.10.3.0 / 1.14.0 |
| OWASP ZAP | 2.17.0 (`ghcr.io/zaproxy/zaproxy:stable`) |
| k6 (rendimiento) | v2.1.0 |

---

## Funcionalidades principales

- **Autenticación y autorización**: JWT, cookies `HttpOnly`/`Secure`/`SameSite`, control de acceso por roles (`ROLE_ADMIN`, `ROLE_VETERINARIO`, `ROLE_DUENO`).
- **Usuarios**: alta, consulta y administración de cuentas.
- **Mascotas**: registro, edición, baja lógica, resumen agregado por especie.
- **Citas**: programación y actualización de estado (individual y masiva).
- **Consultas**: registro clínico validado (mascota activa, veterinario autorizado).
- **Vacunas**: registro y seguimiento de aplicaciones.
- **Integración externa con caché**: `ExternalApiService` usa Redis para reducir llamadas repetidas.
- **Auditoría y manejo de errores**: respuestas de error estandarizadas (RFC 7807 / `ProblemDetail`), *rate limiting* de login, auditoría de eventos de autenticación.

---

## Base de datos y procedimientos

Las migraciones Flyway del backend llegan actualmente hasta **`V6`**
(`Backend/src/main/resources/db/migration/`). Existen **6 rutinas
PostgreSQL finales** (objetos `PROCEDURE`), todas invocadas desde Java
mediante un mecanismo JPA formal (`@Procedure`, con
`@NamedStoredProcedureQuery` explícito para las tres que devuelven un
conjunto de filas vía `REF_CURSOR`). El catálogo completo — nombre,
categoría, parámetros, mecanismo de invocación y control de acceso — está
en [`docs/basedatos/CATALOGOSP.md`](docs/basedatos/CATALOGOSP.md).

---

## Cómo ejecutar el proyecto

### Requisitos

- Docker Desktop (o Docker Engine + Compose) con soporte para `docker compose`.
- Java 21 y Maven (si se ejecuta el backend fuera de contenedor).
- Node.js 20+ y npm (si se ejecuta el frontend fuera de contenedor).
- GNU Make.
- Bash, para los scripts `.sh` de `scripts/` (en Windows: Git Bash en el `PATH`).

### Levantar el entorno

```bash
make up
```

Equivale a `docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d`, generando primero el keystore TLS de desarrollo.

### Detener el entorno

```bash
make down
```

Detiene los contenedores sin borrar los volúmenes de datos.

### Validación técnica completa

```bash
make all
```

Ejecuta, en orden y con parada inmediata ante el primer fallo:

1. **Backend + JaCoCo** (`mvn clean verify`: pruebas, Testcontainers, Flyway, gate de cobertura ≥70% LINE/BRANCH)
2. **Frontend** (build de producción Angular)
3. **Trazabilidad** (SRS ↔ matriz de requisitos ↔ historias/casos de uso)
4. **Auditoría SQL dinámica** (`db/procs/*.sql`)
5. **Análisis estático de seguridad** (SpotBugs + Find Security Bugs)
6. **OWASP ZAP Baseline Scan**

`make all` **no** incluye Lighthouse ni ningún paso de despliegue/GHCR/Zenodo (ver secciones correspondientes más abajo).

---

## Estado técnico verificado

| Validación | Resultado |
|---|---|
| Tests backend | 205 / 205 |
| Failures | 0 |
| Errors | 0 |
| JaCoCo LINE | 91.8 % |
| JaCoCo BRANCH | 79.4 % |
| Flyway | V1 → V6 |
| Trazabilidad | 38 / 38 |
| SQL dinámico inseguro | 0 hallazgos |
| SpotBugs `SQL_*` | 0 hallazgos |
| ZAP High | 0 |
| Frontend production build | OK |
| `make all` | OK |
| SUS (usabilidad, n=18) | media 74.44 / 100, IC95 % [63.33, 85.56] |

Fuentes: ejecución real de `make all` sobre este repositorio;
[`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv);
[`docs/mediciones/sec/static-analysis/README.md`](docs/mediciones/sec/static-analysis/README.md);
[`docs/mediciones/sec/zap/README.md`](docs/mediciones/sec/zap/README.md);
[`docs/mediciones/sus/REPORT.md`](docs/mediciones/sus/REPORT.md).

---

## Seguridad

BIOPET aplica varios controles verificables en el repositorio, no una
afirmación genérica de "sistema seguro":

- Autenticación con **JWT** y cookies `HttpOnly` + `Secure` + `SameSite`.
- Control de acceso por roles a nivel de endpoint y de servicio.
- ***Rate limiting*** de intentos de login (`LoginRateLimiterService`).
- Manejo de errores estandarizado con **`ProblemDetail`** (RFC 7807).
- Cabeceras de seguridad HTTP configuradas (ver [`docs/mediciones/sec/A05-security-headers.md`](docs/mediciones/sec/A05-security-headers.md)).
- **Auditoría SQL dinámica** automatizada sobre `db/procs/*.sql` (0 hallazgos actuales).
- **SpotBugs + Find Security Bugs**, con gate obligatorio sobre hallazgos `SQL_*` (0 actuales). Existe un hallazgo documentado y no-SQL, `SPRING_CSRF_PROTECTION_DISABLED`, que está mitigado por la arquitectura de cookies adoptada (ver [`docs/mediciones/sec/static-analysis/README.md`](docs/mediciones/sec/static-analysis/README.md) para el detalle); no se lo descarta como falso positivo, se documenta su mitigación.
- **OWASP ZAP Baseline Scan**, con gate obligatorio: **High = 0**. Alertas de severidad media/baja/informativa se conservan íntegras como evidencia y no bloquean el build.

Ningún control anterior implica que el sistema esté libre de riesgo; son
las medidas y umbrales efectivamente verificados hasta esta entrega.

---

## Rendimiento

Evidencia real en [`docs/mediciones/perf/REPORT.md`](docs/mediciones/perf/REPORT.md):
5 corridas en caliente y 5 en frío con k6, sobre HTTPS/TLS 1.3.

- p95 en caliente ≤ 200 ms en las 5 corridas.
- p95 en frío ≤ 500 ms en las 5 corridas.
- Tasa de error: 0 % en todas las corridas.

El reporte incluye intervalos de confianza al 95 % (t de Student) y una
comparación pareada (Wilcoxon) entre corridas en frío y en caliente.

---

## Evidencias y documentación

| Tema | Documento |
|---|---|
| Requisitos (SRS) | [`docs/requisitos/SRS.md`](docs/requisitos/SRS.md) |
| Trazabilidad | [`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv) |
| Procedimientos PostgreSQL | [`docs/basedatos/CATALOGOSP.md`](docs/basedatos/CATALOGOSP.md) |
| Rendimiento (k6) | [`docs/mediciones/perf/REPORT.md`](docs/mediciones/perf/REPORT.md) |
| Seguridad — ZAP | [`docs/mediciones/sec/zap/README.md`](docs/mediciones/sec/zap/README.md) |
| Seguridad — análisis estático | [`docs/mediciones/sec/static-analysis/README.md`](docs/mediciones/sec/static-analysis/README.md) |
| Usabilidad (SUS) | [`docs/mediciones/sus/REPORT.md`](docs/mediciones/sus/REPORT.md) |
| Lighthouse | [`docs/mediciones/lighthouse/README.md`](docs/mediciones/lighthouse/README.md) |
| Calidad ISO/IEC 25010 | [`docs/arquitectura/ISO-25010.md`](docs/arquitectura/ISO-25010.md) |
| Checklists metodológicos (PRISMA, INCOSE, RALPH) | [`docs/checklists/`](docs/checklists/) |
| Bitácora de observaciones | [`docs/observaciones/OBSERVACIONES.md`](docs/observaciones/OBSERVACIONES.md) |
| Decisiones de arquitectura (ADR) | [`docs/adr/`](docs/adr/) |
| Política de versionado | [`docs/VERSIONING.md`](docs/VERSIONING.md) |

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) ejecuta validaciones sobre la
rama `main` en cada `push` y `pull_request`, con los jobs:

- `backend-test` — `mvn clean verify` (pruebas + gate JaCoCo).
- `frontend-build` — `npm ci` + build de producción Angular.
- `traceability` — `scripts/validate-traceability.sh`.
- `sql-audit` — `scripts/audit-sql-dynamic.sh`.
- `security-static` — SpotBugs + Find Security Bugs, gate sobre hallazgos SQL.
- `zap-baseline` — OWASP ZAP Baseline Scan, gate sobre severidad alta.

Lighthouse **no** forma parte de CI todavía (ver [Lighthouse](#lighthouse)).

---

## Historial de entregas / tags

| Tag | Significado |
|---|---|
| `v0.1.0-entrega-1b` | Entrega 1B (histórico) |
| `v0.7.0` | Entrega previa |
| `v0.7.1` | Entrega previa |
| `v0.9.0-rc` | Tercera Entrega (release candidate) |
| `v1.0.0` | **Versión final objetivo, pendiente de publicación** |

---

## Despliegue

Pendiente de incorporar/verificar URL pública HTTPS final. No existe en
esta rama evidencia verificable de un despliegue activo y accesible; no se
publica ninguna URL como si estuviera en producción. La estrategia de
reproducibilidad local (Docker, digests fijados por imagen) está
documentada en [`docs/adr/ADR-005-despliegue.md`](docs/adr/ADR-005-despliegue.md).

---

## GHCR

Pendiente de publicación de imagen final y digest GHCR.

---

## DOI / Zenodo

- DOI de software: pendiente de publicación en Zenodo.
- DOI del dataset: pendiente de publicación.

---

## Lighthouse

Existe evidencia histórica en
[`docs/mediciones/lighthouse/README.md`](docs/mediciones/lighthouse/README.md),
correspondiente a una única corrida en perfil móvil simulado (SEO = 82,
por debajo del umbral configurado de 90). La evaluación final en los
perfiles móvil **y** desktop, exigida para el cierre de esta entrega,
todavía está en proceso; no se presenta la evidencia histórica como
cumplimiento final de los umbrales. `make lighthouse` reproduce la
auditoría (requiere `make up` previo) pero no está integrado en
`make all` ni en CI mientras esa evaluación no se cierre.

---

## Reproducibilidad

El comando principal de reproducción técnica es:

```bash
make all
```

Requiere Docker, Java, Maven, Node/npm, GNU Make y Bash (para los scripts
`.sh` de `scripts/`). La referencia de reproducibilidad es Linux/GitHub
Actions; el Makefile evita construcciones específicas de un único shell
para poder ejecutarse también en Windows con Git Bash en el `PATH`.

---

## Licencia / citación

Este proyecto se distribuye bajo licencia **MIT** — ver [`LICENSE`](LICENSE).

Para citar el software, usar los metadatos de [`CITATION.cff`](CITATION.cff).
Sus metadatos (versión, DOI) se actualizarán antes de publicar el tag
`v1.0.0`, conforme a lo que quede pendiente en las secciones
[GHCR](#ghcr) y [DOI / Zenodo](#doi--zenodo) de este README.
