# Política de versionado — BIOPET

BIOPET adopta **Semantic Versioning 2.0.0** (SemVer) de forma estricta a partir de la
Tercera Entrega (v0.9.0-rc).

## Esquema

El número de versión sigue la forma `MAJOR.MINOR.PATCH[-PRERELEASE]`:

- **MAJOR**: se incrementa ante cambios incompatibles en la API pública
  (por ejemplo, un cambio de contrato en un endpoint REST existente que
  rompa a los clientes actuales, o una migración de base de datos que
  invalide datos previos).
- **MINOR**: se incrementa al agregar funcionalidad nueva compatible hacia
  atrás (por ejemplo, un módulo nuevo como historial clínico o citas,
  sección 3.3 del SRS, cuando se implemente).
- **PATCH**: se incrementa ante correcciones de errores compatibles hacia
  atrás que no agregan funcionalidad (por ejemplo, una corrección de un bug
  en `JwtService` sin cambiar el contrato del endpoint).
- **PRERELEASE** (sufijo `-rc`, `-alpha`, `-beta`): identifica una versión
  de pre-lanzamiento, no estable, como es el caso de `v0.9.0-rc` en esta
  entrega.

## Historial de versiones del proyecto

| Versión | Hito académico | Fecha | Descripción |
|---|---|---|---|
| v0.3.0 | Entrega 1A | 04-jun-2026 | Corpus inicial de requisitos, diseño arquitectónico C4 nivel 1 y 2, stack propuesto ASP.NET Core 8 (ADR-001), esqueleto ejecutable con Docker. |
| v0.7.0 | Entrega 1B | semana del 14-jun-2026 | Primer módulo funcional: autenticación JWT stateless, CRUD de Mascota con Spring Data JPA, migración real de la pila a Java 21 / Spring Boot 3.2 (ADR-002), Flyway, Redis, Angular. |
| v0.7.1 | Cierre de observaciones (Bloque 0) | previa a la Tercera Entrega | Etiqueta intermedia sobre el commit que cierra la aplicación de observaciones de las Entregas 1A y 1B, conforme al bloque 0 de la Guía. **Pendiente de creación**: no se encontró evidencia de esta etiqueta ni de `docs/observaciones/OBSERVACIONES.md` en el repositorio provisto (ver sección 7 del SRS). |
| v0.9.0-rc | Tercera Entrega | 24-jul-2026 | Estado release candidate: consolidación de requisitos, reproducibilidad automática, evidencia empírica preliminar y trazabilidad, conforme a esta guía. |
| v1.0.0 | Entrega Final | semana del 17-ago-2026 | Sistema estable, cobertura JaCoCo ≥ 70 %, umbrales k6/Lighthouse cumplidos, auditoría OWASP completa. |

## Reglas de aplicación

- El tag Git de cada entrega debe coincidir exactamente con la versión
  declarada (por ejemplo, el tag de esta entrega es `v0.9.0-rc`, sin
  variaciones).
- Toda versión de pre-lanzamiento (`-rc`, `-alpha`, `-beta`) se considera
  inestable y no debe usarse como referencia para citar el software de forma
  permanente; para eso se usa el DOI de Zenodo asociado al tag correspondiente
  (bloque E.2 de la Guía).
- El cambio de MAJOR, MINOR o PATCH debe reflejarse en `CHANGELOG.md`
  siguiendo la convención Keep a Changelog, con secciones Added, Changed,
  Deprecated, Removed, Fixed y Security.
- Los mensajes de commit siguen Conventional Commits (`feat:`, `fix:`,
  `docs:`, `chore:`, `refactor:`, `test:`, `perf:`) para permitir la
  generación automática del historial de cambios, conforme al bloque E.4 de
  la Guía.
- Ninguna decisión arquitectónica mayor debe posponerse a la Entrega Final:
  si un cambio de versión MAJOR requiere reescribir un ADR o refactorizar un
  módulo, debe documentarse dentro del mismo ciclo de entrega en que ocurre
  (ver `docs/adr/`).


