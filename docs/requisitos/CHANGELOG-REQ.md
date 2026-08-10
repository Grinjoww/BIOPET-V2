# CHANGELOG-REQ.md

Registro de cambios a los requisitos del SRS de BIOPET desde la Entrega 1A,
siguiendo la convención Keep a Changelog adaptada a requisitos (bloque A.3.4
de la Guía de la Tercera Entrega). No reemplaza a `docs/requisitos/cambios/CAMBIOS-SRS.md`
(que narra el contexto general de la migración de pila ASP.NET → Spring Boot);
este archivo es el registro formal, fila por requisito, exigido por A.3.4.

## [v0.9.0-rc] - 2026-08-10 (reconciliación GA Unidad IV)

Corrige el fallo del CI de trazabilidad (`CI BIOPET / traceability`, job
`scripts/validate-traceability.sh`, bloque A.3.3 de la Guía), causado por
20 identificadores `REQ-F-023` a `REQ-F-042` agregados a
`docs/trazabilidad/matriz.csv` sin correspondencia en el SRS, fragmentando
funcionalidades completas (Citas, Consultas, Vacunas, Usuarios, API
externa) en un requisito por endpoint HTTP en lugar de un requisito por
módulo.

### Added
- **REQ-F-023** — Gestión administrativa de usuarios (`UsuarioController`
  CRUD, excluye `/api/usuarios/me`, ya cubierto por `REQ-F-007`). Historia
  `HU-022`, caso de uso `CU-22`. Estado: verificado
  (`UsuarioControllerTest`). Autor: Jaime Josué Mariscal Cabrera.
- **REQ-F-024** — Gestión de vacunas (`VacunaController`/`VacunaService`,
  registro, consulta global/por mascota/por id, actualización y baja
  lógica). Historia `HU-023`, caso de uso `CU-23`. Estado: verificado
  (`VacunaControllerTest`, colección Postman
  `docs/postman/BIOPET-Vacunas.postman_collection.json`), con la
  salvedad documentada de que `GET /api/vacunas/mascota/{mascotaId}` no
  tiene prueba automatizada dedicada. Autor: Jaime Josué Mariscal Cabrera.
- **REQ-F-025** — Consulta de información externa de especies
  (`ExternalApiController`/`ExternalApiService`, integración con API
  Ninjas y caché Redis *cache-aside*). Historia `HU-024`, caso de uso
  `CU-24`. Estado: implementado (sin prueba automatizada dedicada;
  evidencia empírica manual en
  `docs/u4/evidencias/fred/redis-cache-comparacion.md`). Autor: Jaime
  Josué Mariscal Cabrera.
- Historias `HU-022`, `HU-023`, `HU-024` en `HistoriasUsuario.md` y casos
  de uso `CU-22`, `CU-23`, `CU-24` en `CasosDeUso.md`, correspondientes a
  los tres requisitos anteriores.

### Changed
- **REQ-F-013** (historial clínico / Consultas) — la fila de
  `matriz.csv` ahora consolida las 5 operaciones reales de
  `ConsultaController` (`GET`, `GET /{id}`, `POST`, `PUT`, `DELETE`) en un
  único identificador, en vez de fragmentarlas en `REQ-F-013` y
  `REQ-F-027` a `REQ-F-030`. No cambia el enunciado del requisito en el
  SRS. Se preserva, sin ocultarla, la limitación real ya detectada: el
  listado no filtra por propietario para `ROLE_DUENO`, y el listado y
  `PUT` no tienen prueba automatizada dedicada; por eso el estado se
  mantiene en "implementado", no "verificado". Autor: Jaime Josué
  Mariscal Cabrera.
- **REQ-F-015** (gestión de citas) — la fila de `matriz.csv` ahora
  consolida las 5 operaciones reales de `CitaController` en un único
  identificador, en vez de fragmentarlas en `REQ-F-015` y `REQ-F-023` a
  `REQ-F-026`. No cambia el enunciado del requisito en el SRS. El backend
  está completamente probado (`CitaControllerTest`), pero como el
  enunciado formal de `HU-014`/`REQ-F-015` exige un "calendario
  interactivo" que no existe en el frontend, el estado se mantiene en
  "implementado", no "verificado", para no certificar como cumplido un
  requisito que solo se satisface parcialmente. Autor: Jaime Josué
  Mariscal Cabrera.

### Removed
- **`REQ-F-023` a `REQ-F-042`** (numeración anterior, 20 identificadores)
  — eliminados de `docs/trazabilidad/matriz.csv` por no existir en el SRS.
  Ninguna evidencia técnica se perdió: la trazabilidad de Citas y
  Consultas se conservó consolidada en `REQ-F-015`/`REQ-F-013`; la de
  Usuarios, Vacunas y API externa se conservó formalizándola en los
  nuevos `REQ-F-023`, `REQ-F-024` y `REQ-F-025` de esta misma entrada.
  Autor: Jaime Josué Mariscal Cabrera.

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
