# ADR-006: Autenticación JWT mediante cookies seguras y controles complementarios

## Identificador
ADR-006

## Título
Autenticación JWT mediante cookies seguras y controles complementarios

## Estado
Aceptado — implementado y verificado en las ramas `jaime/jwt-cookie-security`,
`jaime/jwt-standard-claims`, `jaime/login-rate-limiting`,
`jaime/seguridad-owasp`, `jaime/jacoco-cobertura` y `jaime/evidencias-owasp`
de la Tercera Entrega (v0.9.0-rc).

## Fecha
2026-07-30

## Contexto

BIOPET necesita autenticar usuarios con distintos roles (`ROLE_ADMIN`,
`ROLE_VETERINARIO`, `ROLE_DUENO`, `ROLE_AUXILIAR`) y aplicar reglas de
autorización diferentes según ese rol y, en el caso de `ROLE_DUENO`, según la
propiedad del recurso solicitado.

La aplicación web (Angular) debe evitar almacenar el token de sesión en
`localStorage`, dado el riesgo de exfiltración vía XSS que ese mecanismo
implica. El backend debe además distinguir explícitamente entre
**autenticación** (¿quién es el usuario?) y **autorización** (¿qué puede
hacer ese usuario?), en vez de resolver ambas preguntas con la misma
comprobación.

El sistema requiere dos tipos de token con vidas distintas — un *access
token* de corta duración y un *refresh token* de mayor duración — y
necesita, además del login, un flujo de logout con revocación real (no solo
borrado de cookie en el cliente), una limitación de intentos fallidos de
login para reducir ataques automatizados, y un registro auditable de los
eventos de autenticación.

El proyecto exige adicionalmente controles verificables contra varias
categorías de OWASP Top 10: A01 (Broken Access Control), A02 (Cryptographic
Failures), A05 (Security Misconfiguration), A07 (Identification and
Authentication Failures) y A09 (Security Logging and Monitoring Failures).

Finalmente, la solución debe ser reproducible en un entorno académico basado
en Docker, sin depender de infraestructura externa de producción (por
ejemplo, sin requerir un certificado TLS emitido por una autoridad
certificadora real).

## Decisión

Se implementa autenticación **JWT firmada por el propio backend**
(`Backend/src/main/java/com/biopet/security/JwtService.java`, HMAC-SHA256),
con **access token y refresh token separados**: cada uno se distingue por el
claim propio `typ` (`access` o `refresh`), y ambos se generan a partir del
mismo método `buildToken`, con tiempos de expiración independientes
(`security.jwt.expiration-ms` y `security.jwt.refresh-expiration-ms`).

**Entrega mediante cookies**, no mediante un valor que el frontend deba leer
o almacenar (`Backend/src/main/java/com/biopet/security/JwtCookieService.java`):
las cookies `access_token` y `refresh_token` se emiten con los atributos
`HttpOnly`, `Secure` y `SameSite=Strict` (`security.cookie.secure`,
`security.cookie.same-site` en `application.yml`), con `Path=/` para el
access token y `Path=/api/auth` para el refresh token. El navegador adjunta
estas cookies automáticamente en cada solicitud al mismo origen; el
frontend Angular las envía mediante `credentials: 'include'` y **no accede
ni administra manualmente el valor del JWT** en ningún momento.

Cada solicitud protegida pasa por `JwtAuthenticationFilter`
(`Backend/src/main/java/com/biopet/security/JwtAuthenticationFilter.java`),
que resuelve el token primero desde la cookie y, si no existe, desde el
encabezado `Authorization: Bearer` (`resolveToken`). El filtro realiza
**validación criptográfica y semántica** completa: verifica la firma HMAC,
el emisor (`iss`) y la audiencia (`aud`) declarados
(`JwtService.extractClaims`), confirma que el token sea de tipo `access`
(`isAccessToken`), y consulta la blacklist de revocación antes de construir
la autenticación en el contexto de seguridad.

El token incluye los **siete claims JWT estándar** (RFC 7519): `iss`
(emisor), `sub` (id de usuario), `aud` (audiencia), `iat` (emisión), `nbf`
(no antes de), `exp` (expiración) y `jti` (identificador único), más tres
claims propios no estándar: `email`, `rol` y `typ`.

La **autorización** combina rol (`@PreAuthorize("hasAnyRole(...)")` en
`MascotaController`) y propiedad del recurso
(`MascotaService.verificarPropiedad`, que compara el dueño real de la
mascota contra el usuario autenticado para `ROLE_DUENO`). Una solicitud sin
autenticación válida responde **401**
(`ProblemAuthenticationEntryPoint`); una solicitud autenticada pero sin el
rol o la propiedad requeridos responde **403**
(`ProblemAccessDeniedHandler`).

El **logout** (`AuthController.logout` → `AuthService.logout`) elimina
ambas cookies en el cliente (`Max-Age=0`) y **revoca** los tokens presentes
mediante `TokenBlacklistService`
(`Backend/src/main/java/com/biopet/security/TokenBlacklistService.java`),
que almacena el `jti` en **Redis** con un TTL igual al tiempo de vida
restante del token — decisión ya registrada en `ADR-003-jwt-redis.md`, aquí
documentada como parte del conjunto completo de controles de autenticación.

Se aplica **rate limiting de intentos fallidos de login**
(`Backend/src/main/java/com/biopet/security/LoginRateLimiterService.java`,
en memoria, por IP): los primeros cinco fallos consecutivos siguen
respondiendo **401**; el sexto fallo responde **429** con cabecera
`Retry-After` (segundos); los contadores están **aislados por IP** (IPs
distintas no comparten estado) y se **reinician tras un login exitoso**.

Cada evento relevante (login exitoso/fallido/bloqueado, refresh
exitoso/fallido, logout, token revocado detectado) se registra de forma
**estructurada** mediante `AuthenticationAuditService`
(`Backend/src/main/java/com/biopet/security/AuthenticationAuditService.java`)
con el formato `AUTH_AUDIT timestamp=<UTC> event=<EVENTO> result=<RESULTADO>
ip=<IP> subject=<SUJETO>`.

Se configuran **cabeceras de seguridad HTTP** (`X-Frame-Options: DENY`,
`X-Content-Type-Options: nosniff`, `Content-Security-Policy` con
`frame-ancestors 'none'`, `Referrer-Policy: no-referrer`,
`Strict-Transport-Security` solo sobre HTTPS) y **CORS** con origen
concreto y `allowCredentials(true)`, todo en
`Backend/src/main/java/com/biopet/config/SecurityConfig.java`.

Se habilita **HTTPS nativo** mediante el perfil Spring `tls`
(`Backend/src/main/resources/application-tls.yml`), con un conector
adicional HTTP interno en el puerto 8080
(`Backend/src/main/java/com/biopet/config/TomcatDualConnectorConfig.java`)
para tráfico entre contenedores, y el conector HTTPS principal en el
puerto **8443**.

Todas las respuestas de error de este subsistema usan el formato uniforme
**ProblemDetail** (RFC 7807), producido por `GlobalExceptionHandler` y
`ProblemDetailFactory`.

Este documento no reproduce ningún secreto, token, contraseña ni valor real
de cookie: todas las referencias a credenciales de prueba usadas en el
código son valores de desarrollo ya públicos en el propio repositorio
(por ejemplo, en los fixtures de las pruebas), no secretos de producción.

## Alternativas consideradas

1. **Guardar el JWT en `localStorage`.**
   Rechazada por mayor exposición frente a ataques XSS: cualquier script
   inyectado en la página tendría acceso directo y de lectura al token.
   Las cookies `HttpOnly` no son legibles desde JavaScript, lo que reduce
   ese vector.

2. **Enviar el JWT manualmente en `Authorization` desde el frontend
   (leyéndolo de una respuesta JSON y guardándolo en memoria o
   `localStorage`).**
   No seleccionada para el flujo web principal porque obliga al frontend a
   acceder y administrar directamente el valor del token (almacenarlo,
   adjuntarlo en cada petición, limpiarlo en logout), trasladando al
   cliente una responsabilidad de seguridad que el mecanismo de cookies
   resuelve de forma transparente. El soporte de `Authorization: Bearer`
   se mantiene en el backend como mecanismo adicional (útil para clientes
   no-navegador o pruebas), pero no es el flujo principal del frontend web.

3. **Sesiones tradicionales almacenadas completamente en el servidor
   (session id + estado en memoria/BD del lado servidor).**
   No seleccionadas por el enfoque JWT ya adoptado desde entregas
   anteriores y la necesidad de integración con la arquitectura actual
   (API stateless con `SessionCreationPolicy.STATELESS`, ver
   `SecurityConfig`); migrar a sesiones tradicionales habría implicado
   rehacer la autenticación completa sin un beneficio claro dado el alcance
   del proyecto.

4. **Rate limiting distribuido en Redis** (en vez de en memoria local).
   Se reconoce que sería más adecuado si el backend se desplegara con
   múltiples instancias, ya que un limitador en memoria no comparte estado
   entre procesos. No se seleccionó en esta versión porque el rate limiter
   actual es local (`ConcurrentHashMap`) y el despliegue académico de
   BIOPET utiliza una única instancia del backend (ver
   `docker-compose.yml`, servicio `backend` sin replicación).

5. **Certificado TLS emitido por una autoridad certificadora (CA) real.**
   Preferible en un entorno de producción, donde un certificado autofirmado
   generaría advertencias de confianza inaceptables para usuarios reales.
   No utilizado en el entorno académico local, que emplea un certificado
   autofirmado generado reproduciblemente
   (`scripts/generate-dev-keystore.ps1`/`.sh`), suficiente para demostrar
   TLS 1.3 con cifrado AEAD sin depender de un dominio público ni de un
   proveedor de certificados.

## Consecuencias positivas

- Los tokens son inaccesibles desde JavaScript (`HttpOnly`), reduciendo el
  impacto de un XSS exitoso sobre la sesión del usuario.
- Separación clara entre access token (corta duración, uso frecuente) y
  refresh token (mayor duración, uso puntual), limitando la ventana de
  exposición de cada uno.
- El logout revoca realmente el token (vía blacklist en Redis), no
  solo borra la cookie del navegador.
- El control de acceso combina rol y propiedad del recurso, evaluado
  siempre en el backend.
- El rate limiting reduce la efectividad de ataques automatizados de fuerza
  bruta contra el login, con aislamiento por IP y reinicio tras éxito.
- Los eventos de autenticación quedan registrados de forma auditable, sin
  registrar credenciales ni tokens completos.
- La configuración HTTPS es reproducible mediante un script documentado y
  un perfil Spring dedicado, sin pasos manuales ocultos.
- Los errores de este subsistema son consistentes y máquina-legibles
  gracias al formato ProblemDetail uniforme.

## Consecuencias negativas y compromisos

- `SameSite=Strict` puede limitar integraciones legítimas entre distintos
  orígenes (por ejemplo, un flujo de autenticación embebido desde otro
  sitio), al no enviar la cookie en navegación cross-site.
- Las cookies `Secure` requieren que la conexión sea HTTPS; en un entorno
  sin TLS configurado, el navegador no las enviaría de vuelta.
- El certificado local autofirmado genera advertencias de seguridad en el
  navegador y requiere el flag de "inseguro" en herramientas como `curl`;
  es una limitación aceptada del entorno académico, no de producción.
- El rate limiting en memoria no se comparte entre múltiples instancias del
  backend (ver alternativa 4 rechazada arriba).
- La revocación de JWT introduce una dependencia dura de Redis: sin Redis
  disponible, la verificación de revocación no puede completarse.
- Los logs `AUTH_AUDIT` no están integrados con un SIEM centralizado; hoy
  dependen de la recolección manual de los logs del proceso/contenedor.
- Mantener una blacklist de revocación añade estado operativo a una
  arquitectura que, sin ese mecanismo, sería completamente *stateless* —
  es una compensación deliberada a cambio de poder invalidar tokens antes
  de su expiración natural.

## Consideraciones de seguridad

- `HttpOnly` reduce la exposición del token frente a JavaScript malicioso,
  pero no elimina todos los riesgos: un atacante con acceso a la máquina
  del usuario, o que explote una vulnerabilidad distinta (por ejemplo, un
  proxy MITM sin TLS), podría seguir comprometiendo la sesión.
- `SameSite=Strict` ayuda frente a CSRF al no enviar la cookie en
  solicitudes iniciadas desde otro sitio, pero esto no sustituye una
  revisión completa del modelo de amenazas del proyecto; la justificación
  formal de mantener CSRF deshabilitado en `SecurityConfig` se apoya en
  esta cabecera, no en la ausencia de un token CSRF explícito.
- La autorización (rol y propiedad) se valida siempre en el backend
  (`@PreAuthorize`, `MascotaService.verificarPropiedad`) y no depende de
  que el frontend oculte botones o rutas — un cliente que hable
  directamente con la API sin pasar por Angular recibe los mismos 401/403.
- TLS 1.3 protege los datos en tránsito en el entorno configurado
  (`docs/mediciones/sec/A02-cryptography-tls.md`), pero el tráfico interno
  entre el proxy del frontend y el backend, dentro de la red de Docker,
  sigue siendo HTTP simple; esto es una decisión de alcance ya documentada
  en fases anteriores, no un descuido.
- Los logs de auditoría evitan registrar contraseñas, JWT completos,
  cookies o el encabezado `Authorization`, y sanitizan caracteres de
  control para prevenir *log forging* (`docs/mediciones/sec/A09-logging.md`).
- Ninguno de estos controles debe considerarse definitivo: deben revisarse
  nuevamente, junto con la gestión de secretos, antes de cualquier
  despliegue en un entorno de producción real.

## Evidencia y trazabilidad

**Clases principales:**
- `Backend/src/main/java/com/biopet/controller/AuthController.java`
- `Backend/src/main/java/com/biopet/service/AuthService.java`
- `Backend/src/main/java/com/biopet/security/JwtService.java`
- `Backend/src/main/java/com/biopet/security/JwtCookieService.java`
- `Backend/src/main/java/com/biopet/security/JwtAuthenticationFilter.java`
- `Backend/src/main/java/com/biopet/security/TokenBlacklistService.java`
- `Backend/src/main/java/com/biopet/security/LoginRateLimiterService.java`
- `Backend/src/main/java/com/biopet/security/AuthenticationAuditService.java`
- `Backend/src/main/java/com/biopet/config/SecurityConfig.java`
- `Backend/src/main/java/com/biopet/config/TomcatDualConnectorConfig.java`
- `Backend/src/main/resources/application-tls.yml`

**Pruebas automatizadas:**
- `Backend/src/test/java/com/biopet/AuthControllerTest.java`
- `Backend/src/test/java/com/biopet/JwtCookieAuthenticationTest.java`
- `Backend/src/test/java/com/biopet/security/AuthenticationAuditServiceTest.java`
- `Backend/src/test/java/com/biopet/security/LoginRateLimiterServiceTest.java`
- `Backend/src/test/java/com/biopet/security/JwtServiceTest.java`
- `Backend/src/test/java/com/biopet/security/JwtCookieServiceTest.java`
- `Backend/src/test/java/com/biopet/config/TomcatDualConnectorConfigTest.java`
- `Backend/src/test/java/com/biopet/SecurityHeadersTest.java`
- `Backend/src/test/java/com/biopet/SqlInjectionSecurityTest.java`

**Infraestructura y despliegue:**
- `docker-compose.tls.yml`
- `scripts/generate-dev-keystore.ps1` / `scripts/generate-dev-keystore.sh`

**Evidencia OWASP recopilada (Fase 9A):**
- `docs/mediciones/sec/REPORT.md`
- `docs/mediciones/sec/A01-access-control.md`
- `docs/mediciones/sec/A02-cryptography-tls.md`
- `docs/mediciones/sec/A05-security-headers.md`
- `docs/mediciones/sec/A07-authentication.md`
- `docs/mediciones/sec/A09-logging.md`
- `scripts/security-evidence.ps1` / `scripts/security-evidence.sh`

No se incluyen capturas de pantalla en esta fase.

## Limitaciones

- El certificado TLS es autofirmado y de uso exclusivamente académico/local;
  no es válido para un despliegue de producción.
- El rate limiting de login es en memoria y no se comparte entre múltiples
  instancias del backend.
- Los logs de auditoría son locales al proceso/contenedor, sin integración
  con un SIEM centralizado ni alertas automáticas.
- El despliegue documentado y evaluado corresponde a una única instancia del
  backend (`docker-compose.yml`), no a un escenario de alta disponibilidad
  con múltiples réplicas.
- Los secretos actuales (`JWT_SECRET`, contraseñas de base de datos, etc.)
  son valores de desarrollo definidos en `.env`/`.env.example`; un
  despliegue productivo requeriría gestionarlos fuera del repositorio
  (por ejemplo, mediante un gestor de secretos), lo cual no está
  implementado en el alcance académico actual.

## Referencias a otros documentos

- `ADR-003-jwt-redis.md` (decisión específica de revocación de JWT vía Redis).
