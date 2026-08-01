# A07 — Identification and Authentication Failures

## Control implementado

- Login/refresh/logout mediante cookies `HttpOnly`+`Secure`+`SameSite=Strict`
  (`JwtCookieService`), con soporte adicional de header `Authorization: Bearer`.
- Revocación de JWT vía `TokenBlacklistService` (Redis) al hacer logout.
- Rate limiting de login por IP (`LoginRateLimiterService`): en memoria,
  `ConcurrentHashMap`, ventana de 15 minutos, bloqueo de 15 minutos, máximo
  de intentos configurable (`security.rate-limit.login.max-attempts`,
  por defecto `6` → 5 fallos permitidos, el 6º bloquea).
- Auditoría de cada evento (ver `A09-logging.md`).

## Login, refresh y logout con cookies

| Afirmación | Prueba (`AuthControllerTest` salvo que se indique otra clase) |
|---|---|
| Login exitoso emite cookies `access_token`/`refresh_token` con `HttpOnly`/`Secure`/`SameSite=Strict` | `loginExitoso` |
| Credenciales incorrectas responden 401 ProblemDetail, sin cookies | `loginClaveIncorrecta` |
| Refresh con cookie válida emite nueva cookie de access | `refreshCookieValidaEmiteNuevaAccessCookie` |
| Refresh sin cookie responde 401 ProblemDetail | `refreshSinCookieDevuelve401ProblemDetail` |
| Refresh con cookie inválida responde 401 | `refreshCookieInvalidaDevuelve401ProblemDetail` |
| Un access token no sirve como cookie de refresh | `accessTokenNoSirveComoRefreshCookie` |
| Refresh con token ya revocado responde 401 | `refreshCookieRevocadaDevuelve401` |
| Logout con ambas cookies revoca ambos tokens y las elimina (`Max-Age=0`) | `logoutConAmbasCookiesRevocaTokensYLasElimina` |
| Logout sin cookies es idempotente (204, sin revocar nada) | `logoutSinCookiesEsIdempotente` |
| Logout con solo access/solo refresh revoca el correspondiente y elimina ambas cookies | `logoutSoloConAccessCookieRevocaAccessYEliminaAmbas`, `logoutSoloConRefreshCookieRevocaRefreshYEliminaAmbas` |
| Logout con cookies inválidas sigue devolviendo 204 | `logoutConCookiesInvalidasSigueSiendo204` |
| Logout procesa el token válido aunque el otro sea inválido | `logoutProcesaTokenValidoAunqueElOtroSeaInvalido` |

## Access token revocado es rechazado

Prueba dedicada en `Backend/src/test/java/com/biopet/JwtCookieAuthenticationTest.java::accessCookieRevocadaDevuelve401`:
login real, se marca el JTI como revocado (`tokenBlacklistService.isRevoked = true`),
y una petición posterior con esa misma cookie de access responde 401
ProblemDetail (`type=urn:biopet:error:unauthorized`, `title="No autenticado"`).
La misma clase confirma además que un token expirado
(`tokenExpiradoNoInvocaAuditoriaDeRevocacion`) y un refresh token usado como
access (`refreshTokenNoSirveComoAccessCookie`, `refreshTokenComoBearerTampocoInvocaAuditoriaDeRevocacion`)
también son rechazados, cada uno por su propia razón (expiración vs. tipo de
token incorrecto), sin confundirse con "revocado".

## Siete claims estándar del JWT

`Backend/src/main/java/com/biopet/security/JwtService.java::buildToken`
incluye los siete claims JWT registrados por RFC 7519: `iss` (issuer), `sub`
(subject = id del usuario), `aud` (audience), `iat` (issuedAt), `nbf`
(notBefore), `exp` (expiration) y `jti` (id único). Además agrega tres
claims propios no estándar: `email`, `rol` y `typ` (`access`/`refresh`).

## Rate limiting: 5 fallos → 401, 6º fallo → 429

Todas en `AuthControllerTest`:

| Afirmación | Prueba |
|---|---|
| Los primeros 5 fallos desde la misma IP siguen respondiendo 401 | `quintoIntentoFallidoSigueRespondiendo401` |
| El 6º fallo consecutivo responde 429 ProblemDetail (`type=urn:biopet:error:rate-limited`, `title="Demasiados intentos"`) | `sextoIntentoFallidoDevuelve429ProblemDetail` |
| El 429 incluye cabecera `Retry-After` numérica y positiva | Misma prueba, aserción sobre `HttpHeaders.RETRY_AFTER` |
| Una IP ya bloqueada rechaza incluso con credenciales correctas | `ipBloqueadaRechazaInclusoCredencialesCorrectas` |
| IPs distintas no comparten contador de intentos | `intentosDesdeIpsDistintasNoSeAcumulan` |
| Un login exitoso reinicia el contador de fallos de esa IP | `loginExitosoReiniciaContadorDeLaIp` |

Ejemplo real del ProblemDetail 429 (verificado en el propio código de
prueba):

```json
{
  "type": "urn:biopet:error:rate-limited",
  "title": "Demasiados intentos",
  "status": 429,
  "detail": "Se ha excedido el número máximo de intentos fallidos de inicio de sesión. Intente nuevamente más tarde.",
  "instance": "/api/auth/login"
}
```

con cabecera `Retry-After: <segundos>` — nunca se copia un token real en este
documento; el valor de `Retry-After` es un entero de segundos, no un secreto.

## Evidencia HTTP real (curl, no solo prueba JUnit)

Generada el 2026-08-01 (commit `136b707`, cambios de esta tarea aún sin
confirmar) contra el stack Docker real
(perfil `tls`) con `scripts/security-evidence.sh`, guardada íntegra y
sanitizada en [`raw/A07-auth-rate-limit.txt`](raw/A07-auth-rate-limit.txt):

- **Atributos de cookie reales** (valor redactado, atributos visibles):
  ```
  Set-Cookie: access_token=[REDACTADO]; Path=/; Max-Age=3600; Expires=...; Secure; HttpOnly; SameSite=Strict
  Set-Cookie: refresh_token=[REDACTADO]; Path=/api/auth; Max-Age=604800; Expires=...; Secure; HttpOnly; SameSite=Strict
  ```
- **Refresh real** con la cookie de refresh válida → `200`, nueva cookie de
  access emitida.
- **Logout real** → `204`.
- **Prueba de la blacklist Redis sin exponer el token:** antes del logout se
  guarda una copia local (nunca impresa) de la cookie de access; tras el
  logout, esa misma cookie se reenvía contra `GET /api/usuarios/me` →
  **401** — el JTI ya está en la blacklist de Redis (`TokenBlacklistService`),
  confirmado también por el evento `TOKEN_REVOKED` en `A09-audit-logs.txt`.
- **Rate limiting real, misma IP, sin carga agresiva** (6 peticiones en
  total, no más): 5 intentos fallidos con credenciales inválidas → `401`
  cada uno; el 6º → **`429`** con cabecera real `Retry-After: 900`.
- **Cuenta usada para el rate limiting:** una dirección ficticia
  (`example.test`) que nunca llegó a registrarse — el bloqueo es por IP
  (`LoginRateLimiterService`), no por cuenta, así que no hizo falta una
  cuenta real ni una contraseña real equivocada.

**Limitación de esta ejecución (documentada, no oculta):** no se esperó la
ventana de bloqueo de 15 minutos (`security.rate-limit.login.block-duration`)
para demostrar un login exitoso posterior desde la misma IP — excede el
alcance de una ejecución automatizada de este script. La IP usada queda
bloqueada por ~15 minutos tras cada corrida.

## Reproducción

```bash
cd Backend
mvn -Dtest=AuthControllerTest,JwtCookieAuthenticationTest test
```

Evidencia HTTP real (end-to-end, requiere el stack Docker levantado; la
variable de entorno `ADMIN_PASSWORD` es obligatoria):

```bash
ADMIN_PASSWORD='...' scripts/security-evidence.sh
```

## Limitaciones

- El rate limiting es en memoria (`ConcurrentHashMap`), no distribuido: en un
  despliegue con múltiples instancias del backend, cada instancia llevaría su
  propio contador por IP. Esto ya estaba documentado como decisión de diseño
  desde la Fase 6 (sin Redis).
- No se prueba aquí expiración real de la ventana/bloqueo de 15 minutos con
  tiempo de reloj real (las pruebas de `LoginRateLimiterServiceTest` usan un
  reloj inyectable para eso, fuera del alcance de este documento de
  evidencias de integración).
