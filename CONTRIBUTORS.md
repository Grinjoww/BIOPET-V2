# Contribuciones — BIOPET

Este archivo declara las contribuciones de cada integrante del equipo usando
la taxonomía **CRediT (Contributor Roles Taxonomy)**

## Equipo

- **Beltrán Montiel, Fred Adrián** — Universidad Técnica Estatal de Quevedo
- **Mariscal Cabrera, Jaime Josué** — Universidad Técnica Estatal de Quevedo
- **Taipe Mora, Zaida Melissa** — Universidad Técnica Estatal de Quevedo

Docente responsable (no contribuyente del software, rol de evaluación):
Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.

## Matriz de roles CRediT

Este repositorio **sí conserva su historial de Git** (170 commits entre
2026-07-30 y 2026-08-17, ramas de los tres integrantes fusionadas a `main`),
por lo que la atribución de roles no se hace únicamente por declaración del
equipo sino contrastada contra la autoría real de los commits (`git log
--author`) y las rutas de archivo que cada integrante modificó
(`Backend/`, `frontend/`, `db/`, `docs/`, `scripts/`, `k6/`). Los tres
integrantes originales participaron en Backend, frontend y documentación;
las columnas siguientes distinguen dónde tuvo cada uno mayor peso relativo
según el volumen de commits, sin implicar que el resto de roles no haya
sido compartido.

Nota: los commits de Johan Carvajal y Michael Fajardo (grupo de GA de
Unidad IV) corresponden a documentación de revisión cruzada, no al
desarrollo del software BIOPET, y por eso no figuran en esta matriz (ver
sección "Autoría e historial del repositorio" del README).

| Rol CRediT | Beltrán Montiel, F. | Mariscal Cabrera, J. | Taipe Mora, Z. | Evidencia |
|---|:---:|:---:|:---:|---|
| Conceptualization | ✔ | ✔ | ✔ | Alcance y objetivo del producto definidos en el SRS (secciones 1.1–1.2) y en el README de la Entrega 1A. |
| Data curation | ✔ | ✔ | ✔ | Historial de commits sobre `db/` (migraciones Flyway, `V1__schema_inicial.sql`, `db/roles.sql`) y el diccionario de datos (SRS, sección 5.1), con aportes registrados de los tres integrantes. |
| Formal analysis | ✔ | ✔ | ✔ | Redacción de requisitos funcionales y no funcionales según ISO/IEC/IEEE 29148:2018 (SRS, sección 3). |
| Funding acquisition | No aplica | No aplica | No aplica | No se encontró evidencia de financiamiento externo en los documentos provistos; proyecto exclusivamente académico. |
| Investigation | ✔ | ✔ | ✔ | Verificación de requisitos contra el código real descrita en `CAMBIOS-SRS.md` (sección "Requisitos funcionales"). |
| Methodology | ✔ | ✔ | ✔ | Adopción del patrón `[condición][sujeto] shall [acción]` de ISO/IEC/IEEE 29148:2018 y de MoSCoW para priorización (SRS, sección 3). |
| Project administration | ✔ | ✔ | ✔ | Coordinación distribuida entre los tres integrantes, visible en la cadencia de merges a `main` a lo largo del historial de commits; sin un único responsable exclusivo de gestión. |
| Resources | ✔ | ✔ | ✔ | Provisión del entorno de desarrollo, contenedores Docker y dependencias declaradas en `Backend/pom.xml` y `docker-compose.yml`. |
| Software | ✔ | ✔ | ✔ | Implementación del backend Spring Boot y del frontend Angular; los tres integrantes registran commits en `Backend/` y `frontend/` (mayor volumen de Fred Beltrán en `Backend/`, según `git log --author`). |
| Supervision | — | — | — | Ejercida por el docente responsable (Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.), no por integrantes del equipo. |
| Validation | ✔ | ✔ | ✔ | Pruebas automatizadas JUnit 5 + MockMvc (`AuthControllerTest`, `MascotaControllerTest`, `JwtServiceTest`) presentes en `Backend/src/test`. |
| Visualization | ✔ | ✔ | ✔ | Diagramas C4, DER, de clases y de secuencia referenciados en `CAMBIOS-SRS.md` (sección "Arquitectura"); mayor volumen de commits en `docs/` de Jaime Mariscal, según `git log --author`. |
| Writing – original draft | ✔ | ✔ | ✔ | Redacción del SRS v0.3.0 (Entrega 1A) y de su actualización a v0.9.0-rc. |
| Writing – review & editing | ✔ | ✔ | ✔ | Consolidación y revisión del SRS v0.9.0-rc documentada en `CAMBIOS-SRS.md`. |

