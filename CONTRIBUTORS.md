# Contribuciones — BIOPET

Este archivo declara las contribuciones de cada integrante del equipo usando
la taxonomía **CRediT (Contributor Roles Taxonomy)**.

## Equipo

- **Beltrán Montiel, Fred Adrián** — Universidad Técnica Estatal de Quevedo — `fbeltranm@uteq.edu.ec`
- **Mariscal Cabrera, Jaime Josué** — Universidad Técnica Estatal de Quevedo — `jmariscalc@uteq.edu.ec`
- **Taipe Mora, Zaida Melissa** — Universidad Técnica Estatal de Quevedo — `ztaipem@uteq.edu.ec`

Docente responsable (no contribuyente del software, rol de evaluación):
Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.

## Matriz de roles CRediT (individual)

El repositorio conserva su historial de Git completo (270+ commits, con
autoría diferenciada por los tres integrantes, verificable con
`git shortlog -sne --all` y `git log --author=...`); esta tabla asigna cada
rol CRediT **por persona**, exclusivamente cuando existe evidencia real y
verificable de que la ejerció — commits, ramas de trabajo (`fred/*`,
`jaime/*`, `zaida/*`) y archivos concretos del repositorio. No se atribuye
un rol a una persona sin esa evidencia; donde no se encontró evidencia
diferenciada, la celda queda en blanco (`—`) para esa persona, en vez de
marcarla por defecto.

| Rol CRediT | Beltrán Montiel, F. | Mariscal Cabrera, J. | Taipe Mora, Z. | Evidencia |
|---|:---:|:---:|:---:|---|
| Conceptualization | ✔ | ✔ | ✔ | Alcance y objetivo del producto definidos en conjunto en el SRS (secciones 1.1–1.2), sin diferenciación de autoría por sección. |
| Data curation | ✔ | ✔ | — | Fred: rutinas y catálogo de acceso a datos (`db/procs/`, `docs/basedatos/CATALOGOSP.md`, migración `V5__procedimientos_biopet.sql`, rama `fred/f01-f05-sp-acceso-datos`). Jaime: `docs/mediciones/DATA-DICTIONARY.md` (rama `jaime/data-dictionary`) y la formalización JPA/Flyway `V6__formalizar_procedimientos_jpa.sql`. |
| Formal analysis | — | ✔ | ✔ | Zaida: redacción y consolidación de requisitos (`docs/requisitos/SRS.md`, `HistoriasUsuario.md`, `CasosDeUso.md`, `CHANGELOG-REQ.md`). Jaime: verificación de requisitos contra el código real y cierre de observaciones de completitud (`docs/observaciones/OBSERVACIONES.md`, OBS-02/OBS-03). |
| Funding acquisition | No aplica | No aplica | No aplica | No se encontró evidencia de financiamiento externo; proyecto exclusivamente académico. |
| Investigation | ✔ | ✔ | ✔ | Fred: mediciones de rendimiento y FAIR (`docs/mediciones/perf/`, `docs/checklists/fair.md`). Jaime: verificación de requisitos vs. código y auditoría de la bitácora de observaciones. Zaida: encuesta de usabilidad SUS (`docs/mediciones/sus/`) y búsqueda de trabajos relacionados (PRISMA). |
| Methodology | ✔ | ✔ | ✔ | Fred: análisis estadístico de las corridas k6 (IC95% t de Student, Wilcoxon pareado, tamaño de efecto — `docs/mediciones/perf/REPORT.md`). Jaime: selección y aplicación del estándar Ralph et al. (`docs/checklists/ralph2021-engineering-research.md`). Zaida: adopción de INCOSE Guide y PRISMA 2020 (`docs/checklists/incose2023-req.md`, `prisma2020.md`). |
| Project administration | ✔ | ✔ | ✔ | Sin evidencia de un rol de gestión diferenciado por persona; coordinación compartida mediante ramas de trabajo individuales (`fred/*`, `jaime/*`, `zaida/*`) integradas por pull request. |
| Resources | ✔ | ✔ | — | Fred: infraestructura de despliegue (`render.yaml`, `docker-compose.prod.yml`, `docs/despliegue/`). Jaime: entorno Docker/CI del repositorio (`docker-compose.yml`, `.github/workflows/ci.yml`, `Makefile`). |
| Software | ✔ | ✔ | ✔ | Fred: implementación inicial del backend y de las rutinas almacenadas. Jaime: backend Spring Boot (controladores, servicios, seguridad) y la formalización del acceso JPA a las 6 rutinas PostgreSQL (`ProcedimientoBiopetRepository.java`, migración V6). Zaida: frontend Angular (componentes, formularios, accesibilidad). |
| Supervision | — | — | — | Ejercida por el docente responsable (Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.), no por integrantes del equipo. |
| Validation | ✔ | ✔ | ✔ | Fred: corridas de rendimiento k6 (5 en frío + 5 en caliente, `docs/mediciones/perf/`). Jaime: pruebas automatizadas, JaCoCo, auditoría SQL, SpotBugs/Find Security Bugs y OWASP ZAP (`Backend/src/test`, `docs/mediciones/sec/`). Zaida: encuesta SUS con 18 participantes (`docs/mediciones/sus/REPORT.md`). |
| Visualization | ✔ | — | ✔ | Fred: gráfico de latencias de las corridas k6 (`docs/mediciones/perf/grafico.svg`). Zaida: diagramas C4 y DER (`docs/diagrams/`). |
| Writing – original draft | ✔ | ✔ | ✔ | Fred: documentación de despliegue y checklist FAIR (`docs/despliegue/`, `docs/checklists/fair.md`). Jaime: borradores del informe técnico y README (`docs/informe/borradores/jaime/`). Zaida: redacción del SRS (v0.3.0 a v0.9.0-rc). |
| Writing – review & editing | ✔ | ✔ | ✔ | Fred: revisión de secciones del informe técnico (`docs/informe/`). Jaime: consolidación de cambios del SRS (`docs/requisitos/cambios/CAMBIOS-SRS.md`) y cierre de la bitácora de observaciones. Zaida: revisión de secciones del informe técnico (`docs/informe/`). |

**Cómo se verificó esta tabla:** cada celda se contrastó contra archivos
reales del repositorio y contra la autoría de commits real
(`git shortlog -sne --all`, `git log --all --author=<correo> --name-only`),
no contra memoria ni supuestos. No se marcó un rol para una persona sin al
menos un archivo o rama de trabajo concreto que lo respalde.
