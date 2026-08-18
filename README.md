# BIOPET — Sistema Web de Gestión Veterinaria

**Universidad Técnica Estatal de Quevedo**
Proyecto Fin de Curso — Aplicaciones Web
**Entrega Final — `v1.0.0`**

> Estado: Entrega Final publicada. El tag `v1.0.0` fue creado y publicado
> (commit `ba41e11`, ver [Historial de entregas / tags](#historial-de-entregas--tags)).

---

## Integrantes

- **Beltrán Montiel, Fred Adrián** — Universidad Técnica Estatal de Quevedo (`fbeltranm@uteq.edu.ec`)
- **Mariscal Cabrera, Jaime Josué** — Universidad Técnica Estatal de Quevedo (`jmariscalc@uteq.edu.ec`)
- **Taipe Mora, Zaida Melissa** — Universidad Técnica Estatal de Quevedo (`ztaipem@uteq.edu.ec`)

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
| PostgreSQL (local/Docker) | 16-alpine |
| Redis (local/Docker) | 7-alpine |
| Docker / Docker Compose | 29.6.x / v5.3.0 |
| Maven | 3.9 (imagen de build `maven:3.9-eclipse-temurin-21`) |
| Node / npm | 20-alpine (imagen de build), local 24.18.0 / 11.16.0 |
| Flyway | integrado vía Spring Boot (migraciones `V1`→`V6`) |
| JaCoCo | 0.8.12 |
| SpotBugs / Find Security Bugs | 4.10.3.0 / 1.14.0 |
| OWASP ZAP | 2.17.0 (`ghcr.io/zaproxy/zaproxy:stable`) |
| k6 (rendimiento) | v2.1.0 |

**Producción (Render)** usa versiones distintas a las de desarrollo local,
gestionadas por el propio proveedor — no se reemplazan las de arriba, se
documentan aparte:

| Componente (producción) | Versión |
|---|---|
| PostgreSQL | 18 |
| Redis (compatible) | Valkey 8 |
| HTTPS | Activo (gestionado por Render) |

---

## Funcionalidades principales

- **Autenticación y autorización**: JWT, cookies `HttpOnly`/`Secure`/`SameSite`, control de acceso por roles (`ROLE_ADMIN`, `ROLE_VETERINARIO`, `ROLE_AUXILIAR`, `ROLE_DUENO`).
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
| Lighthouse (mobile+desktop) | 4/4 categorías cumplen, SEO 100 |

Fuentes: ejecución real de `make all` sobre este repositorio;
[`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv);
[`docs/mediciones/sec/static-analysis/README.md`](docs/mediciones/sec/static-analysis/README.md);
[`docs/mediciones/sec/zap/README.md`](docs/mediciones/sec/zap/README.md);
[`docs/mediciones/sus/REPORT.md`](docs/mediciones/sus/REPORT.md);
[`docs/mediciones/lighthouse/`](docs/mediciones/lighthouse/) (`lhci-20260818-0538-*.json`).

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
| `v1.0.0` | **Entrega Final publicada** (commit `ba41e11`) |

---

## Despliegue

BIOPET está desplegado en producción sobre **Render**:

| Servicio | URL |
|---|---|
| Frontend | [https://biopet-frontend.onrender.com](https://biopet-frontend.onrender.com) |
| Backend | [https://biopet-backend-dh5e.onrender.com](https://biopet-backend-dh5e.onrender.com) |

Healthcheck verificado:

```bash
curl https://biopet-backend-dh5e.onrender.com/actuator/health
```

Respuesta real:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Infraestructura: Render, PostgreSQL 18, Valkey 8, HTTPS activo. Detalle
completo del despliegue (variables de entorno, Blueprint `render.yaml`,
pasos de verificación) en
[`docs/despliegue/DEPLOYMENT.md`](docs/despliegue/DEPLOYMENT.md). La
estrategia de reproducibilidad local (Docker, digests fijados por imagen)
está documentada en [`docs/adr/ADR-005-despliegue.md`](docs/adr/ADR-005-despliegue.md).

---

## GHCR

Imagen del backend publicada en GitHub Container Registry:

| Campo | Valor |
|---|---|
| Imagen | `ghcr.io/grinjoww/entregafinal-biopet-backend` |
| Tag publicado | `sha-fe2f033` |
| Digest (sha256) | `sha256:ef1e857a95a307a115ebe01599a41506eab824808b70a3c8e317dcc55bef5163` |

```bash
docker pull ghcr.io/grinjoww/entregafinal-biopet-backend@sha256:ef1e857a95a307a115ebe01599a41506eab824808b70a3c8e317dcc55bef5163
```

El tag Git `v1.0.0` ya fue publicado (commit `ba41e11`), lo que dispara
automáticamente (`.github/workflows/ghcr-publish.yml`, disparador
`push: tags: v*`) la publicación de las etiquetas `1.0.0` y `latest` en
GHCR. No hay evidencia local de que esa corrida de GitHub Actions ya haya
terminado: **publicación automática disparada por el tag `v1.0.0`**, sin
afirmar que las etiquetas `1.0.0`/`latest` ya estén visibles en el
registro hasta confirmarlo directamente en GHCR.

---

## DOI / Zenodo

- DOI de software: [10.5281/zenodo.21988746](https://doi.org/10.5281/zenodo.21988746)
- DOI del dataset: [10.5281/zenodo.21988785](https://doi.org/10.5281/zenodo.21988785)

---

## Lighthouse

Corrida final: **2026-08-18**, 12 corridas (perfiles móvil **y** desktop,
3 corridas por ruta/perfil), fuente
[`docs/mediciones/lighthouse/lhci-20260818-0538-*.json`](docs/mediciones/lighthouse/).
Las cuatro categorías cumplen su umbral:

| Categoría | Resultado |
|---|---|
| Performance | Cumple (100 en desktop, 90–94 en móvil) |
| Accessibility | Cumple (91) |
| Best Practices | Cumple (96–100) |
| SEO | Cumple (**100**) |

Nota histórica: la corrida inicial del 2026-08-01 (solo perfil móvil)
había registrado SEO = 82, por debajo del umbral de 90; los fixes
aplicados (`meta description`, `robots.txt`) se confirmaron efectivos en
la corrida final del 2026-08-18. `make lighthouse` reproduce la auditoría
(requiere `make up` previo); Lighthouse no está integrado en `make all` ni
en CI.

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

El tag `v1.0.0` fue publicado; [`CITATION.cff`](CITATION.cff) contiene los
metadatos de citación del software (versión, autores, licencia, DOI). El
DOI del software y del dataset ya están archivados en Zenodo (ver
[DOI / Zenodo](#doi--zenodo)). El campo `date-released` de `CITATION.cff`
todavía no tiene valor asignado; no se afirma aquí que ya esté completado.
