# BIOPET — Evidencias de seguridad OWASP (Fase 9A)

## Objetivo

Documentar, con evidencia física y reproducible, el estado real de los controles
de seguridad ya implementados en el backend de BIOPET frente a las categorías
OWASP Top 10 aplicables al alcance actual del proyecto: A01 (Broken Access
Control), A02 (Cryptographic Failures), A03 (Injection), A05 (Security
Misconfiguration), A07 (Identification and Authentication Failures) y A09
(Security Logging and Monitoring Failures).

Este reporte **no implementa controles nuevos**: recopila y referencia
evidencia de código, pruebas automatizadas y ejecuciones reales ya realizadas
en fases anteriores (7A–7E, 8A–8B) del proyecto.

## Alcance

- Backend Spring Boot (`Backend/`), únicamente. No cubre el frontend Angular,
  Redis/caché, k6 ni la infraestructura de PostgreSQL/Docker más allá de lo
  estrictamente necesario para demostrar TLS.
- Controles ya fusionados en la rama `main` al momento de esta auditoría:
  autorización por propietario, JWT por cookies, refresh/logout/revocación,
  rate limiting de login, auditoría de eventos de autenticación (A09),
  cabeceras HTTP (A05), HTTPS/TLS 1.3 nativo, y pruebas contra inyección SQL
  (A03).
- No incluye A04, A06, A08 ni A10: no fueron objeto de fases anteriores y no
  se documentan aquí para no inventar evidencia inexistente.

## Fecha y commit

- Fecha (ISO 8601, UTC) de la evidencia de código/pruebas original: `2026-07-31`
- Commit corto usado como base de esa evidencia: `a781fcf`
- Rama original: `jaime/evidencias-owasp` (creada desde `main` actualizado)
- **Actualización con evidencia HTTP real (A01, A03, A07, A09) y ejecución
  automatizada completa de los seis controles**: fecha `2026-07-31`, commit
  `136b707`, rama `jaime/owasp-evidencias-reales`, generada con
  `scripts/security-evidence.sh` contra el stack Docker real (perfil `tls`).
- **Cierre de la discrepancia A03 (400 vs. 422)**: fecha `2026-08-01`, misma
  rama. Se determinó que el endpoint y payload correctos para demostrar el
  caso exigido por la guía (Bloque C.2, "un campo de búsqueda") es el campo
  `email` de `POST /api/auth/login`, no `duenioId` en
  `/api/mascotas/resumen-especies`. Confirmado con **422** real y
  determinístico en tres payloads distintos, sin modificar código
  productivo — ver `A03-injection.md`.

## Entorno de ejecución

| Componente | Versión observada |
|---|---|
| Sistema operativo | Windows 11 (build `10.0.26200`) |
| Java | OpenJDK `21.0.11` (Eclipse Temurin) |
| Maven | Apache Maven `3.9.16` |
| Docker | Docker Desktop `4.83.0`, Engine `29.6.2` |

## Metodología

1. Inspección directa del código fuente real (controladores, servicios,
   filtros, configuración de seguridad) para confirmar el mecanismo de cada
   control, sin asumir comportamiento no verificado.
2. Ejecución real de la suite de pruebas (`mvn clean verify` desde `Backend/`)
   para confirmar el resultado exacto reportado por JUnit y JaCoCo.
3. Verificación en vivo del stack Docker con el perfil `tls` ya activo
   (`docker-compose.yml` + `docker-compose.tls.yml`), usando `curl.exe` y
   `openssl s_client` para capturar cabeceras, protocolo TLS y cifrado
   negociado exactamente como los recibe un cliente real.
4. Ninguna cifra, código de estado o cabecera de este reporte fue inventada:
   toda afirmación cuantitativa proviene de una prueba automatizada existente
   (referenciada por nombre de clase y método) o de una ejecución real
   documentada en los archivos de detalle de `docs/mediciones/sec/`.

## Limitaciones

- La evidencia de TLS se tomó contra un stack Docker que ya estaba en
  ejecución de una fase anterior (más de 2 horas activo, `biopet-backend`
  en estado `healthy`); no fue necesario reconstruirlo para esta fase, tal
  como permite el alcance de la Fase 9A.
- El certificado TLS es autofirmado y exclusivamente para uso académico/local;
  no representa una cadena de confianza válida para producción.
- La cobertura JaCoCo mide el backend Spring Boot; no incluye frontend ni
  scripts de infraestructura.
- `TokenBlacklistService` (revocación de JWT vía Redis) se prueba siempre con
  `@MockBean` en las pruebas unitarias/MockMvc; su comportamiento real contra
  Redis no se re-verifica en esta fase (ya documentado como limitación en la
  Fase 8A).
- Ningún control aquí descrito se presenta como perfecto o como eliminación
  total de riesgo: cada sección documenta específicamente qué se comprobó y
  qué queda fuera de esa comprobación.

## Resumen por categoría OWASP

| Categoría | Control comprobado | Evidencia | Resultado |
|---|---|---|---|
| A01 | Autorización por propietario (`ROLE_DUENO`) y acceso global por rol (`ADMIN`/`VETERINARIO`/`AUXILIAR`); 401 sin autenticación, 403 con autenticación pero sin permiso | `docs/mediciones/sec/A01-access-control.md`, `MascotaControllerTest`, **evidencia HTTP real** `raw/A01-access-control.txt` | PASS |
| A02 | HTTPS real en `https://localhost:8443` con TLS 1.3 y cifrado AEAD; cookies `HttpOnly`+`Secure`+`SameSite=Strict` | `docs/mediciones/sec/A02-cryptography-tls.md`, ejecución real con `curl.exe`/`openssl s_client`, `raw/A02-tls.txt` | PASS |
| A03 | Consultas parametrizadas (Spring Data + `@Query` nativa con `:duenioId` enlazado); payload de inyección en el campo `email` de login rechazado por Bean Validation con 422 real | `docs/mediciones/sec/A03-injection.md`, `SqlInjectionSecurityTest`, **evidencia HTTP real** `raw/A03-injection.txt` | PASS |
| A05 | Cabeceras HTTP (`X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, CSP, HSTS condicional a HTTPS), CORS con origen concreto | `docs/mediciones/sec/A05-security-headers.md`, `SecurityHeadersTest`, `raw/A05-security-headers.txt` | PASS |
| A07 | Login/refresh/logout por cookies, revocación de tokens, rate limiting 401→429 con `Retry-After`, reinicio de contador tras éxito | `docs/mediciones/sec/A07-authentication.md`, `AuthControllerTest`, `JwtCookieAuthenticationTest`, **evidencia HTTP real** `raw/A07-auth-rate-limit.txt` | PASS |
| A09 | Eventos `AUTH_AUDIT` estructurados, sin contraseñas/JWT/cookies/JTI, con sanitización anti log-forging | `docs/mediciones/sec/A09-logging.md`, `AuthenticationAuditServiceTest`, **evidencia real de contenedor** `raw/A09-audit-logs.txt` | PASS |

"PASS" indica que las pruebas y evidencias referenciadas se ejecutaron
realmente y su resultado coincide **exactamente** con el comportamiento que
exige la guía — no implica ausencia total de riesgo residual, que se
detalla en cada documento individual y en las limitaciones de cada
categoría.

**Historial de A03 (transparencia del proceso, no un problema abierto):**
la primera ronda de evidencia HTTP real (2026-07-31) probó los payloads de
inyección contra `duenioId` (`GET /api/mascotas/resumen-especies`), un
parámetro `Long`, obteniendo **400** (rechazo en el *binding* de Spring MVC)
en vez del **422** que exige la guía — documentado en su momento como
discrepancia conocida, sin ocultarla ni marcarla como PASS. El 2026-08-01 se
determinó que el endpoint correcto para el caso que pide la guía ("un campo
de búsqueda") es el campo `email` de `POST /api/auth/login`: un payload de
inyección ahí no tiene forma de correo válida y es rechazado por Bean
Validation (`@Email`) con **422** real, verificado con tres payloads
distintos. No se modificó código productivo; el cambio fue de endpoint y
payload elegidos para la evidencia. El caso `duenioId`/400 se conserva en
`raw/A03-injection.txt` como evidencia adicional de defensa en profundidad
(un mecanismo de rechazo distinto, igualmente seguro), sin contar como
verificación pass/fail.

## Ejecución automatizada de los seis controles (`scripts/security-evidence.sh`)

El 2026-08-01 (commit `136b707`, con cambios de esta tarea aún sin
confirmar/`commit`) se ejecutó `scripts/security-evidence.sh` de punta a
punta contra el stack Docker real (perfil `tls`): `mvn -B clean verify`
(**109/109 pruebas**, 0 fallos, 0 errores; JaCoCo `LINE 95.87%`/`BRANCH
76.87%`/`COMPLEXITY 80.00%`, todas por encima del umbral 60%),
levantamiento del stack sin `down -v` ni `reset-db`, y una secuencia real
de peticiones HTTP para cada uno de los seis controles, usando dos cuentas
académicas temporales (`example.test`, contraseña aleatoria nunca impresa)
y la cuenta admin semilla (leída obligatoriamente de la variable de entorno
`ADMIN_PASSWORD`, sin valor por defecto en el script). **Las 28 de 28
verificaciones puntuales automatizadas resultaron `CUMPLE`**; el script
terminó con código de salida **0**. El frontend quedó en estado `starting`
durante la ejecución (limitación registrada en `raw/frontend-limitacion.txt`,
sin afectar ninguno de los controles del backend evaluados aquí).

Nota sobre el total de verificaciones: la ronda anterior (2026-07-31)
reportó incorrectamente "21" verificaciones por un error de conteo en el
resumen final del script (un identificador duplicado —`A07.5`— y varias
verificaciones omitidas de la lista impresa, aunque sí se habían ejecutado y
contado correctamente para el código de salida). Se corrigió el script
(identificadores únicos, enumeración completa) antes de esta ejecución; el
total real y verificado es **28**.

## Documentos de esta carpeta

- [A01-access-control.md](A01-access-control.md)
- [A02-cryptography-tls.md](A02-cryptography-tls.md)
- [A03-injection.md](A03-injection.md)
- [A05-security-headers.md](A05-security-headers.md)
- [A07-authentication.md](A07-authentication.md)
- [A09-logging.md](A09-logging.md)
- [jacoco-summary.md](jacoco-summary.md)
