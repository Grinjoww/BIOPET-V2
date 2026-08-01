# A09 — Security Logging and Monitoring Failures

## Control implementado

`Backend/src/main/java/com/biopet/security/AuthenticationAuditService.java`
(SLF4J + Logback, sin dependencias nuevas) registra siete eventos
estructurados en una sola línea:

- `LOGIN_SUCCESS` (INFO)
- `LOGIN_FAILURE` (WARN)
- `LOGIN_RATE_LIMITED` (WARN)
- `REFRESH_SUCCESS` (INFO)
- `REFRESH_FAILURE` (WARN)
- `LOGOUT_SUCCESS` (INFO)
- `TOKEN_REVOKED` (WARN)

## Formato exacto

```
AUTH_AUDIT timestamp=<UTC> event=<EVENTO> result=<RESULTADO> ip=<IP> subject=<SUJETO>
```

`timestamp` es un `Instant.now()` (siempre UTC por definición de `Instant`).
`result` es una de `SUCCESS`/`FAILURE`/`BLOCKED`, fijada por el propio código
según el método invocado — nunca un valor libre.

Ejemplo real capturado en ejecución de pruebas (sin datos sensibles):

```
AUTH_AUDIT timestamp=2026-07-30T23:25:34.191226600Z event=LOGIN_FAILURE result=FAILURE ip=unknown subject=unknown
AUTH_AUDIT timestamp=2026-07-30T23:25:34.819240900Z event=LOGIN_SUCCESS result=SUCCESS ip=127.0.0.1 subject=jaime@biopet.com
AUTH_AUDIT timestamp=2026-07-30T22:11:40.941Z event=LOGIN_RATE_LIMITED result=BLOCKED ip=203.0.113.12 subject=usuario@biopet.com
```

## INFO frente a WARN

- **INFO**: eventos de resultado esperado/normal (`LOGIN_SUCCESS`,
  `REFRESH_SUCCESS`, `LOGOUT_SUCCESS`).
- **WARN**: eventos que representan un intento fallido o bloqueado
  (`LOGIN_FAILURE`, `LOGIN_RATE_LIMITED`, `REFRESH_FAILURE`, `TOKEN_REVOKED`)
  — todos ameritan revisión si se agregan en volumen inusual.

## Sanitización y prevención de log forging

`AuthenticationAuditService.normalizar()`:

1. `null`/blank → `"unknown"` (nunca se registra un campo vacío ambiguo).
2. `valor.replaceAll("\\p{Cntrl}", "")` elimina **todos** los caracteres de
   control Unicode (incluye `\r`, `\n`, `\t`) en una sola operación — un
   atacante no puede inyectar un salto de línea falso para simular una
   segunda línea de log o falsificar un evento distinto.
3. Truncado a un máximo de 200 caracteres (`LONGITUD_MAXIMA`), para evitar
   crecimiento descontrolado del log por un campo anómalamente largo.

Prueba dedicada:
`AuthenticationAuditServiceTest.eliminaCaracteresDeControlParaEvitarLogForging`
envía una IP y un `subject` con `\r`, `\n` y `\t` embebidos, y confirma que
el mensaje final **no** contiene ninguno de esos caracteres y sigue siendo
una sola línea (`mensaje.split("\\R", -1).length == 1`).

## `unknown` para valores ausentes

`AuthenticationAuditServiceTest.valoresNulosSeNormalizanComoUnknown`: al
llamar `loginFallido(null, null)`, el mensaje resultante contiene
`ip=unknown subject=unknown`.

## Ausencia de datos sensibles

`AuthenticationAuditServiceTest.noRegistraDatosSensibles` confirma que el
mensaje generado no contiene una contraseña de prueba
(`"ClaveSecreta123*"`), ni `access_token`, ni `refresh_token`, ni `Bearer` —
y estructuralmente, el servicio **solo** acepta dos parámetros (`ip`,
`subject`) en cada método público, por lo que no existe ninguna vía para que
una contraseña, un JWT completo, una cookie o un JTI lleguen al logger: esas
piezas de información nunca se le pasan como argumento en ningún punto de
integración (`AuthService`, `JwtAuthenticationFilter`).

El header `Authorization` tampoco se registra nunca: `JwtAuthenticationFilter`
solo pasa el `email` ya verificado criptográficamente como `subject`, nunca
el token ni el encabezado completo.

## Eventos por punto de integración

| Evento | Dónde se registra |
|---|---|
| `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGIN_RATE_LIMITED` | `AuthService.login` |
| `REFRESH_SUCCESS` / `REFRESH_FAILURE` | `AuthService.refresh` |
| `LOGOUT_SUCCESS` | `AuthService.logout` (exactamente una vez por petición, aunque revoque hasta dos tokens) |
| `TOKEN_REVOKED` | `JwtAuthenticationFilter.doFilterInternal`, solo cuando el JWT ya pasó validación criptográfica, es de tipo `access`, y su JTI está en la blacklist |

## Pruebas que lo demuestran

- Unitarias: `Backend/src/test/java/com/biopet/security/AuthenticationAuditServiceTest.java`
  (10 pruebas: una por evento + normalización + anti log-forging + ausencia
  de datos sensibles).
- Integración: `AuthControllerTest.loginExitosoInvocaAuditoriaDeExitoUnaVez`,
  `loginFallidoInvocaAuditoriaDeFalloUnaVez`,
  `sextoIntentoInvocaAuditoriaDeBloqueoYNoDeFalloParaEseIntento`,
  `ipBloqueadaInvocaAuditoriaDeBloqueoYNoAutentica` (verifican, vía
  `@MockBean` + Mockito, cuántas veces y con qué IP se invoca cada método);
  `JwtCookieAuthenticationTest.accessCookieRevocadaDevuelve401` (verifica
  `tokenRevocado` invocado exactamente una vez con el subject correcto, y
  `never()` en los demás casos de token inválido/expirado/tipo incorrecto).

## Evidencia real desde `docker compose logs backend`

Generada el 2026-08-01 (commit `136b707`, cambios de esta tarea aún sin
confirmar) contra el stack Docker real,
ejecutando `docker compose logs backend | grep AUTH_AUDIT` tras la secuencia
de peticiones HTTP de A01/A03/A07 (`scripts/security-evidence.sh`), guardada
íntegra en [`raw/A09-audit-logs.txt`](raw/A09-audit-logs.txt) — email
truncado a las cuentas académicas de prueba (`example.test`), sin cookies,
JWT, JTI, contraseñas ni `Authorization` en ninguna línea:

```
AUTH_AUDIT event=LOGIN_SUCCESS result=SUCCESS ip=172.22.0.1 subject=qa.owasp.a01.b.<...>@example.test
AUTH_AUDIT event=LOGIN_FAILURE result=FAILURE ip=172.22.0.1 subject=qa.owasp.a07.ratelimit.<...>@example.test
AUTH_AUDIT event=LOGIN_RATE_LIMITED result=BLOCKED ip=172.22.0.1 subject=qa.owasp.a07.ratelimit.<...>@example.test
AUTH_AUDIT event=REFRESH_SUCCESS result=SUCCESS ip=172.22.0.1 subject=qa.owasp.a01.b.<...>@example.test
AUTH_AUDIT event=LOGOUT_SUCCESS result=SUCCESS ip=172.22.0.1 subject=admin@biopet.ec
AUTH_AUDIT event=TOKEN_REVOKED result=BLOCKED ip=172.22.0.1 subject=qa.owasp.a01.b.<...>@example.test
```

Cinco de los siete eventos posibles quedaron registrados en esta corrida
(falta `REFRESH_FAILURE`, honestamente marcado como ausente en el archivo
crudo — no se fuerza artificialmente). Confirma, contra el contenedor real
(no contra un logger de pruebas), el mismo formato exacto documentado
arriba: `timestamp`, `event`, `result`, `ip` e IP de contenedor Docker
(`172.22.0.1`, la puerta de enlace del bridge, no `127.0.0.1`, porque el
tráfico llega desde fuera del contenedor vía el puerto publicado 8443) y
`subject` verificado, sin ningún dato sensible.

## Reproducción

```bash
cd Backend
mvn -Dtest=AuthenticationAuditServiceTest,AuthControllerTest,JwtCookieAuthenticationTest test
```

Evidencia real desde el contenedor (end-to-end, requiere el stack Docker
levantado; la variable de entorno `ADMIN_PASSWORD` es obligatoria):

```bash
ADMIN_PASSWORD='...' scripts/security-evidence.sh
```

## Limitación actual

Los eventos `AUTH_AUDIT` se escriben únicamente en el log local del proceso
(stdout/consola de Spring Boot, o `docker logs biopet-backend` en el stack
Docker) — **no existe todavía integración con un SIEM centralizado**, ni
persistencia estructurada (por ejemplo, a un índice de logs o base de datos
de auditoría separada). En el estado actual, la trazabilidad depende de que
alguien recolecte y conserve esos logs por su cuenta; no hay alertas
automáticas ni retención garantizada más allá de lo que gestione el propio
contenedor/host.
