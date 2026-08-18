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
`A09-logging.md` de esta misma carpeta (evidencia verificada contra el
código, contra pruebas automatizadas reales y, desde 2026-07-31, contra
peticiones HTTP reales); las salidas que genera `scripts/security-evidence.sh`
(canónico) / `.ps1` en `docs/mediciones/sec/raw/` — `mvn-clean-verify.txt`,
`docker-compose-config.txt`, `docker-compose-ps.txt`, y, por cada uno de los
seis controles, un archivo dedicado: `A01-access-control.txt`, `A02-tls.txt`,
`A03-injection.txt`, `A05-security-headers.txt`, `A07-auth-rate-limit.txt`,
`A09-audit-logs.txt` (nota: esta carpeta **no** está excluida por
`.gitignore` más allá de `.gitkeep`; los archivos que el script genera son
evidencia real, sanitizada, destinada a versionarse, no una salida
puramente local descartable); `curl`/`curl.exe` y `openssl s_client` contra
el stack Docker real; la suite `mvn clean verify`
(`Backend/src/test/java/com/biopet/**`); y los logs estructurados
`AUTH_AUDIT` obtenidos con `docker compose logs backend`.

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
| security_tests_run | Entero | pruebas | 109 | Total de pruebas ejecutadas por `mvn clean verify` (suite completa del módulo, no solo las clases de seguridad; es la única cifra agregada real disponible — ver también la sección de JaCoCo). Sube de 108 a 109 el 2026-08-01 al reemplazar la aserción ambigua de `SqlInjectionSecurityTest.loginConEmailDeInyeccionNoAutentica` (antes aceptaba 401 o 422) por una aserción exacta de 422, y añadir `loginConPayloadLiteralDeLaGuiaDevuelve422` con el payload literal de la guía. |
| failures | Entero | pruebas | 0 | Aserciones fallidas reportadas por Surefire/Failsafe. |
| errors | Entero | pruebas | 0 | Errores no controlados durante la ejecución de pruebas. |
| skipped | Entero | pruebas | 0 | Pruebas omitidas. |
| test_result | Texto (categórico) | — | {BUILD SUCCESS} | Resultado agregado final de `mvn clean verify`, reejecutado el 2026-08-01. |

## Usabilidad SUS (`docs/mediciones/sus/sus-raw.csv`) — responsable: Zaida

Datos crudos de la prueba de usabilidad System Usability Scale (Brooke, 1996),
aplicada a dieciocho participantes externos al equipo (P01–P18; muestra
inicial de la Tercera Entrega P01–P10, ampliada en la Entrega Final con
P11–P18 para cumplir el mínimo n≥15). Instrumento detallado en
`docs/mediciones/sus/instrumento-sus.md`; análisis y cálculo del puntaje
agregado en `scripts/analisis-sus.py`, reporte en
`docs/mediciones/sus/REPORT.md`.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| codigo_participante | Texto (categórico) | — | P01–P18 | Identificador anonimizado del participante; no permite identificarlo individualmente. |
| fecha_iso8601 | Fecha (ISO 8601) | — | AAAA-MM-DD | Fecha en que el participante realizó la prueba. |
| edad | Entero | años | 18–99 | Edad declarada por el participante, con fines demográficos agregados. |
| sexo | Texto (categórico) | — | {F, M} | Sexo declarado por el participante. |
| experiencia_web | Texto (categórico) | — | {ninguna, basica, intermedia, avanzada} | Experiencia previa autodeclarada con aplicaciones web. |
| dispositivo | Texto (categórico) | — | {laptop, computador de escritorio, tablet, celular} | Dispositivo utilizado por el participante durante la prueba: laptop=10, computador de escritorio=4, tablet=2, celular=2 (ver `docs/mediciones/sus/REPORT.md`). |
| Q1_usaria_frecuentemente … Q10_necesito_aprender_mucho_antes | Entero | puntos Likert | 1–5 | Respuesta a cada uno de los diez ítems originales del instrumento SUS (1 = totalmente en desacuerdo, 5 = totalmente de acuerdo). Ítems impares redactados en sentido positivo, pares en sentido negativo, según Brooke (1996). |
| sus_score | Decimal | puntos SUS | 0.0–100.0 | Puntaje SUS agregado por participante, calculado según el método estándar de Brooke: suma de contribuciones de los diez ítems × 2.5. |

## Accesibilidad / Lighthouse (`docs/mediciones/lighthouse/`) — responsable: Zaida

Fuente: archivos crudos `lhci-YYYYMMDD-HHMM-<slug>-runN.json`, producidos por
`npx @lhci/cli autorun` (ver `lighthouserc.js` y `scripts/run-lighthouse.sh`)
contra el frontend Angular servido por el contenedor real
(`http://localhost:4200`), nunca contra `ng serve`. Cada corrida es un LHR
(Lighthouse Result) completo en formato JSON; no se edita manualmente. El
archivo `lhci-YYYYMMDD-HHMM.meta.txt` que acompaña cada lote documenta fecha
ISO 8601, commit hash corto y versiones de herramientas, exigido por el
Bloque B.2 de la guía.

> **Estado a la fecha de este documento:** la corrida real contra el
> contenedor **ya existe y está archivada** (6 corridas oficiales,
> 2026-08-01, ver `docs/mediciones/lighthouse/README.md` y
> `docs/mediciones/lighthouse/raw/`). Este diccionario describía antes el
> esquema sin datos; se completa aquí con los valores medidos reales.
> Pendiente: una corrida adicional en perfil **desktop** (hasta ahora solo
> se auditó perfil móvil) y una nueva corrida móvil que confirme que SEO
> llega a ≥90 tras los dos fixes aplicados a `frontend/` (meta description
> y `robots.txt`, ver `docs/requisitos/SRS.md`, sección 7).

### Identificación de la corrida

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| requestedUrl | Texto (URL) | — | {`http://localhost:4200/login`, `http://localhost:4200/mascotas`} | URL solicitada, tal como se declara en `lighthouserc.js` (`collect.url`). |
| finalUrl | Texto (URL) | — | igual a `requestedUrl` salvo redirección | URL final tras seguir redirecciones; para `/mascotas` sin sesión activa, Lighthouse termina auditando `/login` (authGuard redirige) — comportamiento esperado, documentado en `lighthouserc.js`. |
| fetchTime | Fecha-hora (ISO 8601) | — | coincide con el `STAMP` del archivo | Momento en que Lighthouse ejecutó la auditoría (campo `fetchTime` del LHR). |
| lighthouseVersion | Texto (semver) | — | `^0.14.x` (línea fijada en `run-lighthouse.sh`, `@lhci/cli@0.14.x`) | Versión de la librería Lighthouse embebida en `@lhci/cli`, tomada del propio JSON, no adivinada. |
| userAgent | Texto | — | cadena de Chrome headless | User-Agent del navegador que ejecutó la auditoría (Chromium headless invocado por `lhci`). |
| run_index | Entero | corrida | 0–2 | Índice de la corrida dentro de las 3 exigidas por `numberOfRuns: 3` en `lighthouserc.js`; corresponde al sufijo `runN` del nombre de archivo asignado por `run-lighthouse.sh`. |

### Puntuaciones por categoría

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| categories.performance.score | Decimal | proporción (0–1 en el JSON crudo; ×100 al reportar) | ≥ 0.80 | Puntuación de Performance. Umbral mínimo declarado en `lighthouserc.js`: `categories:performance` ≥ 0.8. |
| categories.accessibility.score | Decimal | proporción (0–1; ×100 al reportar) | ≥ 0.90 | Puntuación de Accessibility. Umbral: ≥ 0.9. |
| categories['best-practices'].score | Decimal | proporción (0–1; ×100 al reportar) | ≥ 0.90 | Puntuación de Best Practices. Umbral: ≥ 0.9. |
| categories.seo.score | Decimal | proporción (0–1; ×100 al reportar) | ≥ 0.90 | Puntuación de SEO. Umbral: ≥ 0.9. |
| assertion_result | Texto (categórico) | — | {PASS, FAIL} | Resultado de `lhci assert` para cada categoría contra el umbral de `lighthouserc.js` (`assert.assertions`); FAIL en cualquier categoría hace fallar `lhci autorun` con código de salida ≠ 0. |

### Métricas crudas de rendimiento (subconjunto de `audits.*.numericValue`)

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| first_contentful_paint_ms | Decimal | ms | según perfil móvil/Slow 4G, sin umbral propio de la guía | `audits['first-contentful-paint'].numericValue`: momento en que aparece el primer contenido visible. |
| largest_contentful_paint_ms | Decimal | ms | sin umbral propio de la guía | `audits['largest-contentful-paint'].numericValue`: momento en que aparece el elemento más grande visible. |
| total_blocking_time_ms | Decimal | ms | sin umbral propio de la guía | `audits['total-blocking-time'].numericValue`: tiempo total en que el hilo principal estuvo bloqueado para el usuario. |
| cumulative_layout_shift | Decimal | adimensional | sin umbral propio de la guía | `audits['cumulative-layout-shift'].numericValue`: medida de estabilidad visual (saltos de layout inesperados). |
| speed_index_ms | Decimal | ms | sin umbral propio de la guía | `audits['speed-index'].numericValue`: rapidez con la que se puebla visualmente el contenido. |
| time_to_interactive_ms | Decimal | ms | sin umbral propio de la guía | `audits.interactive.numericValue`: momento en que la página queda interactiva de forma confiable. |

Estas seis métricas alimentan `categories.performance.score` (con pesos
propios del algoritmo de Lighthouse v10+), pero la guía solo exige umbral
sobre el score agregado de la categoría, no sobre cada métrica individual;
se documentan aquí porque explican variaciones entre corridas.

### Condiciones de la auditoría (configuración aplicada, no medida)

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| perfil | Texto (categórico) | — | {mobile} | `settings.preset` de `lighthouserc.js`; perfil móvil exigido por el Bloque C.5. |
| throttling_method | Texto (categórico) | — | {simulate} | `settings.throttlingMethod`; throttling de red y CPU simulado equivalente a Slow 4G + CPU 4x slowdown (preset por defecto de Lighthouse, no sobreescrito). |
| skipped_audits | Texto (lista) | — | {`uses-http2`} | Auditorías omitidas explícitamente (`settings.skipAudits`); `uses-http2` se omite porque el contenedor de desarrollo sirve HTTP/1.1 vía Nginx sin TLS local, documentado en el propio `lighthouserc.js`. |
| numberOfRuns | Entero | corridas | 3 | Corridas por URL exigidas en `collect.numberOfRuns`, para reducir varianza entre mediciones. |

### Metadata de la medición (`lhci-YYYYMMDD-HHMM.meta.txt`)

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| fecha_iso8601 | Fecha-hora (ISO 8601, UTC) | — | AAAA-MM-DDTHH:MM:SSZ | Fecha/hora de generación del lote de corridas, capturada por `run-lighthouse.sh` con `date -u`. |
| commit_hash_corto | Texto (hash Git) | — | 7 caracteres hexadecimales | `git rev-parse --short HEAD` en el momento de la corrida; ausente ("sin-git") solo si se ejecuta fuera de un repositorio Git, caso que no debe darse en la evidencia final. |
| node_version | Texto (semver) | — | `v18.x`–`v22.x` | Salida de `node --version` en la máquina que ejecutó la auditoría. |
| lighthouse_cli_version | Texto (semver) | — | `^0.14.x` | Salida de `npx @lhci/cli@0.14.x --version`. |
| urls_auditadas | Texto (lista) | — | las dos URL de `collect.url` | Confirmación textual de qué rutas se auditaron en ese lote. |
| corridas_por_url | Entero | corridas | 3 | Debe coincidir con `numberOfRuns` de `lighthouserc.js`, para trazabilidad cruzada entre la config y la evidencia archivada. |

### Resultados medidos actuales

Fuente: `docs/mediciones/lighthouse/raw/manifest.json` y
`docs/mediciones/lighthouse/raw/assertion-results.json` (6 corridas
oficiales, 2026-08-01, perfil móvil simulado). Valores tomados
directamente de los JSON crudos, sin editar.

| Solicitud | Performance | Accessibility | Best Practices | SEO | Assertion SEO (`>= 0.90`) |
|---|---:|---:|---:|---:|---|
| `/login` (3 corridas) | 93–94 | 91 | 100 | 82 | FAIL (`actual: 0.82`) |
| `/mascotas` (3 corridas, redirige a `/login`) | 92 | 91 | 96 | 82 | FAIL (`actual: 0.82`) |

**Causa raíz del FAIL de SEO** (auditorías con `score: 0` en
`categories.seo.auditRefs`, extraídas del reporte JSON): `meta-description`
(sin meta descripción en `frontend/src/index.html`) y `robots-txt`
(`robots.txt` inválido/inexistente). Ambas se corrigieron en este cierre:
meta descripción agregada a `index.html`; `frontend/public/robots.txt`
creado y registrado como `assets` en `angular.json`. **Falta re-ejecutar**
`make lighthouse` para confirmar el nuevo puntaje y archivar la evidencia
actualizada (no se reporta un puntaje proyectado sin la corrida real, seria
inventar datos).

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
| tests_run | Entero | pruebas | 109 | Total de pruebas ejecutadas por `mvn clean verify`, verificado sumando `Tests run:` de todos los `Backend/target/surefire-reports/*.txt` generados localmente (109/109, 0 fallos, 0 errores, reejecutado el 2026-08-01). |
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