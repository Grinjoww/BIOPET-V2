# ADR-002: Migración de la pila tecnológica del backend de ASP.NET Core 8 a Java 21 / Spring Boot 3.2

## Identificador
ADR-002

## Título
Elección (por migración) de la pila tecnológica del backend: Java 21 + Spring Boot 3.2

## Estado
Aceptado — decisión ya implementada y en producción de desarrollo desde la Entrega 1B (v0.7.0). Este documento formaliza retroactivamente una decisión que el código ya refleja, cerrando la observación abierta registrada en la sección 7 del SRS v0.9.0-rc y en `CAMBIOS-SRS.md`.

**Supersede a:** ADR-001 (*Selección del lenguaje de programación del servidor*, Entrega 1A, `docs/ADR/ADR-001-tecnologia.md`), que seleccionó ASP.NET Core 8. ADR-001 no se modifica ni se elimina de su repositorio original — se conserva como registro histórico de la decisión inicial — pero su decisión deja de regir el proyecto a partir de este documento.

## Fecha
- Decisión original (ASP.NET Core 8, ahora superada): junio de 2026 (Entrega 1A, ADR-001).
- Migración efectiva a Java 21 / Spring Boot 3.2: Entrega 1B (semana del 14 de junio de 2026), según el control de versiones del SRS y el `README.md` del proyecto v0.9.0-rc.
- Formalización de este ADR: Tercera Entrega (24 de julio de 2026).

## Contexto
En la Entrega 1A (v0.3.0) el equipo seleccionó ASP.NET Core 8 como lenguaje/framework del backend (ADR-001), fundamentado en la afinidad del equipo con C# y en el rendimiento esperado para APIs REST con manejo asíncrono nativo.

Durante la Entrega 1B, el equipo implementó el backend efectivamente en **Java 21 con Spring Boot 3.2**, no en ASP.NET Core. Esto se verifica de forma directa en el repositorio del proyecto v0.9.0-rc:

- `Backend/pom.xml`: proyecto Maven con `spring-boot-starter-parent` versión `3.2.12`, propiedad `java.version` = `21`.
- Dependencias implementadas: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `spring-boot-starter-actuator`, `flyway-core`, driver `postgresql`, y `io.jsonwebtoken:jjwt-*` versión `0.12.6`.
- `README.md` del proyecto v0.9.0-rc declara explícitamente el stack: Java 21, Spring Boot 3.2.x, Spring Security 6, jjwt 0.12.x, Spring Data JPA + Hibernate, PostgreSQL 16, Flyway, Redis 7, Angular 17+, Docker Compose.
- No existe ningún artefacto `.csproj`, `.sln` ni referencia a .NET en el repositorio v0.9.0-rc.

El SRS v0.9.0-rc (sección 2.2, *Cambio de plataforma tecnológica respecto a la Entrega 1A*) y `CAMBIOS-SRS.md` documentan este mismo hecho y señalan explícitamente que no se encontró en el repositorio un ADR que formalice la migración, dejándolo como observación abierta. Este documento cierra esa observación.

No se encontró en los repositorios provistos evidencia de un acta de reunión, un issue o un mensaje de commit que narre el motivo puntual del cambio de ASP.NET Core a Java/Spring Boot. La reconstrucción de "alternativas consideradas" en este ADR se basa en:
1. Las opciones ya evaluadas y descartadas en ADR-001.
2. El stack efectivamente implementado y verificable en el código de la Entrega 1B / v0.9.0-rc.
3. Las restricciones ya declaradas en el SRS v0.9.0-rc (sección 2.5), que fijan Java 21 LTS y Spring Boot 3.2.x como parte del entorno obligatorio del proyecto desde esta versión en adelante.

## Problema
¿Qué lenguaje y framework de backend debe sustentar BIOPET a partir de la Entrega 1B en adelante, dado que la implementación real ya migró de la decisión original (ASP.NET Core 8, ADR-001) hacia Java 21 / Spring Boot 3.2, y es necesario que la documentación arquitectónica refleje con evidencia verificable la pila realmente construida, evaluada y probada?

## Alternativas consideradas

| Opción | Descripción | Origen de la evidencia |
|---|---|---|
| A. Mantener ASP.NET Core 8 (decisión original de ADR-001) | Continuar con la pila seleccionada en la Entrega 1A. | ADR-001. |
| B. PHP 8.2 con Laravel 11 | Alternativa ya evaluada y descartada en ADR-001 por el equipo. | ADR-001. |
| C. PHP 8.2 sin framework | Alternativa ya evaluada y descartada en ADR-001 por falta de estructura mantenible. | ADR-001. |
| D. **Java 21 + Spring Boot 3.2** (opción finalmente implementada) | Migración efectiva realizada durante la Entrega 1B: ecosistema Spring (Security, Data JPA, Cache, Actuator) más Flyway, PostgreSQL 16 y Redis 7. | Código fuente del proyecto v0.9.0-rc (`pom.xml`, `README.md`), SRS v0.9.0-rc sección 2.2, `CAMBIOS-SRS.md`. |

Los repositorios provistos no contienen el razonamiento explícito que descartó la Opción A a favor de la Opción D. Este ADR no inventa dicho razonamiento; se limita a formalizar la decisión ya tomada e implementada.

## Decisión adoptada
Se adopta formalmente **Java 21 (LTS) con Spring Boot 3.2.x** como lenguaje y framework del backend de BIOPET, junto con el resto de la pila ya implementada y verificada en el código:

- **Lenguaje/runtime:** Java 21 LTS.
- **Framework de aplicación:** Spring Boot 3.2.x.
- **Seguridad:** Spring Security 6, autenticación JWT stateless con `jjwt` 0.12.x (RFC 7519).
- **Persistencia:** Spring Data JPA sobre Hibernate, PostgreSQL 16.
- **Control de versiones de esquema:** Flyway.
- **Caché y revocación de tokens:** Redis 7 (formalizado además en ADR-003).
- **Documentación de API:** Springdoc OpenAPI 2.x.
- **Frontend:** Angular 17+ (fuera del alcance de este ADR de backend, pero parte de la pila general).
- **Empaquetado y despliegue:** Docker / Docker Compose, con imágenes ancladas por digest sha256.

ADR-001 (ASP.NET Core 8) queda **superado** por este documento a partir de la Entrega 1B.

## Justificación técnica
1. **Es la pila efectivamente construida, probada y en funcionamiento.** El código del proyecto v0.9.0-rc contiene un backend Spring Boot funcional (`AuthController`, `MascotaController`, `UsuarioController`, servicios, seguridad, migraciones Flyway, pruebas JUnit 5 + MockMvc), verificado contra los requisitos del SRS (REQ-F-001 a REQ-F-012, todos "Implementado y verificado").
2. **El ecosistema Spring cubre de forma nativa los requisitos no funcionales críticos de la Tercera Entrega:** Spring Security 6 con `@PreAuthorize` para RBAC (REQ-F-006), Spring Data JPA/Hibernate para persistencia sobre PostgreSQL 16 con Flyway (restricción de la sección 2.5 del SRS: prohibición de `ddl-auto=update`), y Spring Data Redis para la revocación de JWT (REQ-F-005, ADR-003).
3. **Continuidad con las restricciones ya fijadas por el SRS v0.9.0-rc** (sección 2.5): "El backend debe ejecutarse sobre Java 21 LTS y Spring Boot 3.2.x". Mantener esta pila evita una nueva ruptura de continuidad antes de la Entrega Final (v1.0.0).
4. **Alineación con el bloque A.2 de la Guía de la Tercera Entrega** (estrategia híbrida ORM + procedimientos almacenados vía Jakarta Persistence 2.1, `@Procedure` y `@NamedStoredProcedureQuery`): este mecanismo está disponible de forma nativa en Spring Data JPA sobre Java, lo que facilita cumplir dicho bloque en iteraciones futuras.

## Consecuencias positivas
- El backend cuenta con un ecosistema maduro para seguridad (Spring Security), persistencia (Spring Data JPA/Hibernate) y observabilidad (Actuator).
- Java 21 LTS ofrece soporte a largo plazo, alineado con la vida útil esperada de un proyecto académico que se archivará como artefacto reproducible (Zenodo, DOI) según el bloque E de la Guía.
- El soporte nativo de Spring Data JPA para `@Procedure` y `@NamedStoredProcedureQuery` facilita la futura estrategia híbrida ORM/SP exigida por el bloque A.2 de la Guía, sin requerir librerías adicionales.
- Integración fluida ya verificada con PostgreSQL 16, Flyway y Redis 7.
- Buena disponibilidad de documentación oficial y comunidad para Spring Boot, reduciendo el riesgo de bloqueos técnicos en las semanas restantes del calendario académico.

## Consecuencias negativas
- Se pierde el trabajo de análisis y cualquier código o configuración que pudiera haberse iniciado sobre ASP.NET Core 8 durante la Entrega 1A (no se encontró código .NET en los repositorios provistos).
- El equipo debió asumir una curva de aprendizaje distinta a la prevista originalmente en ADR-001 (que citaba la afinidad con C# como ventaja); no se encontró evidencia documentada de cómo se gestionó.
- Aumenta la complejidad operativa del entorno de build (Maven, imágenes `maven:3.9-eclipse-temurin-21` y `eclipse-temurin:21-jre-alpine` ancladas por digest) respecto de lo que habría exigido un runtime .NET.
- La ausencia de un ADR formal durante la Entrega 1B representa una brecha de trazabilidad de decisiones que este documento resuelve de forma tardía y retroactiva.

## Impacto sobre el proyecto
- **Sobre el SRS v0.9.0-rc:** consistente con la sección 2.2 y con las restricciones de la sección 2.5. No contradice ningún requisito funcional ni no funcional; los formaliza documentalmente.
- **Sobre la Guía de la Tercera Entrega:** cierra uno de los seis ADR obligatorios del bloque D ("elección de la pila principal"). Los cinco restantes (esquema de autenticación, gestor de base de datos, estrategia de cache, estrategia de despliegue, estrategia de acceso a datos) no forman parte del alcance de este documento.
- **Sobre el criterio de la rúbrica:** contribuye al criterio C5 (*Documentación arquitectónica y trazabilidad*, bloque D) y resuelve una observación abierta que podría afectar C0R (*Consolidación de ingeniería de requisitos*) si permanecía sin documentar.
- **No introduce cambios de código.** Es un documento retroactivo que formaliza una decisión ya implementada.

## Referencias a otros documentos
- ADR-001 — *Selección del lenguaje de programación del servidor* (`docs/ADR/ADR-001-tecnologia.md`, Entrega 1A). Superado por este documento.
- ADR-003 — *Revocación de JWT mediante Redis* (`docs/adr/ADR-003-jwt-redis.md`). Vigente, complementario a este ADR.
- SRS v0.9.0-rc, sección 2.2 (*Cambio de plataforma tecnológica respecto a la Entrega 1A*) y sección 2.5 (*Restricciones*).
- `Backend/pom.xml` y `README.md` del proyecto v0.9.0-rc (evidencia de código verificada).
