# CHANGELOG-REQ.md

Registro de cambios a los requisitos del SRS de BIOPET desde la Entrega 1A,
siguiendo la convención Keep a Changelog adaptada a requisitos (bloque A.3.4
de la Guía de la Tercera Entrega). No reemplaza a `docs/requisitos/cambios/CAMBIOS-SRS.md`
(que narra el contexto general de la migración de pila ASP.NET → Spring Boot);
este archivo es el registro formal, fila por requisito, exigido por A.3.4.

## [v0.9.0-rc] - 2026-07-31 (rama `jaime/cierre-observaciones-1a-1b`)

Cierre de las observaciones de requisitos de la Entrega 1A (OBS-02, OBS-03,
OBS-04), registradas en `docs/observaciones/OBSERVACIONES.md` a partir de la
retroalimentación oficial del SGA.

### Added
- **REQ-F-022** — Notificaciones al usuario por correo electrónico. Cierra
  **OBS-02** (ausencia de RF-07 en la lista consolidada de la Entrega 1A).
  RF-07 correspondía al "Servicio de Correos" ya documentado como sistema
  externo en `docs/diagrams/c4-contexto/C4-L1-contexto.md`, pero nunca
  formalizado como requisito `REQ-F`. Se numera 022 (no 007, ya ocupado por
  "Consulta del perfil propio") para no duplicar identificadores. Estado:
  pendiente (no implementado). Historia `HU-021`, caso de uso `CU-21`.
  Autor: Jaime Josué Mariscal Cabrera.
- **Sección 4.1 del SRS — Trazabilidad histórica RF/RF-WEB → REQ-F**. Cierra
  **OBS-03** (remapeo de RF-WEB a RF-16/RF-17 sin matriz de trazabilidad
  explícita). Consolida en una tabla estructurada (identificador anterior,
  identificador actual, descripción, caso de uso, historia, estado) el
  vínculo que antes solo existía disperso en el campo Rationale de cada
  requisito individual. No modifica `docs/trazabilidad/matriz.csv` (fuera
  del alcance de archivos autorizados para este cierre); queda como acción
  de seguimiento agregar allí una columna equivalente de origen histórico.
  Autor: Jaime Josué Mariscal Cabrera.

### Changed
- **REQ-F-017** — Recomendaciones clínicas informativas. Cierra **OBS-04**
  (ambigüedad leve señalada por el docente en la redacción original de
  RF-10, "recomendaciones informativas"). Se reemplaza el resumen de una
  sola línea por un bloque completo en patrón "El sistema deberá...", con
  entradas, resultado esperado y tres criterios de aceptación verificables.
  No cambia el identificador, la prioridad (`Could`) ni el estado
  (`pendiente`); no se agrega funcionalidad nueva. Autor: Jaime Josué
  Mariscal Cabrera.

### Fixed
- Corrección factual en este mismo changelog: la entrada `[v0.9.0-rc] -
  2026-07-30` de `REQ-F-021` citaba `HU-021`/`CU-021` como su historia y
  caso de uso asociados. La fuente de verdad real
  (`docs/requisitos/historias/HistoriasUsuario.md`,
  `docs/requisitos/casos-de-uso/CasosDeUso.md` y la tabla de correspondencia
  de la sección 4 del SRS) siempre asignó `HU-020`/`CU-20` a `REQ-F-021`; se
  corrige la entrada de abajo para no chocar con `HU-021`/`CU-21`, que a
  partir de esta revisión sí identifican a `REQ-F-022`. Autor: Jaime Josué
  Mariscal Cabrera.

## [v0.9.0-rc] - 2026-07-30

### Added
- **REQ-F-021** — Resumen de mascotas por especie. El endpoint
  `GET /api/mascotas/resumen-especies` ya estaba implementado en el backend
  (función `fn_resumen_mascotas_por_especie`, catalogada en
  `docs/basedatos/CATALOGO-SP.md`) pero no tenía requisito formal en el SRS.
  Se agrega ahora con su historia `HU-020` y caso de uso `CU-20`
  (corregido; ver entrada `Fixed` del 2026-07-31 de más arriba).
  Autor: Zaida Melissa Taipe Mora.

### Changed
- **REQ-NF-007** (diseño responsivo) — cambia de estado "pendiente de
  evidencia empírica" a "implementado". Se agregó una cuadrícula responsive
  (`grid-mascotas` con media query en `styles.css`) y estados de foco visibles
  (`:focus-visible`) en el frontend. Sigue pendiente la corrida formal de
  Lighthouse para pasar a "verificado".
  Autor: Zaida Melissa Taipe Mora.

### Fixed
- Los flujos de error del frontend (login y mascotas) ya interpretan
  `ProblemDetail` para los códigos 400, 401, 403, 404, 409, 422 y 429,
  incluyendo el desglose por campo del 422 (`errors`). Antes de este cambio,
  el 409 caía en un mensaje genérico no diferenciado.
  Autor: Zaida Melissa Taipe Mora.

## [v0.7.0] - 2026-06-14

- Ver `docs/requisitos/cambios/CAMBIOS-SRS.md` para el detalle completo de
  la consolidación de requisitos realizada en esta entrega (migración de
  esquema RF-NN a REQ-F-NNN, separación implementados/pendientes, etc.).
