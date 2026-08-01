# Postman — BIOPET API

Colección y entorno Postman reales y reproducibles para probar la API del
backend de BIOPET (Spring Boot), verificados contra el código actual
(controladores, DTOs, `SecurityConfig`, `GlobalExceptionHandler`,
`ProblemDetailFactory`, `JwtCookieService`, `AuthService`, `MascotaService`,
`application.yml`, `application-tls.yml`) y **ejecutados de punta a punta con
Newman contra el backend real** (ver "Resultado verificado" más abajo).

## Archivos

- [`BIOPET.postman_collection.json`](BIOPET.postman_collection.json) —
  colección Postman (Schema v2.1.0), 7 carpetas, 44 requests.
- [`BIOPET-Local.postman_environment.json`](BIOPET-Local.postman_environment.json) —
  entorno local académico, sin secretos ni credenciales reales (plantilla
  versionada).
- `BIOPET-Local.local.postman_environment.json` — entorno con valores reales
  para ejecutar la colección localmente. **No se versiona** (ver
  `.gitignore`: `docs/postman/*.local.postman_environment.json`); cada
  integrante crea el suyo copiando la plantilla y completando los valores.

Reemplaza a la colección anterior de una entrega previa
(`BIOPET_Entrega1B.postman_collection.json`), que usaba un flujo con
`accessToken`/`adminToken` guardados en variables y `Authorization: Bearer`
— eso ya no refleja el backend actual (autenticación por cookies
`HttpOnly`+`Secure`+`SameSite=Strict`, `ProblemDetail`, rate limiting, TLS).
No existe ninguna colección paralela: este archivo la sustituye por completo.

## Importar en Postman

1. Abrir Postman → **Import** → seleccionar `BIOPET.postman_collection.json`.
2. Copiar `BIOPET-Local.postman_environment.json` a
   `BIOPET-Local.local.postman_environment.json` (mismo directorio) y
   completar ahí los valores reales — **nunca** en la plantilla versionada.
   Importar ese archivo `.local` en Postman.
3. En el selector de entorno (arriba a la derecha), elegir
   **"BIOPET - Local (valores reales, NO versionar)"**.

## Variables que debes completar (en el archivo `.local`)

| Variable | Para qué sirve |
|---|---|
| `duenioEmail` / `duenioPassword` | Cuenta que se crea con el request "Registro (dueno)" (rol ROLE_DUENO real) |
| `segundoDuenioEmail` / `segundoDuenioPassword` | Segundo dueño, usado para los casos de "mascota ajena" |
| `adminEmail` / `adminPassword` | Cuenta ROLE_ADMIN sembrada automáticamente al arrancar el backend (ver más abajo) |
| `veterinarioEmail` / `veterinarioPassword` | Cuenta ROLE_VETERINARIO — sin siembra, alta manual (ver más abajo); si se deja vacío, el login se omite explícitamente (no cuenta como fallo) |
| `auxiliarEmail` / `auxiliarPassword` | Cuenta ROLE_AUXILIAR — sin siembra, alta manual (ver más abajo); mismo comportamiento que arriba si se deja vacío |

**No agregues** `mascotaId`, `mascotaAjenaId`, `duenioUsuarioId` ni
`segundoDuenioUsuarioId` al archivo de entorno. Ver la sección
"Por qué estas cuatro variables NO viven en el entorno" más abajo — es un
error real que rompió la colección y ya está corregido; no debe repetirse.

### Cuenta ADMIN: sembrada automáticamente (DataInitializer)

`Backend/src/main/java/com/biopet/config/DataInitializer.java` define un
`CommandLineRunner` (`seedAdmin`) que se ejecuta en **cada arranque** del
backend y, de forma idempotente (`if (!repo.existsByEmail("admin@biopet.ec"))`),
crea un único usuario:

- **Email** (literal en el código): `admin@biopet.ec`
- **Rol**: `ROLE_ADMIN`
- **Contraseña**: está codificada en el propio archivo y en `db/seed.sql`
  (hash BCrypt precalculado del mismo valor). Es un dato académico de
  desarrollo, no un secreto de producción — ya está documentado en texto
  plano en `db/seed.sql` y en el `README.md` raíz. Aun así, la plantilla
  versionada de esta colección **no copia esa contraseña**: solo el archivo
  `.local` (no versionado) la incluye.

### VETERINARIO y AUXILIAR: sin siembra, alta manual

`DataInitializer` solo crea la cuenta `ROLE_ADMIN` descrita arriba; no siembra
ninguna cuenta `ROLE_VETERINARIO` ni `ROLE_AUXILIAR`. Tampoco existe un
endpoint público para registrarse con esos roles: `POST /api/auth/registro`
**siempre** asigna `ROLE_DUENO`, sin importar el valor de `rol` que se envíe
(`AuthService.registrar` lo ignora explícitamente). Para probar los flujos
que requieren VETERINARIO o AUXILIAR, la única vía real con el código actual
es:

1. Ejecuta "Registro (dueno)" (o "Registro (segundo dueno)") para crear la
   cuenta base.
2. Con acceso directo a PostgreSQL (`docker compose exec postgres psql ...` o
   un cliente SQL), actualiza manualmente esa fila:
   `UPDATE usuarios SET rol = 'ROLE_VETERINARIO' WHERE email = '...';`
   (o `'ROLE_AUXILIAR'`).
3. Completa `veterinarioEmail`/`veterinarioPassword` o
   `auxiliarEmail`/`auxiliarPassword` con esas credenciales en el entorno
   `.local`.

Esto **no aplica a ADMIN**, que ya viene sembrado como se describe arriba. Si
estas variables quedan vacías, los requests "Login (veterinario)" y "Login
(auxiliar)" se omiten explícitamente (`pm.execution.skipRequest()`) con una
aserción que documenta el motivo — no aparecen como fallo.

## Cookie jar automático — no hay JWT manual

Todo el flujo de autenticación usa cookies `access_token`/`refresh_token`
(`HttpOnly`+`Secure`+`SameSite=Strict`), gestionadas automáticamente por el
cookie jar interno de Postman/Newman tras un Login exitoso. La colección
**nunca**:

- guarda un JWT en una variable;
- guarda el valor de una cookie en una variable;
- agrega `Authorization: Bearer` al flujo web principal;
- imprime cookies o tokens en la consola de Postman.

## HTTP vs HTTPS

`baseUrl` apunta **por defecto a `{{baseUrlHttps}}`** (`https://localhost:8443`):
las cookies de sesión se emiten con el atributo `Secure`, por lo que el
cliente HTTP solo las reenvía si la petición original también fue HTTPS. Casi
toda la colección corre sobre HTTPS a propósito por esta razón. Únicamente
dos requests usan `{{baseUrlHttp}}` a propósito, para comprobar que la
cabecera HSTS está ausente sobre HTTP:

- "Health check HTTP" (carpeta 1).
- "Cabeceras de seguridad" (carpeta 5).

**Certificado autofirmado:** el HTTPS local usa un certificado académico
autofirmado (`Backend/certs/biopet-dev.p12`, generado con
`scripts/generate-dev-keystore.ps1`/`.sh`, nunca versionado). Postman
rechazará la conexión por defecto; si hace falta, desactiva temporalmente
**Settings → General → SSL certificate verification** solo en este entorno
académico local, o usa `--insecure` con Newman. Nunca hagas esto contra un
dominio de producción real.

## Orden de ejecución (carpetas 1 a 7, en ese orden)

1. **1. Estado del servicio** — health check HTTP y HTTPS.
2. **2. Autenticación** — Registro (dueño y segundo dueño), Login de cada
   rol, Refresh, y el caso 401 sin sesión.
3. **3. Mascotas** — cada bloque hace su propio Login explícito justo antes
   de necesitarlo (admin → crear/listar; dueño → mascotas propias y casos
   403; admin → acceso global/actualizar/eliminar). No depende de cookies
   dejadas por una carpeta anterior.
4. **4. Resumen o estadísticas** — reutiliza la sesión admin que deja la
   carpeta 3.
5. **5. Seguridad y errores** — 422/400/404 reutilizan la sesión admin; el
   caso 401 limpia las cookies explícitamente antes de correr; el caso 403
   hace un Login (dueño) explícito antes.
6. **6. Rate limiting (login)** — deliberadamente casi al final: bloquea el
   login desde la IP actual durante 15 minutos
   (`security.rate-limit.login.block-duration`), y ese bloqueo afecta a
   **cualquier** intento de login posterior, con credenciales correctas o
   no. Si corriera antes, los re-logins de las carpetas 3 a 5 fallarían con
   429 (así se rompía la colección original).
7. **7. Cierre de sesión** — Logout, el último paso.

No hace falta levantar TLS solo para health check HTTP, pero sí para todo lo
demás:

```bash
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d
```

## Por qué estas cuatro variables NO viven en el entorno

`mascotaId`, `mascotaAjenaId`, `duenioUsuarioId` y `segundoDuenioUsuarioId`
son estado dinámico que los propios scripts de la colección calculan en cada
corrida (`pm.collectionVariables.set(...)`) — **no pertenecen al archivo de
entorno**. Postman resuelve `{{variable}}` con esta precedencia: entorno
**antes que** colección. Si el entorno declara la misma clave (aunque sea con
valor vacío), esa entrada vacía siempre gana sobre el valor que el script
guardó en la colección, y el placeholder en la URL o en el body de una
petición posterior se resuelve como cadena vacía — sin ningún error visible
en el script que lo guardó. Esto rompía sistemáticamente "Crear mascota
(dueno)" y varios requests más (`"duenioId": ` vacío → `400`/`401` en vez
del `201`/`403` esperado), exactamente el síntoma reportado como "los IDs no
se guardaron tras fallar la creación". La causa real no era que la creación
fallara: es que el entorno enmascaraba el valor ya guardado. Corregido
eliminando esas cuatro claves de ambos archivos de entorno; siguen
declaradas (con valor por defecto vacío) únicamente en el `variable` de nivel
colección del propio `.json`, donde no hay ningún entorno que las tape.

## Cómo interpretar los códigos de estado

Todas las respuestas de error usan `application/problem+json`
(`ProblemDetail`, RFC 7807), con `type`, `title`, `status`, `detail` e
`instance`:

- **401** (`urn:biopet:error:unauthorized`, título "No autenticado"): no hay
  sesión válida (sin cookie, cookie inválida o revocada) o credenciales
  incorrectas en login.
- **403** (`urn:biopet:error:forbidden`, título "Acceso denegado"): hay
  sesión válida, pero el rol no tiene permiso, o el dueño intenta acceder a
  una mascota que no es suya.
- **422** (`urn:biopet:error:validation`, título "Error de validación"):
  el body no cumple las anotaciones de Bean Validation del DTO (incluye una
  propiedad `errors` con el detalle por campo).
- **429** (`urn:biopet:error:rate-limited`, título "Demasiados intentos"):
  se superó el máximo de intentos fallidos de login desde esa IP; incluye la
  cabecera `Retry-After` con los segundos restantes.

## Newman

```bash
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d

npx newman run docs/postman/BIOPET.postman_collection.json \
  -e docs/postman/BIOPET-Local.local.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export docs/mediciones/postman/newman-report.json \
  --insecure
```

`--insecure` es necesario por el certificado TLS autofirmado local (ver
"HTTP vs HTTPS" arriba); nunca se usa contra un dominio real.

Si se corre dos veces seguidas dentro de la misma ventana de 15 minutos, la
carpeta "6. Rate limiting (login)" deja la IP bloqueada para el *siguiente*
intento de login del *siguiente* run; reinicia el contenedor del backend
(`docker restart biopet-backend`) para limpiar ese estado en memoria antes de
volver a correr la colección completa, o excluye esa carpeta con
`--folder "1. Estado del servicio" --folder "2. Autenticacion" ...`
(repitiendo `--folder` por cada carpeta que sí quieras incluir).

### Resultado verificado

Ejecutado contra el backend real (`docker compose -f docker-compose.yml -f
docker-compose.tls.yml`, perfil `tls`), con el archivo `.local` completado:

```
requests: 46 (44 items; 2 corresponden a llamadas internas de
  resolución de id vía pm.sendRequest, no a peticiones nuevas del usuario)
assertions: 234, failed: 0
```

Todas las peticiones y aserciones pasaron; el reporte JSON crudo de esa
corrida queda en `docs/mediciones/postman/newman-report.json` (no
versionado por defecto; consérvalo como evidencia si corresponde).

## Collection Runner (Postman de escritorio)

Para ejecutar toda la colección (o una carpeta) de forma secuencial:
**Postman → Collection Runner** → seleccionar `BIOPET - API`, el entorno
`.local`, y las carpetas 1 a 7 en orden. Igual que con Newman, evita correr
dos veces seguidas dentro de los 15 minutos de bloqueo del rate limiter.

## Capturas de pantalla

No se generan en esta fase. Se reservan para una fase final de evidencias.
