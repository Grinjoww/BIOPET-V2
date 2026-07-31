# CHANGELOG-REQ.md

Registro de cambios a los requisitos del SRS de BIOPET desde la Entrega 1A,
siguiendo la convención Keep a Changelog adaptada a requisitos (bloque A.3.4
de la Guía de la Tercera Entrega). No reemplaza a `docs/requisitos/cambios/CAMBIOS-SRS.md`
(que narra el contexto general de la migración de pila ASP.NET → Spring Boot);
este archivo es el registro formal, fila por requisito, exigido por A.3.4.

## [v0.9.0-rc] - 2026-07-30

### Added
- **REQ-F-021** — Resumen de mascotas por especie. El endpoint
  `GET /api/mascotas/resumen-especies` ya estaba implementado en el backend
  (función `fn_resumen_mascotas_por_especie`, catalogada en
  `docs/basedatos/CATALOGO-SP.md`) pero no tenía requisito formal en el SRS.
  Se agrega ahora con su historia `HU-021` y caso de uso `CU-021`.
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
