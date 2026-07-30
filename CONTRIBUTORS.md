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

Los 14 roles de la taxonomía CRediT se aplican a nivel de equipo porque el
repositorio provisto no incluye historial de Git (`.git/`) ni metadatos de
autoría por commit que permitan atribuir de forma verificable un rol a una
persona específica en particular. Los tres integrantes figuran como coautores
del SRS, del informe de la Entrega 1A y del código entregado en los ZIP
provistos, sin distinción de autoría por archivo o módulo. En consecuencia,
esta tabla asigna cada rol al equipo completo cuando hay evidencia documental
de que se ejerció, y lo marca como **No aplica** cuando no se encontró
evidencia de que la actividad se haya realizado en este proyecto.

| Rol CRediT | Beltrán Montiel, F. | Mariscal Cabrera, J. | Taipe Mora, Z. | Evidencia |
|---|:---:|:---:|:---:|---|
| Conceptualization | ✔ | ✔ | ✔ | Alcance y objetivo del producto definidos en el SRS (secciones 1.1–1.2) y en el README de la Entrega 1A. |
| Data curation | ✔ | ✔ | ✔ | Diseño y mantenimiento del diccionario de datos de `usuarios` y `mascotas` (SRS, sección 5.1) y de la migración Flyway `V1__schema_inicial.sql`. |
| Formal analysis | ✔ | ✔ | ✔ | Redacción de requisitos funcionales y no funcionales según ISO/IEC/IEEE 29148:2018 (SRS, sección 3). |
| Funding acquisition | No aplica | No aplica | No aplica | No se encontró evidencia de financiamiento externo en los documentos provistos; proyecto exclusivamente académico. |
| Investigation | ✔ | ✔ | ✔ | Verificación de requisitos contra el código real descrita en `CAMBIOS-SRS.md` (sección "Requisitos funcionales"). |
| Methodology | ✔ | ✔ | ✔ | Adopción del patrón `[condición][sujeto] shall [acción]` de ISO/IEC/IEEE 29148:2018 y de MoSCoW para priorización (SRS, sección 3). |
| Project administration | ✔ | ✔ | ✔ | No se encontró evidencia de un rol de gestión de proyecto diferenciado por persona; se asume gestión compartida propia de un equipo de tres integrantes en un PFC académico. |
| Resources | ✔ | ✔ | ✔ | Provisión del entorno de desarrollo, contenedores Docker y dependencias declaradas en `Backend/pom.xml` y `docker-compose.yml`. |
| Software | ✔ | ✔ | ✔ | Implementación del backend Spring Boot (`AuthController`, `MascotaController`, `JwtService`, etc.) y del frontend Angular, verificada en el repositorio v0.9.0-rc. |
| Supervision | — | — | — | Ejercida por el docente responsable (Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.), no por integrantes del equipo. |
| Validation | ✔ | ✔ | ✔ | Pruebas automatizadas JUnit 5 + MockMvc (`AuthControllerTest`, `MascotaControllerTest`, `JwtServiceTest`) presentes en `Backend/src/test`. |
| Visualization | ✔ | ✔ | ✔ | Diagramas C4, DER, de clases y de secuencia de la Entrega 1B referenciados en `CAMBIOS-SRS.md` (sección "Arquitectura"). |
| Writing – original draft | ✔ | ✔ | ✔ | Redacción del SRS v0.3.0 (Entrega 1A) y de su actualización a v0.9.0-rc. |
| Writing – review & editing | ✔ | ✔ | ✔ | Consolidación y revisión del SRS v0.9.0-rc documentada en `CAMBIOS-SRS.md`. |

