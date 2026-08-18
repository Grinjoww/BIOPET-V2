# Checklist FAIR — BIOPET (F17)

Evaluación ítem por ítem de los principios FAIR (Findable, Accessible,
Interoperable, Reusable) para el software de BIOPET, **enlazando SOLO
evidencia real ya existente** en el repositorio o en el despliegue. Los ítems
que dependen de un DOI/URL que aún no existe se marcan explícitamente como
**pendiente de publicación** — no se inventa ningún enlace.

Fecha de evaluación: 2026-08-18. Rama: `fred/f17-f19-fair-software-provenance`.
Referencia del estándar: FAIR Principles for Research Software (FAIR4RS,
Barker et al. 2022) — mapeo sobre la guía de publicación del proyecto.

---

## F — Findable (Encontrable)

### F1. Identificador persistente único (PID)
- **Estado: cumplido.**
- DOI real asignado por **Zenodo**:
  - Software: [`10.5281/zenodo.21988746`](https://doi.org/10.5281/zenodo.21988746)
  - Dataset (evidencias empíricas): [`10.5281/zenodo.21988785`](https://doi.org/10.5281/zenodo.21988785)
- Evidencia: `CITATION.cff` (bloque `identifiers`), `README.md` (sección
  "DOI / Zenodo"), `docs/observaciones/OBSERVACIONES.md` (cierre de OBS-10).

### F2. Metadatos ricos
- **Estado: cumplido (parcialmente, ver F1 para DOI).**
- `CITATION.cff` (v1.2.0): título, tipo `software`, 3 autores con afiliación
  (UTEQ), versión `0.9.0-rc`, fecha, licencia MIT, `repository-code`, 8
  keywords. Ruta: `CITATION.cff`.
- **Hallazgo real**: el campo `repository-code` apunta a
  `https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc` (repo anterior)
  y la versión es `0.9.0-rc`; para v1.0.0 debe actualizarse a
  `https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET` y `version: 1.0.0`
  (pendiente de edición en F18/paquete de release).

### F3. Los metadatos incluyen el identificador del software
- **Estado: cumplido** — el DOI (`10.5281/zenodo.21988746`) ya está en
  `CITATION.cff` y `README.md`; pendiente únicamente reflejarlo en la
  portada del informe final (fuera de alcance de este checklist).

### F4. Metadatos registrados/buscables en infraestructura indexable
- **Estado: cumplido** — Zenodo indexa el DOI ya asignado; GitHub ya
  indexa el repo (búsqueda de "BIOPET" en github.com encuentra el
  repositorio actual).

---

## A — Accessible (Accesible)

### A1. Protocolo estándar, abierto y gratuito para obtener los datos/software
- **Estado: cumplido (código) / pendiente (deploy público).**
- Código: `https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET` (HTTPS,
  repo público, protocolo Git estándar). Evidencia: remoto verificado con
  `git remote -v`.
- Despliegue: el backend de producción estará en
  `https://biopet-backend.onrender.com` (HTTPS válido emitido por Render),
  healthcheck `GET /actuator/health`. **Pendiente de publicación**: la URL
  real la crea el dueño del repo al conectar Render (ver DEPLOYMENT.md,
  `docs/despliegue/DEPLOYMENT.md`, sección 5). No se inventa la URL aquí.

### A1.1 El protocolo permite autenticación/autorización cuando es necesario
- **Estado: cumplido** — el backend exige JWT (cookies `HttpOnly`+`Secure`+
  `SameSite=Strict`) para todos los endpoints protegidos, verificado en
  `docs/mediciones/sec/A07-authentication.md` y `docs/mediciones/sec/A01-access-control.md`.

### A1.2 Los metadatos son accesibles incluso si el software no lo es
- **Estado: cumplido** — `CITATION.cff`, `README.md` y los ADR
  (`docs/adr/`) están versionados y accesibles vía GitHub sin necesidad de
  desplegar el sistema.

### A2. Los metadatos persisten más allá de la vida del software
- **Estado: cumplido** — el historial git del repo persiste; la
  persistencia formal ya queda cubierta por el archivado real en Zenodo
  (F1, DOI `10.5281/zenodo.21988746`).

---

## I — Interoperable (Interoperable)

### I1. Vocabularios formales, estándares abiertos
- **Estado: cumplido** — evidencia real:
  - API documentada con **OpenAPI/Swagger** (`/api/openapi`, `application.yml`
    líneas 53-57: `springdoc.api-docs.path: /api/openapi`).
  - Esquema SQL gestionado por **Flyway** (`Backend/src/main/resources/db/migration/V1__schema_inicial.sql`,
    `V5__procedimientos_biopet.sql`) + `db/schema.sql`.
  - Colecciones **Postman** ejecutables: `docs/postman/*.postman_collection.json`.
  - Formatos de datos: JSON (API), CSV (`docs/trazabilidad/matriz.csv`), JSON
    de k6 (`docs/mediciones/perf/k6-20260817T*.json`), SVG (`grafico.svg`).

### I2. Uso de modelos/vocabularios FAIR
- **Estado: parcial** — los metadatos siguen los estándares del proyecto
  (CITATION.cff v1.2.0, formato Nygard en ADR); no se usan ontologías
  externas (no requerido por la guía; se documenta como limitación
  consciente).

### I3. Referencias calificadas entre metadatos
- **Estado: cumplido** — evidencia real:
  - `CITATION.cff` → `repository-code` (remoto GitHub).
  - Matriz de trazabilidad (`docs/trazabilidad/matriz.csv`) liga cada
    requisito a módulo, endpoint y evidencia empírica.
  - ADR referencian archivos de respaldo y otros ADR
    (p.ej. `docs/adr/ADR-007-acceso-datos.md` → CATALOGOSP.md).

---

## R — Reusable (Reusable)

### R1. Metadatos ricos con licencia clara
- **Estado: cumplido** — `LICENSE` = **MIT**, Copyright (c) 2026 Equipo BIOPET
  (los 3 integrantes nombrados), referenciado en `CITATION.cff`
  (`license: MIT`).

### R1.1 Licencia clara y accesible
- **Estado: cumplido** — archivo `LICENSE` en la raíz del repo (21 líneas,
  texto MIT estándar), detectado por GitHub (widget de licencia del repo).

### R1.2 Provenance documentada
- **Estado: cumplido (parcial)** — el handoff de provenance
  `docs/mediciones/handoff-fred-provenance.md` (F19, esta rama) documenta la
  cadena raw → script → figura → commit para los bloques de Fred (k6,
  SP/BD, despliegue, entorno). La consolidación final en
  `DATA-PROVENANCE.md` es tarea de Zaida (Z12).

### R1.3 Cumplimiento de estándares de la comunidad
- **Estado: cumplido** — checklists existentes:
  - `docs/checklists/ralph2021-engineering-research.md` (estándar empírico).
  - `docs/checklists/prisma2020.md` y `docs/checklists/incose2023-req.md`.
  - CI con 6 jobs en `.github/workflows/ci.yml` (tests 189+, JaCoCo ≥70%,
    SpotBugs, ZAP, auditoría SQL).

### R2. Repositorio documentado
- **Estado: cumplido** — `README.md` (raíz), `docs/despliegue/DEPLOYMENT.md`,
  `docs/despliegue/RUNBOOK.md`, `docs/despliegue/BACKUP.md`, `docs/entorno/versions.txt`.

### R3. Cómo citar
- **Estado: cumplido** — `CITATION.cff` con `message: "Si utiliza este
  software, cítelo usando los metadatos de este archivo."` y formato de
  autores/apellidos listo para Zenodo.

---

## Resumen de estado

| Principio | Ítems cumplidos | Ítems pendientes de publicación |
|---|---|---|
| **F** Findable | F1 (DOI Zenodo), F2 (parcial), F3, F4 | — |
| **A** Accessible | A1 (código), A1.1, A1.2, A2 (Zenodo) | A1 (URL Render real) |
| **I** Interoperable | I1, I3 | I2 (parcial, consciente) |
| **R** Reusable | R1, R1.1, R1.2 (parcial), R1.3, R2, R3 | — |

**DOI de Zenodo ya asignado** (F1): software
`10.5281/zenodo.21988746`, dataset `10.5281/zenodo.21988785`. El único
pendiente real que queda en F2 es puntual: `CITATION.cff` seguía
declarándose ahí con `version: 0.9.0-rc` y el `repository-code` anterior
en la fecha de esta evaluación (2026-08-18) — ya corregido por separado
en `docs/publicacion/PAQUETE-V1.0.0.md` (F18); no se actualizó la
redacción completa de F2 en esta pasada porque el alcance de este cambio
fue exclusivamente el estado Zenodo/DOI.

## Referencias

- CITATION.cff (v1.2.0) — `CITATION.cff`
- LICENSE (MIT) — `LICENSE`
- README — `README.md`
- Deployment — `docs/despliegue/DEPLOYMENT.md`, `RUNBOOK.md`, `BACKUP.md`
- Mediciones — `docs/mediciones/perf/REPORT.md`, `docs/mediciones/sec/`
- Entorno/versiones — `docs/entorno/versions.txt`