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

- Fecha (ISO 8601, UTC): `2026-07-31`
- Commit corto usado como base de esta evidencia: `a781fcf`
- Rama: `jaime/evidencias-owasp` (creada desde `main` actualizado)

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
| A01 | Autorización por propietario (`ROLE_DUENO`) y acceso global por rol (`ADMIN`/`VETERINARIO`/`AUXILIAR`); 401 sin autenticación, 403 con autenticación pero sin permiso | `docs/mediciones/sec/A01-access-control.md`, `MascotaControllerTest` | PASS |
| A02 | HTTPS real en `https://localhost:8443` con TLS 1.3 y cifrado AEAD; cookies `HttpOnly`+`Secure`+`SameSite=Strict` | `docs/mediciones/sec/A02-cryptography-tls.md`, ejecución real con `curl.exe`/`openssl s_client` | PASS |
| A03 | Consultas parametrizadas (Spring Data + `@Query` nativa con `:duenioId` enlazado); payloads de inyección tratados como texto/tipo inválido, nunca como SQL | `docs/mediciones/sec/A03-injection.md`, `SqlInjectionSecurityTest` | PASS |
| A05 | Cabeceras HTTP (`X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, CSP, HSTS condicional a HTTPS), CORS con origen concreto | `docs/mediciones/sec/A05-security-headers.md`, `SecurityHeadersTest` | PASS |
| A07 | Login/refresh/logout por cookies, revocación de tokens, rate limiting 401→429 con `Retry-After`, reinicio de contador tras éxito | `docs/mediciones/sec/A07-authentication.md`, `AuthControllerTest`, `JwtCookieAuthenticationTest` | PASS |
| A09 | Eventos `AUTH_AUDIT` estructurados, sin contraseñas/JWT/cookies/JTI, con sanitización anti log-forging | `docs/mediciones/sec/A09-logging.md`, `AuthenticationAuditServiceTest` | PASS |

"PASS" indica que las pruebas y evidencias referenciadas se ejecutaron
realmente y su resultado coincide con el comportamiento documentado — no
implica ausencia total de riesgo residual, que se detalla en cada documento
individual y en las limitaciones de cada categoría.

## Documentos de esta carpeta

- [A01-access-control.md](A01-access-control.md)
- [A02-cryptography-tls.md](A02-cryptography-tls.md)
- [A03-injection.md](A03-injection.md)
- [A05-security-headers.md](A05-security-headers.md)
- [A07-authentication.md](A07-authentication.md)
- [A09-logging.md](A09-logging.md)
- [jacoco-summary.md](jacoco-summary.md)
