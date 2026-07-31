# Diccionario de datos de mediciones — BIOPET (Tercera Entrega v0.9.0-rc)

Cubre las variables de cada archivo crudo de mediciones bajo `docs/mediciones/`,
según lo exige el Bloque E.3 de la guía de la Tercera Entrega: nombre, tipo de
dato, unidad, rango esperado y significado.

Este documento se organiza por sub-bloque de evidencia. Cada integrante agrega
la sección correspondiente a su área.

## Rendimiento (`docs/mediciones/perf/`) — responsable: Fred

Fuente: `k6-runN-{frio,caliente}.json` (datos crudos de k6), agregados por
`scripts/perf-analysis.py` en `docs/mediciones/perf/REPORT.md`.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| n | Entero | peticiones | 3000–3300 (50 VUs × ~30-35s) | Tamaño de muestra: peticiones HTTP completadas en la corrida. |
| media_ms | Decimal | ms | 5–15 (según frío/caliente) | Media aritmética de `http_req_duration` sobre las n peticiones. |
| mediana_ms | Decimal | ms | 5–10 | Percentil 50 de `http_req_duration` (equivalente a p50_ms). |
| desviacion_ms | Decimal | ms | 4–8 | Desviación estándar muestral de `http_req_duration`. |
| ic95_bajo_ms | Decimal | ms | > 0 | Límite inferior del intervalo de confianza al 95% para la media (distribución t de Student, `scipy.stats.t`). |
| ic95_alto_ms | Decimal | ms | > ic95_bajo_ms | Límite superior del intervalo de confianza al 95% para la media. |
| p50_ms / p90_ms / p95_ms / p99_ms | Decimal | ms | crecientes (p50 ≤ p90 ≤ p95 ≤ p99) | Percentiles de latencia: umbral por debajo del cual cae ese % de las peticiones. Umbral objetivo de la guía: p95 < 200 ms con caché caliente, < 500 ms con caché fría. |
| tasa_error_pct | Decimal | % | 0.0 (obligatorio) | Porcentaje de peticiones con código de error HTTP > 500. La guía exige que sea cero. |
| throughput_rps | Decimal | peticiones/s | 85–95 (a 50 VUs) | Peticiones completadas por segundo durante la corrida. |

## Caché Redis (`docs/mediciones/perf/REPORT.md`, sección "Metadata de la medición") — responsable: Fred

Fuente: `redis-cli INFO stats`, leído de forma aislada con `CONFIG RESETSTAT`
inmediatamente antes de la corrida de referencia.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| keyspace_hits | Entero | peticiones a caché | ≥ 0 | Lecturas resueltas sirviendo un valor ya presente en Redis. |
| keyspace_misses | Entero | peticiones a caché | ≥ 0 | Lecturas no resueltas desde Redis (clave inexistente o expirada); fuerza consulta a PostgreSQL. |
| hit_ratio | Decimal | % | 0–100 (objetivo declarado por el equipo: alto con caché caliente) | `hits / (hits + misses)`. Medido: ~49.98%, más bajo que lo esperado para una corrida en caché caliente — documentado como hallazgo abierto en `REPORT.md`, no oculto. |
| CACHE_TTL_MS | Entero | milisegundos | > 0 (por defecto 300000 = 5 min) | Tiempo de vida de una entrada de caché antes de expirar, externalizado vía variable de entorno (`application.yml` / `.env`), no hardcodeado. |

## Seguridad (`docs/mediciones/sec/`) — responsable: Jaime

Fuentes reales: los documentos `A01-access-control.md`, `A02-cryptography-tls.md`,
`A03-injection.md`, `A05-security-headers.md`, `A07-authentication.md` y
`A09-logging.md` de esta misma carpeta (evidencia ya verificada en la Fase 9A
contra el código y contra pruebas automatizadas reales); las salidas locales
que generan `scripts/security-evidence.ps1`/`.sh` en `docs/mediciones/sec/raw/`
(`mvn-clean-verify.txt`, `docker-compose-config.txt`, `docker-compose-ps.txt`,
`http-8080-headers.txt`, `https-8443-headers.txt` — carpeta con solo `.gitkeep`
versionado, el resto se regenera localmente y no se sube al repositorio);
`curl`/`curl.exe` y `openssl s_client` contra el stack Docker real; la suite
`mvn clean verify` (`Backend/src/test/java/com/biopet/**`); y los logs
estructurados `AUTH_AUDIT` que emite `AuthenticationAuditService`.

### Respuestas HTTP y autorización

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| http_status | Entero | código HTTP | {200, 201, 204, 400, 401, 403, 404, 409, 422, 429} | Código de estado real devuelto por el backend. 200: operación de lectura correcta. 201: creación (`POST /api/auth/registro`, `POST /api/mascotas`). 204: sin cuerpo (`POST /api/auth/logout`, `DELETE /api/mascotas/{id}`). 400: parámetro incompatible con su tipo (`MethodArgumentTypeMismatchException`). 401: sin autenticación válida o credenciales incorrectas. 403: autenticado sin autorización. 404: recurso inexistente. 409: conflicto de datos (`EmailDuplicadoException`, código real adicional al mínimo solicitado, verificado en `GlobalExceptionHandler.emailDuplicado`). 422: validación de Bean Validation fallida. 429: rate limiting de login excedido. |
| content_type | Texto (categórico) | — | {application/json, application/problem+json, application/vnd.spring-boot.actuator.v3+json} | Cabecera `Content-Type` real: `application/json` en respuestas de éxito con cuerpo; `application/problem+json` en todo `ProblemDetail` (fijado explícitamente por `GlobalExceptionHandler`, `ProblemAuthenticationEntryPoint`, `ProblemAccessDeniedHandler`); el tercer valor es el que usa Spring Boot Actuator por negociación de contenido por defecto en `/actuator/health` (verificado en `A02-cryptography-tls.md`). |
| problem_type | Texto (URI, `urn:biopet:error:*`) | — | {validation, not-found, conflict, unauthorized, forbidden, bad-request, rate-limited} | Campo `type` del `ProblemDetail` (`ProblemType`, `Backend/src/main/java/com/biopet/exception/ProblemType.java`). El enum también define `internal` (`urn:biopet:error:internal`), pero ningún `@ExceptionHandler` del código actual lo produce; no se documenta como valor observable. |
| problem_title | Texto | — | {"No autenticado", "Acceso denegado", "Error de validación", "Recurso no encontrado", "Conflicto de datos", "Solicitud inválida", "Parámetro inválido", "Demasiados intentos"} | Campo `title` del `ProblemDetail`, fijo por tipo de excepción (nunca generado dinámicamente a partir de la entrada del usuario). |
| problem_status | Entero | código HTTP | igual a `http_status` para respuestas de error | Campo `status` del `ProblemDetail`, siempre coincide con el código HTTP real de la respuesta. |
| problem_detail | Texto libre | — | mensaje descriptivo, ≤ ~200 caracteres en la práctica | Campo `detail` del `ProblemDetail`. Nunca incluye stack trace ni clases Java; para `MethodArgumentTypeMismatchException` se construye solo con `ex.getName()` (nombre del parámetro), nunca con el valor recibido (`ex.getValue()`) — confirmado en `A03-injection.md`. |
| problem_instance | Texto (ruta relativa) | — | ruta real del recurso, p. ej. `/api/auth/login` | Campo `instance` del `ProblemDetail`, generado con `request.getRequestURI()` (`ProblemDetailFactory.build`). |

### Rate limiting

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| failed_attempt_number | Entero | intento consecutivo | 1–6 | Número ordinal del intento fallido de login desde la misma IP (dato agregado por quien reproduce la evidencia, no un campo devuelto por la API). |
| login_http_status | Entero | código HTTP | {401, 429} | 401 en los intentos 1 a 5 (fallo de credenciales); 429 en el intento 6 (bloqueo por `LoginRateLimiterService`, `maxAttempts` por defecto = 6). |
| retry_after_seconds | Entero | segundos | > 0 | Valor de la cabecera `Retry-After`, presente únicamente en la respuesta 429 (`RateLimitExcedidoException.getSegundosRestantes()`). |
| client_ip_scope | Texto (categórico) | — | {aislado por IP} | El contador de fallos es independiente por IP (`ConcurrentHashMap<String, Estado>` con la IP normalizada como clave); IPs distintas nunca comparten contador. |
| limiter_state | Texto (categórico) | — | {permitido, bloqueado} | Estado agregado derivado de `Estado.bloqueadaHasta()` en `LoginRateLimiterService`: `null` = permitido, no `null` (y aún vigente) = bloqueado. Se reinicia (`reiniciar(ip)`) tras un login exitoso; el almacenamiento es en memoria, por instancia del backend (no distribuido — documentado como limitación desde la Fase 6 y en `A07-authentication.md`). |

### TLS y criptografía

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| tls_protocol | Texto | — | {TLSv1.3} | Protocolo negociado, verificado con `openssl s_client -connect localhost:8443 -tls1_3` (`enabled-protocols: TLSv1.3` en `application-tls.yml`). |
| tls_cipher | Texto | — | {TLS_AES_256_GCM_SHA384} | Suite de cifrado negociada (AEAD), observada en la misma ejecución de `openssl s_client`. |
| certificate_subject_alt_name | Texto | — | {DNS:localhost, IP Address:127.0.0.1} | SAN del certificado servido, generado por `scripts/generate-dev-keystore.ps1`/`.sh` con `-ext "SAN=dns:localhost,ip:127.0.0.1"`. |
| certificate_type | Texto (categórico) | — | {autofirmado, académico/local, PKCS12} | El certificado nunca forma parte de una cadena de confianza válida (`verify error:num=18:self-signed certificate`); no apto para producción. |
| https_port | Entero | puerto TCP | {8443} | Puerto del conector HTTPS principal bajo el perfil `tls` (`server.port`, `application-tls.yml`). |
| http_port | Entero | puerto TCP | {8080} | Puerto del conector HTTP adicional/interno (`TomcatDualConnectorConfig`, `tls.http-port`). |
| hsts_present | Booleano | — | {true en HTTPS, false/ausente en HTTP} | La cabecera `Strict-Transport-Security` solo se emite sobre HTTPS; su ausencia sobre HTTP 8080 es el comportamiento correcto esperado, no un defecto (verificado con `curl.exe -i` contra ambos puertos en `A02-cryptography-tls.md`). |

No se documentan aquí valores de la contraseña del keystore, del certificado ni de ninguna clave privada: `Backend/certs/*` no se versiona (excluido por `.gitignore` salvo `.gitkeep`).

### Cookies de autenticación

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| cookie_http_only | Booleano | — | {true} | Atributo `HttpOnly` de `access_token` y `refresh_token` (`JwtCookieService.writeCookie`), impide lectura desde JavaScript del navegador. |
| cookie_secure | Booleano | — | {true} | Atributo `Secure` (`security.cookie.secure=true`), la cookie solo viaja sobre HTTPS. |
| cookie_same_site | Texto (categórico) | — | {Strict} | Atributo `SameSite` (`security.cookie.same-site=Strict`, `application.yml`). |
| cookie_path | Texto | — | {"/", "/api/auth"} | `Path=/` para `access_token`; `Path=/api/auth` para `refresh_token` (limita el envío del refresh token a las rutas de autenticación). |
| cookie_max_age | Entero | segundos | access ≈ 3600 (1 h); refresh ≈ 604800 (7 días), salvo sobreescritura por variable de entorno | Derivado de `security.jwt.expiration-ms`/`refresh-expiration-ms` (`application.yml`, valores por defecto en milisegundos, convertidos a segundos por `Duration.ofMillis(...)` en `JwtCookieService`). No es un secreto: es un parámetro de configuración de vigencia, no el contenido de la cookie. |

No se guarda ni se describe en ningún documento el valor real de una cookie ni de un JWT — únicamente sus atributos y metadatos, tal como exige esta sección.

### Cabeceras de seguridad

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| x_frame_options | Texto | — | {DENY} | Cabecera fija (`SecurityConfig`, `frameOptions(frame -> frame.deny())`), previene *clickjacking*. |
| x_content_type_options | Texto | — | {nosniff} | Cabecera fija, evita *MIME sniffing* del navegador. |
| referrer_policy | Texto | — | {no-referrer} | Cabecera fija (`ReferrerPolicy.NO_REFERRER`). |
| content_security_policy | Texto | — | {`default-src 'self'; frame-ancestors 'none'; object-src 'none'`} | Valor exacto configurado en `SecurityConfig.securityFilterChain`. |
| strict_transport_security | Texto | — | {`max-age=31536000 ; includeSubDomains ; preload`} sobre HTTPS; ausente sobre HTTP | Ver `hsts_present` arriba; mismo control, expresado aquí como el valor textual completo de la cabecera. |
| retry_after | Entero | segundos | > 0, solo en 429 | Redundante con `retry_after_seconds` de la tabla de rate limiting; se repite aquí porque también es, en sentido estricto, una cabecera de seguridad de la respuesta. |

Estas cinco cabeceras (más HSTS condicional) se emiten en **todas** las
respuestas, incluidas las públicas (`POST /api/auth/login`) — no son
exclusivas de rutas autenticadas (`A05-security-headers.md`,
`respuestasPublicasTambienIncluyenCabecerasBasicas`).

### Auditoría A09

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| audit_timestamp | Texto (ISO 8601, UTC) | — | `Instant.now()` con sufijo `Z` | Marca de tiempo de cada línea `AUTH_AUDIT`; siempre UTC por ser un `java.time.Instant`. |
| audit_event | Texto (categórico) | — | {LOGIN_SUCCESS, LOGIN_FAILURE, LOGIN_RATE_LIMITED, REFRESH_SUCCESS, REFRESH_FAILURE, LOGOUT_SUCCESS, TOKEN_REVOKED} | Nombre del evento, fijado por el método invocado en `AuthenticationAuditService`. |
| audit_result | Texto (categórico) | — | {SUCCESS, FAILURE, BLOCKED} | Resultado normalizado; nunca un valor libre. |
| audit_ip | Texto | — | IP real observada, o `"unknown"` | Dirección remota (`HttpServletRequest.getRemoteAddr()`), sanitizada (sin caracteres de control) y truncada a 200 caracteres. |
| audit_subject | Texto | — | email verificado, o `"unknown"` | Identidad del sujeto del evento; para eventos exitosos es el email ya autenticado, nunca un dato sin verificar. |
| audit_level | Texto (categórico) | — | {INFO, WARN} | INFO para `LOGIN_SUCCESS`/`REFRESH_SUCCESS`/`LOGOUT_SUCCESS`; WARN para `LOGIN_FAILURE`/`LOGIN_RATE_LIMITED`/`REFRESH_FAILURE`/`TOKEN_REVOKED`. |
| sanitized_value_length | Entero | caracteres | 0–200 | Longitud máxima de `ip`/`subject` tras sanitizar (`LONGITUD_MAXIMA = 200` en `AuthenticationAuditService.normalizar`). |
| unknown_fallback | Texto | — | {"unknown"} | Valor literal fijo usado cuando `ip` o `subject` llegan `null` o en blanco. |

**No son variables registradas** en `AUTH_AUDIT` (y por lo tanto no forman
parte de este diccionario como datos capturables): contraseña, JWT completo,
cookie completa, JTI, ni el encabezado `Authorization`. Estructuralmente,
`AuthenticationAuditService` solo acepta `ip` y `subject` como parámetros en
cada método público — no existe ninguna vía de código para que esos valores
lleguen al logger (`A09-logging.md`, `noRegistraDatosSensibles`).

### Pruebas de seguridad

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| security_tests_run | Entero | pruebas | 107 | Total de pruebas ejecutadas por `mvn clean verify` (suite completa del módulo, no solo las clases de seguridad; es la única cifra agregada real disponible — ver también la sección de JaCoCo). |
| failures | Entero | pruebas | 0 | Aserciones fallidas reportadas por Surefire/Failsafe. |
| errors | Entero | pruebas | 0 | Errores no controlados durante la ejecución de pruebas. |
| skipped | Entero | pruebas | 0 | Pruebas omitidas. |
| test_result | Texto (categórico) | — | {BUILD SUCCESS} | Resultado agregado final de `mvn clean verify`, reejecutado el 2026-07-31 (`jacoco-summary.md`). |

## Usabilidad SUS (`docs/mediciones/sus/`) — responsable: Zaida

_Pendiente: sección a completar por Zaida con las variables del instrumento SUS
(puntuación por ítem, escala 1–5, puntuación agregada 0–100, IC 95%, perfil de
participantes)._

## Accesibilidad / Lighthouse (`docs/mediciones/lighthouse/`) — responsable: Zaida

_Pendiente: sección a completar por Zaida con las variables de cada auditoría
`lhci` (Performance, Accessibility, Best Practices, SEO — escala 0–100, umbral
mínimo declarado en `lighthouserc.js`)._

## Cobertura JaCoCo (`Backend/target/site/jacoco/`, resumida en `docs/mediciones/sec/jacoco-summary.md`) — responsable: Jaime

Corrección de ruta: esta sección originalmente referenciaba
`docs/mediciones/jacoco/`, una carpeta que no existe en el repositorio. La
fuente real de los datos crudos es local y no versionada:
`Backend/target/site/jacoco/jacoco.xml` (reporte máquina-legible, usado para
las cifras de esta sección), `Backend/target/site/jacoco/index.html` (reporte
navegable) y `Backend/target/jacoco.exec` (datos crudos binarios). El resumen
versionado equivalente está en `docs/mediciones/sec/jacoco-summary.md`. La
configuración del plugin (exclusiones y regla de umbral) está en
`Backend/pom.xml` (`jacoco-maven-plugin`, ejecuciones `prepare-agent`,
`report` y `check`). **`Backend/target/` no se versiona** (excluido por
`.gitignore`); estos artefactos se regeneran en cada `mvn clean verify` y no
deben copiarse al repositorio.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| counter_type | Texto (categórico) | — | {LINE, BRANCH, COMPLEXITY, INSTRUCTION, METHOD, CLASS} | Tipo de contador de JaCoCo (atributo `type` de cada `<counter>` en `jacoco.xml`). Los tres primeros tienen regla de umbral automática; los tres últimos se informan pero no tienen regla de fallo. |
| covered | Entero | elementos del tipo de contador | ≥ 0 | Elementos cubiertos por al menos una ejecución de prueba (atributo `covered`). |
| missed | Entero | elementos del tipo de contador | ≥ 0 | Elementos no cubiertos por ninguna prueba (atributo `missed`). |
| total | Entero | elementos del tipo de contador | = covered + missed | Total de elementos analizados de ese tipo, tras aplicar las exclusiones del plugin. |
| covered_ratio | Decimal | proporción | 0–1 | `covered / total`. Es el valor que compara `jacoco:check` contra `minimum_required_ratio` (regla `COVEREDRATIO`). |
| covered_percentage | Decimal | % | 0–100 | `covered_ratio × 100`; forma en que se reportan las cifras en `jacoco-summary.md`. |
| minimum_required_ratio | Decimal | proporción | 0.60 (LINE, BRANCH, COMPLEXITY) | Umbral mínimo configurado en la regla `BUNDLE` de `Backend/pom.xml`; no existe umbral configurado para INSTRUCTION, METHOD ni CLASS. |
| check_result | Texto (categórico) | — | {PASS, FAIL} | Resultado de `jacoco:check` para un contador con regla: PASS si `covered_ratio ≥ minimum_required_ratio`, FAIL en caso contrario (hace fallar `mvn verify` con el listado de clases incumplidoras). |

### Resultados medidos actuales

Verificados directamente contra `Backend/target/site/jacoco/jacoco.xml`
generado localmente (contadores de nivel `report`, alcance `BUNDLE`) y
contra `docs/mediciones/sec/jacoco-summary.md`:

| counter_type | covered | missed | total | covered_percentage | Regla de umbral |
|---|---|---|---|---|---|
| LINE | 534 | 23 | 557 | 95.87 % | ≥ 60 % |
| BRANCH | 103 | 31 | 134 | 76.87 % | ≥ 60 % |
| COMPLEXITY | 172 | 43 | 215 | 80.00 % | ≥ 60 % |
| INSTRUCTION | 2225 | 148 | 2373 | 93.76 % | Sin regla de fallo (informativo) |
| METHOD | 133 | 15 | 148 | 89.86 % | Sin regla de fallo (informativo) |
| CLASS | 26 | 0 | 26 | 100.00 % | Sin regla de fallo (informativo) |

### Resultado de la ejecución de pruebas asociada

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| tests_run | Entero | pruebas | 107 | Total de pruebas ejecutadas por `mvn clean verify`, verificado sumando `Tests run:` de todos los `Backend/target/surefire-reports/*.txt` generados localmente. |
| test_failures | Entero | pruebas | 0 | Suma de `Failures:` de los mismos reportes. |
| test_errors | Entero | pruebas | 0 | Suma de `Errors:` de los mismos reportes. |
| test_skipped | Entero | pruebas | 0 | Suma de `Skipped:` de los mismos reportes. |
| coverage_check_met | Booleano | — | true | Refleja el mensaje real `[INFO] All coverage checks have been met.` de la ejecución `jacoco:check`. |
| build_result | Texto (categórico) | — | {BUILD SUCCESS} | Resultado final de Maven para `mvn clean verify`. |

### Aclaraciones

- El umbral automático de `jacoco:check` aplica a un único `rule` de alcance
  `BUNDLE` (todo el módulo `biopet-backend`, no por paquete ni por clase).
- Solo LINE, BRANCH y COMPLEXITY tienen `minimum=0.60` configurado; si
  cualquiera de los tres cae por debajo, `mvn verify` falla.
- INSTRUCTION, METHOD y CLASS se informan en `jacoco.xml`/`jacoco-summary.md`
  porque ayudan a interpretar el reporte, pero no participan en la regla de
  fallo del build.
- Las exclusiones de cobertura (`BiopetApplication`, `dto/**`, `entity/Rol`,
  `repository/ResumenEspecie`, `config/OpenApiConfig`) están justificadas
  clase por clase en `docs/mediciones/sec/jacoco-summary.md`; no se repiten
  aquí para no duplicar contenido ya versionado.
- Estos porcentajes miden cobertura de pruebas, no ausencia de errores ni de
  vulnerabilidades: una línea cubierta por una prueba no implica que esté
  libre de defectos.
- Todos los artefactos de `Backend/target/` (incluido `jacoco.xml`) se
  regeneran localmente con `cd Backend && mvn clean verify`; no se versionan
  y no deben copiarse al repositorio.