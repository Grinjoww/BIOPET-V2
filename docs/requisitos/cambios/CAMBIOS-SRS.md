# CAMBIOS-SRS.md

Resumen técnico de las modificaciones realizadas sobre el SRS de la Entrega 1A (v0.3.0)
para producir la versión v0.9.0-rc (Tercera Entrega). No se incluyen cambios triviales
de formato; solo se listan cambios funcionales y de documentación.

## Fuentes analizadas

- SRS de la Entrega 1A (`PFC_Entrega1A_BMT.pdf`, proyecto `APP-WEB-PFC--main`).
- Informe técnico de la Entrega 1B (`PFC_Entrega1B_BMT.pdf`, proyecto `PFC-VET-ENTR3-v0.9.0-rc-main`).
- Código fuente del backend (Java 21 / Spring Boot 3.2): controladores, entidades, DTOs,
  servicios, seguridad y pruebas.
- Migración de base de datos `V1__schema_inicial.sql` (Flyway).
- Frontend Angular (login, guard, interceptor JWT, listado de mascotas).
- Diagramas C4, DER, de clases y de secuencia de la Entrega 1B.
- ADR-003 (revocación de JWT mediante Redis).
- Guía oficial de la Tercera Entrega (v0.9.0-rc).

## Cambio estructural más importante: migración de pila tecnológica

El SRS original (Entrega 1A) especificaba **ASP.NET Core 8 / C#** como backend
(ADR-001). El código realmente implementado en la Entrega 1B usa **Java 21 +
Spring Boot 3.2 + Spring Security 6 + Spring Data JPA/Hibernate + PostgreSQL 16 +
Flyway + Redis 7**. Todo el documento se reescribió para reflejar la pila real.
No se encontró en el repositorio un ADR que documente formalmente este cambio;
se deja como observación abierta en la sección 7 del SRS actualizado.

## Cambios por sección

### Introducción y descripción global
- Se reescribieron las secciones 1 y 2 siguiendo la plantilla ISO/IEC/IEEE
  29148:2018 (propósito, alcance, definiciones, referencias, perspectiva,
  funciones, usuarios, restricciones, supuestos).
- Se añadió una sección explícita (2.2) sobre el cambio de plataforma
  tecnológica respecto a la Entrega 1A, ausente en el documento original.
- El alcance del producto se dividió explícitamente en "funciones implementadas
  y verificadas" versus "funciones planificadas y pendientes", algo que el SRS
  original no distinguía (asumía que todo el núcleo Must Have se implementaría
  en el mismo semestre).

### Requisitos funcionales
- Se actualizaron y renombraron los requisitos heredados de RF-01 a RF-15
  (más RF-WEB-01 a RF-WEB-04) al nuevo esquema REQ-F-NNN, con identificador,
  nombre, descripción en patrón shall, rationale, prioridad MoSCoW, criterio
  de aceptación medible y método de verificación — campos que el SRS original
  no tenía de forma completa y homogénea para todos los requisitos.
- Se separaron en dos grupos:
  - **Implementados y verificados** (REQ-F-001 a REQ-F-012): cubren
    autenticación (registro, login, refresh, logout con revocación,
    control de acceso por rol, consulta de perfil) y CRUD completo de
    Mascota. Se verificaron contra el código real: `AuthController`,
    `MascotaController`, `UsuarioController`, `AuthService`, `MascotaService`,
    `JwtService`, `TokenBlacklistService`.
  - **Pendientes, heredados de la Entrega 1A** (REQ-F-013 a REQ-F-020):
    historial clínico, prescripción de medicamentos, citas, telemetría IoT,
    recomendaciones por IA, facturación digital y reportes. Se conservan
    porque su modelo de datos ya fue diseñado en la Entrega 1A (tablas Cita,
    Historial_Clinico, Dispositivo_IoT, Chat_Triage, Factura, etc.), pero se
    reclasificaron con prioridad Should/Could (en vez de Must/Media del
    documento original) y se marcó su estado como "pendiente" para que la
    matriz de trazabilidad de la Tercera Entrega los declare correctamente.
- Se agregó REQ-F-002 (rechazo de correo duplicado) y REQ-F-007 (consulta de
  perfil propio), funcionalidades presentes en el código pero no
  documentadas como requisitos independientes en el SRS original.
- No se inventó ningún requisito sin sustento en el código, en el SRS
  original o en la Guía de la Tercera Entrega.

### Requisitos no funcionales
- Se actualizaron RNF-01 a RNF-08 y RNF-WEB-01 a RNF-WEB-05 al esquema
  REQ-NF-NNN con el mismo nivel de completitud exigido por el bloque A.3.1 de
  la Guía.
- Se incorporaron umbrales cuantitativos exigidos por el bloque C de la Guía
  (p95 < 200 ms con caché caliente, p95 < 500 ms con caché fría) que el SRS
  original no especificaba con esa precisión.
- Se agregaron requisitos nuevos derivados directamente de controles OWASP
  exigidos por la Guía y ausentes en el SRS original: REQ-NF-009 (registro de
  eventos de autenticación, control A09) y REQ-NF-010 (limitación de intentos
  de login, control A07). Ambos se marcaron como pendientes de evidencia o de
  implementación, según corresponda al estado real del código.
- Se agregó REQ-NF-013 (estrategia híbrida de acceso a datos, ORM vs.
  procedimientos almacenados), exigida por el bloque A.2 de la Guía. Se
  documentó como "cumplida por alcance actual (no aplica todavía)" porque el
  sistema solo tiene CRUD elemental sobre dos entidades sin joins ni
  agregaciones; no se inventó ningún procedimiento almacenado inexistente.
- REQ-NF-004 (gestión de tokens JWT) se actualizó para reflejar que el token
  ya incluye los siete claims estándar, incluyendo `aud`, verificado
  directamente en `JwtService.java`.

### Arquitectura
- Se reemplazaron los diagramas C4 de contenedores, el diagrama de clases y
  se agregó el diagrama de secuencia de autenticación JWT, todos generados
  durante la Entrega 1B y consistentes con el código Java/Spring Boot. El
  diagrama de contexto (Nivel 1) de la Entrega 1A se conservó sin cambios por
  seguir siendo válido (no depende del lenguaje del backend).
- Se agregó una tabla comparativa de capas y tecnologías (v0.9.0-rc vs.
  v0.3.0) para dejar explícito y trazable el cambio de pila.
- Se señaló la ausencia de un ADR formal de reemplazo de ADR-001 como
  observación abierta.

### Modelo de base de datos
- Se actualizó el diccionario de datos de `usuarios` y `mascotas` para que
  coincida exactamente con la migración Flyway `V1__schema_inicial.sql`
  (tipos de dato, restricciones CHECK, triggers de `actualizado_en`), en
  lugar del diseño conceptual original de la Entrega 1A.
- El resto del modelo entidad-relación (Cita, Historial_Clinico,
  Dispositivo_IoT, Chat_Triage, Producto_Servicio, Factura, Detalle_Factura,
  Proveedor, Mercaderia, Detalle_Ingreso) se mantiene referenciado como
  visión de producto pendiente de materialización, sin duplicar el DDL
  completo del documento original.

### Matriz de trazabilidad
- Se agregó una sección de trazabilidad (ausente en el SRS original) con una
  tabla resumen que vincula cada requisito Must implementado a su clase,
  endpoint y prueba automatizada real, y declara el estado "pendiente" para
  los requisitos no funcionales sin evidencia empírica archivada.

### Observaciones e información pendiente (nueva sección)
Se agregó una sección 7 que documenta explícitamente, sin inventar contenido,
los artefactos exigidos por la Guía que no se encontraron en el repositorio
provisto y que por tanto no pudieron completarse con evidencia verificable:
bitácora de observaciones, ADR de cambio de pila, evidencia empírica del
bloque C (perf/sec/sus/lighthouse/jacoco), catálogo de procedimientos
almacenados, matriz de trazabilidad en CSV, historias de usuario y casos de
uso formales, y artefactos del bloque E (licencia, CITATION.cff,
CONTRIBUTORS.md, CHANGELOG.md, DOI).

## Elementos NO modificados por falta de sustento

- No se generaron historias de usuario en formato Connextra ni casos de uso
  Cockburn nuevos, porque no existían en ninguno de los dos proyectos
  proporcionados y su redacción desde cero excedería el alcance de "no
  inventar contenido".
- No se creó el ADR de cambio de pila tecnológica; se señala como pendiente
  en vez de redactarlo, ya que el contenido de "alternativas consideradas"
  requeriría información no documentada en los archivos entregados.
- No se generaron archivos de evidencia empírica (JSON de k6, capturas curl,
  CSV de SUS, reportes JaCoCo/Lighthouse) porque no existen mediciones
  reales que reportar; el SRS deja los requisitos correspondientes marcados
  como "pendiente de evidencia empírica" en vez de simular resultados.
