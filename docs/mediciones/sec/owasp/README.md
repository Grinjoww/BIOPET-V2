# Seis categorías OWASP mínimas — Entrega Final v1.0.0 (Fase 2, Jaime)

## Qué es este documento y qué no es

Este archivo **no reimplementa ni reescribe** la evidencia de seguridad ya
existente en `docs/mediciones/sec/A0*-*.md`: la selecciona, la sintetiza en
el formato exigido por la Entrega Final (control / evidencia / comando /
resultado) y confirma que sigue siendo reproducible hoy, con una ejecución
real y fresca. El detalle completo de cada control — código exacto,
archivo:línea, todas las pruebas relacionadas, limitaciones — sigue viviendo
en los documentos `A0X-*.md` originales; este README enlaza a ellos en vez
de duplicarlos.

## Las seis categorías OWASP

Este README presenta como conjunto principal las **seis categorías OWASP
Top 10** ya utilizadas históricamente por BIOPET en
`docs/mediciones/sec/REPORT.md`, cada una identificada por su código
oficial:

- **A01** — Broken Access Control
- **A02** — Cryptographic Failures
- **A03** — Injection
- **A05** — Security Misconfiguration
- **A07** — Identification and Authentication Failures
- **A09** — Security Logging and Monitoring Failures

No se cuentan como categorías independientes mecanismos que, aunque tienen
su propia evidencia y sus propias pruebas automatizadas, son **parte** de
una de estas seis categorías OWASP, no una categoría OWASP en sí mismos:

| Mecanismo (evidencia propia, no es una categoría OWASP aparte) | Categoría OWASP a la que pertenece | Por qué |
|---|---|---|
| Rate limiting de login | **A07** | A07 (2021) lista explícitamente "permite ataques de fuerza bruta u otros ataques automatizados" como parte de la propia categoría |
| Manejo seguro de errores (`GlobalExceptionHandler`, RFC 7807) | **A05** | A05 (2021) incluye explícitamente "el manejo de errores revela stack traces u otros mensajes demasiado informativos" como ejemplo de configuración de seguridad incorrecta |
| Cookies JWT (`HttpOnly`+`Secure`+`SameSite=Strict`) | **A02** | El transporte/almacenamiento seguro de credenciales de sesión es evidencia de A02 (fallas criptográficas / protección de datos en tránsito), documentado junto con TLS en `A02-cryptography-tls.md` |
| Cabeceras de seguridad HTTP | **A05** | Cabeceras (`CSP`, `X-Frame-Options`, etc.) son el ejemplo canónico de configuración de seguridad de A05 |

De los ocho controles ya evidenciados en `docs/mediciones/sec/`
(A01, A02, A03, A04, A05, A06, A07, A09, más XSS), se seleccionaron
exactamente los **seis** de la lista oficial de arriba. A04
(Insecure Design) y A06 (Vulnerable and Outdated Components) siguen
evaluados y documentados en `docs/mediciones/sec/A04-insecure-design.md` y
`A06-vulnerable-components.md` respectivamente, y `A04` es también la
fuente de la evidencia de manejo de errores citada en la tabla anterior —
pero no se cuentan aquí como parte de las seis categorías principales de
esta fase, para no exceder el conjunto pedido. No se seleccionó ningún
control que el código **no** implemente: no se incluye A08 ni A10 (no
auditados en ninguna fase anterior) ni se inventa evidencia para ellos.

## Las seis categorías, en el formato exigido

### A01 — Broken Access Control

- **Riesgo/control verificado:** un usuario autenticado con rol `ROLE_DUENO`
  no debe poder leer ni modificar recursos que no le pertenecen (IDOR), y
  un usuario sin rol suficiente no debe poder ejecutar operaciones
  administrativas, incluso conociendo el endpoint.
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=MascotaControllerTest,AuthControllerTest test
  ```
  Evidencia HTTP real end-to-end (requiere stack Docker levantado y
  `ADMIN_PASSWORD`):
  ```bash
  ADMIN_PASSWORD='...' scripts/security-evidence.sh
  ```
- **Resultado esperado:** sin autenticación → `401`; autenticado sin
  permiso o sobre un recurso ajeno → `403`, con `ProblemDetail`
  (`type=urn:biopet:error:forbidden`), nunca un `200` ni una fuga de datos.
- **Resultado observado (real, `2026-08-17T03:19:05Z`, commit `9a1afce`):**
  `401` en `GET /api/usuarios/me` sin cookies; `403` cuando el Dueño B pide
  la mascota del Dueño A (IDOR bloqueado); `403` cuando el Dueño B intenta
  `POST /api/mascotas`. Los tres, verificados con `curl` real contra
  `https://localhost:8443`, no solo con `MockMvc`.
- **Archivo de evidencia:** [`../raw/A01-access-control.txt`](../raw/A01-access-control.txt) (fresca) · detalle completo en [`A01-access-control.md`](../A01-access-control.md).

### A02 — Cryptographic Failures

- **Riesgo/control verificado:** los datos de sesión (JWT) no deben viajar
  ni almacenarse de forma insegura: la conexión debe ser HTTPS real (no
  solo TLS "disponible"), y las cookies que portan el JWT deben impedir su
  robo por script (`HttpOnly`) o su envío por canal no cifrado (`Secure`).
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=JwtCookieAuthenticationTest test
  ```
  Evidencia HTTP/TLS real (requiere stack Docker con perfil `tls`):
  ```bash
  ADMIN_PASSWORD='...' scripts/security-evidence.sh
  ```
- **Resultado esperado:** `https://localhost:8443` negocia TLS 1.3 con
  cifrado AEAD; las cookies `access_token`/`refresh_token` se emiten con
  los tres atributos `Secure; HttpOnly; SameSite=Strict` simultáneamente,
  sin excepción, en cada respuesta de login/refresh.
- **Resultado observado (real, misma ejecución):** conexión TLS 1.3
  confirmada con `openssl s_client`/`curl.exe -v` contra `:8443`; cookies
  reales emitidas con `Secure; HttpOnly; SameSite=Strict` (valor
  redactado, atributos visibles) en `raw/A07-auth-rate-limit.txt` — el
  mismo mecanismo de cookies se verifica aquí desde el ángulo de
  protección de datos en tránsito/almacenamiento (A02), no desde el ángulo
  de identidad (A07, ver abajo).
- **Archivo de evidencia:** [`../raw/A02-tls.txt`](../raw/A02-tls.txt) (fresca) · detalle completo en [`A02-cryptography-tls.md`](../A02-cryptography-tls.md).

### A03 — Injection

- **Riesgo/control verificado:** ningún parámetro de entrada debe poder
  alterar la estructura de una sentencia SQL ejecutada por el backend.
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=SqlInjectionSecurityTest test
  ```
  Evidencia HTTP real: `ADMIN_PASSWORD='...' scripts/security-evidence.sh`.
- **Resultado esperado:** payload de inyección en el campo `email` de
  `POST /api/auth/login` → `422` (Bean Validation, `@Email`), nunca
  autentica; payload en `duenioId` (`Long`) → `400` (binding de Spring MVC).
  En ningún caso el cuerpo de la respuesta debe contener nombres de clases
  Java, mensajes de Hibernate/PostgreSQL ni stack traces.
- **Resultado observado (real, misma ejecución):** `422` exacto para los
  tres payloads probados contra `email` (incluido el payload literal de la
  guía, `' OR '1'='1`), con `errors.email:["must be a well-formed email address"]`;
  `400` para los payloads contra `duenioId`; `usuarioRepository.count()`/`mascotaRepository.count()`
  idénticos antes y después (ninguna tabla alterada); ningún cuerpo de
  respuesta contiene `org.hibernate`, `org.postgresql`, `SQLException` ni
  `stackTrace`.
- **Archivo de evidencia:** [`../raw/A03-injection.txt`](../raw/A03-injection.txt) (fresca) · detalle completo en [`A03-injection.md`](../A03-injection.md).

### A05 — Security Misconfiguration

- **Riesgo/control verificado:** dos mecanismos, ambos parte de esta misma
  categoría OWASP:
  1. Las respuestas HTTP deben incluir cabeceras que mitiguen clickjacking
     (`X-Frame-Options`), MIME-sniffing (`X-Content-Type-Options`),
     inyección de contenido (`Content-Security-Policy`) y downgrade a HTTP
     (`Strict-Transport-Security`, solo sobre HTTPS).
  2. Ningún error interno (SQL, Java, Hibernate) debe llegar al cliente en
     texto plano: todos los errores deben responder con un formato
     consistente (RFC 7807 `ProblemDetail`) que no filtre información de
     implementación — la propia guía A05 (2021) cita el manejo de errores
     verboso como ejemplo explícito de configuración de seguridad
     incorrecta.
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=SecurityHeadersTest,UsuarioControllerTest,CitaControllerTest,SqlInjectionSecurityTest test
  ```
  Evidencia HTTP real: `ADMIN_PASSWORD='...' scripts/security-evidence.sh`.
- **Resultado esperado / observado (cabeceras):** ver sección "C. Cabeceras"
  más abajo (tabla dedicada, con los valores exactos verificados en
  código, en pruebas automatizadas y en HTTP real).
- **Resultado esperado / observado (manejo de errores):**
  `RecursoNoEncontradoException` → `404`; `EmailDuplicadoException` → `409`;
  validación de Bean Validation → `422` con detalle por campo;
  `AccessDeniedException` → `403`; confirmado por inspección directa de
  `GlobalExceptionHandler.java` (un único `@RestControllerAdvice`
  reutilizado sin excepción por Usuarios/Citas/Mascotas/Auth) y por
  `SqlInjectionSecurityTest.respuestaNoFiltraInformacionDeBaseDeDatos`, que
  verifica explícitamente la ausencia de `org.hibernate`, `org.postgresql`,
  `SQLException`, `stackTrace` y `syntax error` en el cuerpo de la
  respuesta para los cuatro payloads de inyección probados.
- **Archivo de evidencia:** [`../raw/A05-security-headers.txt`](../raw/A05-security-headers.txt) (fresca) · detalle completo en [`A05-security-headers.md`](../A05-security-headers.md) (cabeceras) y [`A04-insecure-design.md`](../A04-insecure-design.md), sección 8 (manejo de errores; el documento en sí cubre insecure design en general, pero esta sección específica es la evidencia de A05 citada aquí).

### A07 — Identification and Authentication Failures

- **Riesgo/control verificado:** dos mecanismos, ambos parte de esta misma
  categoría OWASP:
  1. La identidad se gestiona mediante JWT en cookies; un token revocado
     (logout) o de tipo incorrecto (refresh usado como access) no debe
     autenticar.
  2. Un atacante no debe poder probar contraseñas sin límite contra el
     endpoint de login (fuerza bruta/credential stuffing) — A07 (2021)
     lista explícitamente "permite ataques de fuerza bruta u otros
     ataques automatizados" como parte de esta misma categoría.
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=AuthControllerTest,JwtCookieAuthenticationTest test
  ```
  Evidencia HTTP real: `ADMIN_PASSWORD='...' scripts/security-evidence.sh`.
- **Resultado esperado (autenticación):** login válido → `200` + cookies
  `access_token`/`refresh_token`; refresh válido → `200` + nueva cookie de
  access; logout → `204` y ambos tokens revocados; una cookie de access ya
  revocada usada después → `401`.
- **Resultado esperado (rate limiting):** los primeros 5 fallos
  consecutivos desde la misma IP → `401` cada uno; el 6º → `429` con
  cabecera `Retry-After` numérica; una IP ya bloqueada rechaza incluso
  credenciales correctas.
- **Resultado observado (real, misma ejecución):** refresh real → `200`
  con nueva cookie; logout real → `204`; la cookie de access usada tras
  logout → `401` (JTI ya en la blacklist de Redis, confirmado también por
  el evento `TOKEN_REVOKED` en A09); intentos 1–5 de login → `401`; 6º
  intento → `429` con `Retry-After: 900` real (15 minutos, igual a
  `security.rate-limit.login.block-duration`).
- **Archivo de evidencia:** [`../raw/A07-auth-rate-limit.txt`](../raw/A07-auth-rate-limit.txt) (fresca) · detalle completo en [`A07-authentication.md`](../A07-authentication.md) (incluye sección dedicada "Rate limiting").

### A09 — Security Logging and Monitoring Failures

- **Riesgo/control verificado:** los eventos de autenticación (éxito,
  fallo, bloqueo, revocación) deben quedar registrados de forma
  estructurada, sin datos sensibles (contraseñas, JWT completos, cookies)
  y sin permitir log forging (inyección de saltos de línea falsos).
- **Comando/prueba reproducible:**
  ```bash
  cd Backend && mvn -Dtest=AuthenticationAuditServiceTest,AuthControllerTest,JwtCookieAuthenticationTest test
  ```
  Evidencia real desde el contenedor: `ADMIN_PASSWORD='...' scripts/security-evidence.sh`.
- **Resultado esperado:** formato `AUTH_AUDIT timestamp=<UTC> event=<EVENTO> result=<RESULTADO> ip=<IP> subject=<SUJETO>`
  para los siete eventos definidos; ausencia total de `password`,
  `access_token`, `refresh_token`, `Bearer` en cualquier línea; caracteres
  de control eliminados del `ip`/`subject` antes de registrar.
- **Resultado observado (real, misma ejecución):** eventos `LOGIN_SUCCESS`,
  `LOGIN_FAILURE`, `LOGIN_RATE_LIMITED`, `REFRESH_SUCCESS`,
  `LOGOUT_SUCCESS`, `TOKEN_REVOKED` capturados directamente desde
  `docker compose logs backend`, sin ningún dato sensible; formato exacto
  verificado.
- **Archivo de evidencia:** [`../raw/A09-audit-logs.txt`](../raw/A09-audit-logs.txt) (fresca) · detalle completo en [`A09-logging.md`](../A09-logging.md).

## Evidencia histórica vs. evidencia final de esta fase

- **Evidencia histórica** (generada `2026-08-01`, commit `136b707`):
  preservada sin alterar en
  [`../raw/historical-2026-08-01/`](../raw/historical-2026-08-01/) — mismo
  conjunto de archivos (`A01-access-control.txt`, `A03-injection.txt`,
  `A05-security-headers.txt`, `A07-auth-rate-limit.txt`,
  `A09-audit-logs.txt`, etc.), sin modificar, para no destruir el rastro de
  la fase anterior.
- **Evidencia final de esta fase** (Fase 2, Entrega Final): generada de
  nuevo, en vivo, el `2026-08-17T03:19:05Z` (commit `9a1afce`), ejecutando
  `scripts/security-evidence.sh` de punta a punta contra el stack Docker
  real levantado en esta misma tarea (se detectaron y eliminaron
  contenedores huérfanos `biopet-*` de un proyecto Docker distinto que
  bloqueaban el `docker compose up`, sin tocar volúmenes de datos). Los 28
  archivos resultantes reemplazan los de `docs/mediciones/sec/raw/*.txt`
  (ruta principal, sin sufijo de fecha — es la evidencia vigente para la
  Entrega Final) y quedan enlazados arriba, uno por categoría.
- **Resultado de esta ejecución:** `mvn clean verify` no se repitió dentro
  de `scripts/security-evidence.sh` en esta corrida (se usó `--skip-build`
  porque ya se había ejecutado por separado momentos antes, sin cambios de
  código entre una ejecución y otra); las 28 verificaciones puntuales
  (`A01.*`–`A09.*`) resultaron **28/28 CUMPLE**, código de salida `0`. Ver
  [`../REPORT.md`](../REPORT.md) para el resumen consolidado de las ocho
  categorías OWASP evaluadas en el proyecto (no solo las seis de esta
  fase).

## Reproducción de punta a punta

```bash
# 1. Backend + suite completa (opcional si ya se corrió antes)
cd Backend && mvn clean verify && cd ..

# 2. Stack Docker + 28 verificaciones HTTP reales de las 6 categorías
ADMIN_PASSWORD='Admin123*' scripts/security-evidence.sh
```

La contraseña del admin semilla (`Admin123*`) es una credencial de
desarrollo pública, documentada en `db/seed.sql` y `README.md` — no es un
secreto real, y el script no trae ningún valor por defecto para forzar que
quien lo ejecute la provea explícitamente.

## Limitaciones (heredadas de los documentos detallados, no repetidas aquí)

Cada documento `A0X-*.md` enlazado arriba documenta sus propias
limitaciones (alcance de las pruebas, qué no se cubrió, riesgos residuales
conocidos). Este README no las repite para no desactualizarse si cambian;
consúltense en el documento correspondiente.
