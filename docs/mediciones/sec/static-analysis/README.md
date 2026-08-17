# Análisis estático (SAST) del backend — SpotBugs + Find Security Bugs

## Resumen ejecutivo

| Campo | Valor |
|---|---|
| Herramienta | SpotBugs `4.10.3.0` (Maven plugin) + Find Security Bugs `1.14.0` |
| Comando exacto | `cd Backend && mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs` |
| Fecha de esta ejecución | `2026-08-17` (commit `9a1afce`) |
| Clases analizadas | Todas las de `Backend/target/classes` (`com.biopet.**`, compiladas desde `mvn clean verify` previo) |
| Hallazgos totales | **66** |
| Severidad **alta** (`priority=1`) | **1** — `SPRING_CSRF_PROTECTION_DISABLED` |
| Severidad **media** (`priority=2`) | **13** |
| Severidad **baja** (`priority=3`) | **52** |
| Hallazgos relacionados con SQL (`SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` y variantes) | **0** |
| Reporte archivado | [`spotbugs-report.xml`](spotbugs-report.xml) (XML completo, sin editar) |

## Integración en el backend

`Backend/pom.xml` declara `spotbugs-maven-plugin` con `findsecbugs-plugin`
como plugin adicional (`effort=Max`, `threshold=Low` — el nivel más
exhaustivo, para no ocultar hallazgos bajando la sensibilidad), **sin
enlazarlo a ninguna fase del ciclo de vida** (`<executions>` vacío
deliberadamente): así `mvn clean verify` sigue comportándose exactamente
igual que antes de esta fase (mismos 189 tests, mismo JaCoCo, mismo
resultado), y el análisis estático se ejecuta solo cuando se invoca
explícitamente. Esto es intencional: introducir un nuevo gate en el build
por defecto es una decisión de CI, que esta fase tiene prohibido tocar
todavía (`.github/workflows/` queda para la Fase 3).

## Comando reproducible

```bash
cd Backend
mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs
# Reporte generado en: Backend/target/spotbugsXml.xml
```

Para que falle el build si aparece cualquier hallazgo (uso opcional, no
integrado por defecto en esta fase):

```bash
cd Backend
mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:check
```

## Desglose de hallazgos por tipo

| Tipo | Categoría | Prioridad | Cantidad | ¿Qué es? |
|---|---|---:|---:|---|
| `SPRING_CSRF_PROTECTION_DISABLED` | SECURITY | **Alta (1)** | 1 | Protección CSRF de Spring Security explícitamente deshabilitada (`SecurityConfig.java:55`) |
| `EI_EXPOSE_REP2` | MALICIOUS_CODE | Media (2) | 11 | Un constructor/setter guarda una referencia mutable recibida como parámetro sin copiarla |
| `CT_CONSTRUCTOR_THROW` | BAD_PRACTICE | Media (2) | 2 | Un constructor puede lanzar una excepción antes de completar la inicialización del objeto |
| `SPRING_ENDPOINT` | SECURITY | Baja (3) | 33 | Hallazgo informativo de Find Security Bugs: identifica cada método anotado como endpoint REST (`@GetMapping`/`@PostMapping`/etc.) — no es, por sí mismo, un defecto |
| `DM_CONVERT_CASE` | I18N | Baja (3) | 5 | `toLowerCase()`/`toUpperCase()` sin especificar `Locale` explícito |
| `CRLF_INJECTION_LOGS` | SECURITY | Baja (3) | 8 | Posible inyección de `\r`/`\n` en líneas de log a partir de un valor no confiable — **ver análisis dedicado abajo, ya mitigado** |
| `REC_CATCH_EXCEPTION` | STYLE | Baja (3) | 2 | `catch (Exception e)` genérico en vez de una excepción más específica |
| `EI_EXPOSE_REP` | MALICIOUS_CODE | Media (2) | 1 | Un getter devuelve una referencia mutable interna sin copiarla |
| `COOKIE_USAGE` | SECURITY | Baja (3) | 2 | Uso de cookies detectado (hallazgo informativo de Find Security Bugs, no un defecto: las cookies de BIOPET ya están documentadas con `HttpOnly`/`Secure`/`SameSite=Strict` en `A07-authentication.md`) |
| `SERVLET_HEADER` | SECURITY | Baja (3) | 1 | Escritura de una cabecera HTTP detectada (informativo) |
| `DE_MIGHT_IGNORE` | BAD_PRACTICE | Baja (3) | 1 | Un `catch` vacío podría estar ignorando una excepción silenciosamente |
| `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` | BAD_PRACTICE | Baja (3) | 1 | Un método declara `throws RuntimeException` genérico |

**Hallazgos SQL: 0.** Ninguna instancia de `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`,
`SQL_INJECTION_JDBC`, `SQL_INJECTION_SPRING_JDBC`, `SQL_INJECTION_HIBERNATE`
ni ninguna otra regla de la familia SQL de Find Security Bugs. Consistente
con lo ya documentado en `docs/mediciones/sec/A03-injection.md`: el backend
usa exclusivamente consultas derivadas de Spring Data y una única `@Query`
nativa con parámetro enlazado (`:duenioId`), sin concatenación de SQL en
ningún punto de `Backend/src/main`.

## Hallazgo de severidad ALTA: `SPRING_CSRF_PROTECTION_DISABLED`

**Resumen del hallazgo, sin ambigüedad:** Find Security Bugs produjo **66
hallazgos totales** en esta ejecución. De ellos, **1 es de severidad alta**:
`SPRING_CSRF_PROTECTION_DISABLED`. **Este hallazgo no está relacionado con
concatenación de SQL ni con ninguna forma de inyección SQL** — es un
hallazgo de categoría `SECURITY` sobre configuración de protección
cross-site, completamente independiente del análisis de SQL (que reporta
**0 hallazgos**, ver sección anterior). No se trata, por tanto, de dos
problemas de la misma familia: son hallazgos de naturaleza distinta y no
deben confundirse al leer el resumen de 66.

- **Ubicación:** `Backend/src/main/java/com/biopet/config/SecurityConfig.java:55` — `.csrf(csrf -> csrf.disable())`.
- **Naturaleza del hallazgo:** es un **hallazgo real del analizador**, no un
  error de la herramienta ni un artefacto de configuración: la protección
  CSRF integrada de Spring Security (el mecanismo de token sincronizador
  que Spring provee por defecto) está, en efecto, deshabilitada en el
  código tal como está escrito hoy. SpotBugs/Find Security Bugs no
  "inventan" esta línea; la detectan correctamente. No se le baja la
  severidad, no se suprime con `@SuppressFBWarnings`, y no se excluye del
  reporte: permanece visible, con severidad alta, en
  [`spotbugs-report.xml`](spotbugs-report.xml).
- **Arquitectura actual y mitigación adoptada:** BIOPET no usa el modelo de
  sesión que el mecanismo CSRF de Spring Security está diseñado para
  proteger (cookie de sesión "de servidor" enviada automáticamente y sin
  restricciones de origen). En su lugar, la identidad se transporta en un
  **JWT dentro de una cookie `HttpOnly` + `Secure` + `SameSite=Strict`**
  (`JwtCookieService`, verificado en
  `docs/mediciones/sec/A02-cryptography-tls.md` y `A07-authentication.md`,
  con evidencia HTTP real en `docs/mediciones/sec/raw/A07-auth-rate-limit.txt`).
  El atributo **`SameSite=Strict` es la mitigación actualmente adoptada
  por el proyecto frente a solicitudes cross-site**: con ese atributo, el
  navegador no adjunta la cookie en ninguna petición de origen cruzado (ni
  siquiera en navegación de nivel superior iniciada desde otro sitio), lo
  que reduce la superficie del vector clásico de CSRF (un sitio malicioso
  induciendo al navegador de la víctima a enviar una petición ya
  autenticada) sin depender del token CSRF tradicional de Spring Security.
  Esta es la razón técnica por la que el equipo deshabilitó el filtro CSRF
  de Spring en primer lugar, no una omisión.
- **Por qué no se describe simplemente como "falso positivo":** el
  hallazgo de SpotBugs es correcto sobre el hecho que reporta (CSRF de
  Spring efectivamente deshabilitado); lo que cambia el nivel de exposición
  real es una decisión arquitectónica externa a lo que SpotBugs puede
  analizar (el atributo `SameSite` de las cookies emitidas en tiempo de
  ejecución, no visible en un análisis estático de bytecode). Por eso este
  hallazgo se conserva y documenta como **riesgo real del analizador,
  actualmente mitigado por la arquitectura de cookies del proyecto** — no
  como un error de la herramienta a descartar sin más.
- **No se modifica `SecurityConfig.java` en esta corrección.** El hallazgo
  queda registrado como riesgo/decisión de seguridad documentada,
  disponible para evaluación y para futuras mejoras (por ejemplo, si algún
  flujo futuro requiriera `SameSite=Lax` o `None` para integraciones de
  terceros, este hallazgo debería reevaluarse en ese momento, ya que la
  mitigación actual dejaría de aplicar de la misma forma).

## Análisis de `CRLF_INJECTION_LOGS` (8 instancias, severidad baja) — falso positivo verificado

Las 8 instancias apuntan, sin excepción, a los siete métodos públicos de
`Backend/src/main/java/com/biopet/security/AuthenticationAuditService.java`
(`loginExitoso`, `loginFallido`, `loginBloqueado`, `refreshExitoso`,
`refreshFallido`, `logoutExitoso`, `tokenRevocado`). SpotBugs marca estas
líneas porque, mirando solo la firma del método, un `String ip`/`String
subject` recibido como parámetro se pasa a un logger — sin poder ver, con
análisis estático de flujo limitado, que **ese mismo archivo ya sanitiza el
valor antes de registrarlo**:

```java
// AuthenticationAuditService.java — normalizar()
valor.replaceAll("\\p{Cntrl}", "")   // elimina TODOS los caracteres de control, incluye \r \n \t
```

Este control ya está **verificado con una prueba automatizada dedicada**
(no solo inspección de código):
`AuthenticationAuditServiceTest.eliminaCaracteresDeControlParaEvitarLogForging`
envía una IP y un `subject` con `\r`, `\n` y `\t` embebidos y confirma que
el mensaje final generado no contiene ninguno de esos caracteres y sigue
siendo una única línea. Es decir: el mismo escenario que SpotBugs
identifica como riesgo teórico ya tiene una prueba real que demuestra que
no ocurre en la práctica.

**Decisión:** no se modifica código. No se suprime la regla globalmente
(sigue activa y visible en el reporte archivado) ni se anota
`@SuppressFBWarnings` en la clase — el hallazgo permanece visible para
quien revise `spotbugs-report.xml`, con esta explicación como registro de
por qué no requiere corrección.

## Otros hallazgos de severidad media (`EI_EXPOSE_REP`/`EI_EXPOSE_REP2`, `CT_CONSTRUCTOR_THROW`)

Los 12 hallazgos `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` corresponden al patrón
estándar de entidades JPA/DTOs generados con Lombok (`@Getter`/`@Setter`/`@Builder`)
que exponen o reciben referencias mutables (por ejemplo, `Instant`,
listas) sin copiarlas defensivamente — un patrón extremadamente común en
proyectos Spring Boot/JPA y de riesgo real bajo en este contexto (no hay
un límite de confianza entre el código que construye estas entidades y el
que las consume; todo ocurre dentro del mismo proceso backend, tras la
capa de autorización). `CT_CONSTRUCTOR_THROW` (2 instancias) señala
constructores que pueden lanzar excepción antes de completar la
inicialización — un riesgo de finalizer-attack casi exclusivamente
relevante cuando la clase es `non-final` y tiene un `finalize()`
sobrescrito, que no es el caso de ninguna clase de este proyecto. Ninguno
de los dos tipos se corrigió en esta fase: no representan una vulnerabilidad
de seguridad explotable en el contexto de este backend, y "corregirlos"
implicaría copiar defensivamente objetos inmutables (`Instant` ya es
inmutable) o reestructurar constructores sin beneficio de seguridad real,
fuera del alcance declarado de esta fase (no se debía alterar lógica
productiva salvo para una vulnerabilidad real demostrada).

## Hallazgos SQL relacionados: ninguno

Se buscó explícitamente en el reporte archivado cualquier tipo de bug con
"SQL" en el nombre:

```bash
grep -oP "type='\K[^']+" docs/mediciones/sec/static-analysis/spotbugs-report.xml | sort -u | grep -i sql
# (sin resultado)
```

Cero hallazgos. Esto es consistente y cruzado con:
- `docs/mediciones/sec/A03-injection.md` (evidencia dinámica: pruebas + HTTP real).
- `scripts/audit-sql-dynamic.sh` (auditoría estática de `db/procs/*.sql`, ver `docs/mediciones/sec/static-analysis/../../..`, sección "Auditor SQL" en la respuesta de esta tarea — 0 hallazgos sobre el único SP existente).

## Filtros y supresiones

**Ninguno.** No se agregó ningún filtro de exclusión de SpotBugs
(`excludeFilterFile`), no se usó `@SuppressFBWarnings` en ninguna clase, y
no se bajó el `threshold` por debajo de `Low` para reducir el conteo. Los
66 hallazgos que produjo la herramienta con su configuración más
exhaustiva (`effort=Max`, `threshold=Low`) están todos en el reporte
archivado y todos catalogados arriba, con decisión explícita para cada
categoría.

## Reproducción

```bash
cd Backend
mvn clean compile                                            # asegura target/classes actualizado
mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs
cat target/spotbugsXml.xml   # o abrir con cualquier visor XML
```
