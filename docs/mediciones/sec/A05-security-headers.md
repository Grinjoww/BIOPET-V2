# A05 — Security Misconfiguration (cabeceras HTTP y CORS)

## Control implementado

Configurado explícitamente en `Backend/src/main/java/com/biopet/config/SecurityConfig.java`:

```java
.headers(headers -> headers
        .contentTypeOptions(contentType -> {})
        .frameOptions(frame -> frame.deny())
        .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
        .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .preload(true)
                .maxAgeInSeconds(31536000))
        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
)
```

## Cabeceras verificadas (valores exactos)

| Cabecera | Valor |
|---|---|
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `no-referrer` |
| `Content-Security-Policy` | `default-src 'self'; frame-ancestors 'none'; object-src 'none'` |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains ; preload` |

## Comportamiento HTTP frente a HTTPS

`Strict-Transport-Security` **solo** se emite sobre conexiones seguras.
Verificado en `Backend/src/test/java/com/biopet/SecurityHeadersTest.java`:

- `respuestaProtegidaIncluyeCabecerasDeSeguridad`: petición con `secure(true)`
  (HTTPS simulado) a `/api/usuarios/me` sin autenticación → 401, y la
  respuesta incluye las cinco cabeceras de la tabla anterior, con el HSTS
  conteniendo `max-age=31536000`, `includeSubDomains` y `preload`.
- `peticionHttpNoIncluyeHsts`: la misma petición **sin** `secure(true)` →
  sigue siendo 401, siguen presentes `X-Frame-Options`, `nosniff`, CSP y
  `Referrer-Policy`, pero `Strict-Transport-Security` es `null` — confirmado
  con `assertNull`.
- `respuestasPublicasTambienIncluyenCabecerasBasicas`: un endpoint público
  (`POST /api/auth/login`, con `secure(true)`) también recibe las mismas
  cabeceras — no son exclusivas de rutas protegidas.

Reejecutado en vivo contra el stack Docker real (perfil `tls`), ver
`A02-cryptography-tls.md`: HTTP 8080 no incluye `Strict-Transport-Security`;
HTTPS 8443 sí, con el mismo valor exacto.

## CORS

Configurado en `SecurityConfig.corsConfigurationSource()`: origen concreto
tomado de `app.cors.allowed-origins` (en pruebas, `http://localhost:4200`,
igual que `application-test.yml`), `allowCredentials(true)`, métodos
`GET/POST/PUT/DELETE/OPTIONS`. Nunca se usa `*` como origen permitido junto
con credenciales.

| Prueba | Qué demuestra |
|---|---|
| `preflightCorsDesdeOrigenPermitidoAceptaCredenciales` | Preflight `OPTIONS /api/auth/login` con `Origin: http://localhost:4200` responde `Access-Control-Allow-Origin: http://localhost:4200` (nunca `*`), `Access-Control-Allow-Credentials: true`, y `POST` incluido en `Access-Control-Allow-Methods` |
| `preflightCorsDesdeOrigenNoPermitidoEsRechazado` | Preflight con `Origin: https://evil.example` no recibe `Access-Control-Allow-Origin` ni `Access-Control-Allow-Credentials: true` |

## Reproducción

```bash
cd Backend
mvn -Dtest=SecurityHeadersTest test
```

## Limitaciones

- No se audita aquí `Permissions-Policy` ni `Cross-Origin-*` (COOP/COEP/CORP)
  porque no están configuradas explícitamente en `SecurityConfig` y no
  formaban parte del alcance de la Fase 7C.
- Spring Security también añade por defecto `X-XSS-Protection: 0` y
  cabeceras `Cache-Control`/`Pragma`/`Expires`; no se documentan aquí por no
  ser objetivo explícito de esta fase, aunque son visibles en las respuestas
  reales mostradas en `A02-cryptography-tls.md`.
