# Postman — BIOPET API

Colección y entorno Postman reales y reproducibles para probar la API del
backend de BIOPET (Spring Boot), verificados contra el código actual
(controladores, DTOs, `SecurityConfig`, `GlobalExceptionHandler`,
`ProblemDetailFactory`, `JwtCookieService`, `AuthService`, `MascotaService`,
`application.yml`, `application-tls.yml`).

## Archivos

- [`BIOPET.postman_collection.json`](BIOPET.postman_collection.json) —
  colección Postman (Schema v2.1.0), 5 carpetas, 40 requests.
- [`BIOPET-Local.postman_environment.json`](BIOPET-Local.postman_environment.json) —
  entorno local académico, sin secretos ni credenciales reales.

Reemplaza a la colección anterior de una entrega previa
(`BIOPET_Entrega1B.postman_collection.json`), que usaba un flujo con
`accessToken`/`adminToken` guardados en variables y `Authorization: Bearer`
— eso ya no refleja el backend actual (autenticación por cookies
`HttpOnly`+`Secure`+`SameSite=Strict`, `ProblemDetail`, rate limiting, TLS).
No existe ninguna colección paralela: este archivo la sustituye por completo.

## Importar en Postman

1. Abrir Postman → **Import** → seleccionar
   `BIOPET.postman_collection.json` y `BIOPET-Local.postman_environment.json`.
2. En el selector de entorno (arriba a la derecha), elegir
   **"BIOPET - Local académico (plantilla sin secretos)"**.

## Variables que debes completar

El entorno se importa con **contraseñas y correos vacíos** a propósito. La
única excepción conceptual es `ROLE_ADMIN`, que sí tiene una cuenta
académica sembrada automáticamente por el backend (ver más abajo); aun así,
la variable `adminPassword` se entrega vacía y debes completarla tú
localmente. Antes de ejecutar nada, completa en el entorno:

| Variable | Para qué sirve |
|---|---|
| `duenioEmail` / `duenioPassword` | Cuenta que se crea con el request "Registro (dueno)" (rol ROLE_DUENO real) |
| `segundoDuenioEmail` / `segundoDuenioPassword` | Segundo dueño, usado para los casos de "mascota ajena" |
| `adminEmail` / `adminPassword` | Cuenta ROLE_ADMIN sembrada automáticamente al arrancar el backend (ver más abajo) — completa solo la contraseña, localmente |
| `veterinarioEmail` / `veterinarioPassword` | Cuenta ROLE_VETERINARIO — sin siembra, alta manual (ver más abajo) |
| `auxiliarEmail` / `auxiliarPassword` | Cuenta ROLE_AUXILIAR — sin siembra, alta manual (ver más abajo) |

Variables que se completan solas (déjalas vacías, los scripts de test las
rellenan): `mascotaId`, `mascotaAjenaId`, `duenioUsuarioId`,
`segundoDuenioUsuarioId`. Estas dos últimas no estaban en la lista original
de variables solicitada; se añadieron porque `MascotaRequest.duenioId` exige
el id numérico real de un usuario `ROLE_DUENO`, y no hay forma de crear una
mascota sin él.

### Cuenta ADMIN: sembrada automáticamente (DataInitializer)

`Backend/src/main/java/com/biopet/config/DataInitializer.java` define un
`CommandLineRunner` (`seedAdmin`) que se ejecuta en **cada arranque** del
backend y, de forma idempotente (`if (!repo.existsByEmail("admin@biopet.ec"))`),
crea un único usuario:

- **Email** (literal en el código): `admin@biopet.ec`
- **Rol**: `ROLE_ADMIN`
- **Contraseña**: está codificada en el propio archivo (línea 21, dentro de
  la llamada a `enc.encode(...)`) y coincide con la tabla "Credenciales
  de prueba" del `README.md` raíz del proyecto. Este documento no repite ese
  valor literal. Existe además un mecanismo
  independiente y consistente para el mismo propósito: `db/seed.sql`
  (montado como script de inicialización de PostgreSQL en
  `docker-compose.yml`), que inserta la misma cuenta con un hash BCrypt
  precalculado del mismo valor.

Este es un dato académico de desarrollo, no un secreto de producción — ya
está documentado en texto plano en el `README.md` raíz y en
`docs/etica/ETHICS.md`. Aun así, esta colección y su entorno **no copian esa
contraseña**: complétala tú mismo, localmente, en la variable `adminPassword`
del entorno (tomándola del propio `DataInitializer.java` o del `README.md`
raíz), para no duplicar el valor en más archivos versionados de los
estrictamente necesarios. `adminEmail` sí puedes completarlo directamente con
`admin@biopet.ec`: no es información sensible, es un valor fijo del código.

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
   `auxiliarEmail`/`auxiliarPassword` con esas credenciales en el entorno.

Esto **no aplica a ADMIN**, que ya viene sembrado como se describe arriba.

## Cookie jar automático — no hay JWT manual

Todo el flujo de autenticación usa cookies `access_token`/`refresh_token`
(`HttpOnly`+`Secure`+`SameSite=Strict`), gestionadas automáticamente por el
cookie jar interno de Postman tras un Login exitoso. La colección **nunca**:

- guarda un JWT en una variable;
- guarda el valor de una cookie en una variable;
- agrega `Authorization: Bearer` al flujo web principal;
- imprime cookies o tokens en la consola de Postman.

## HTTP vs HTTPS

`baseUrl` apunta por defecto a `{{baseUrlHttp}}` (`http://localhost:8080`).
Para probar contra HTTPS, cambia el **valor** de la variable `baseUrl` (en el
entorno o en la colección) a `{{baseUrlHttps}}`, o pega directamente
`https://localhost:8443`.

**Certificado autofirmado:** el HTTPS local usa un certificado académico
autofirmado (`Backend/certs/biopet-dev.p12`, generado con
`scripts/generate-dev-keystore.ps1`/`.sh`, nunca versionado). Postman
rechazará la conexión por defecto; si hace falta, desactiva temporalmente
**Settings → General → SSL certificate verification** solo en este entorno
académico local. Nunca hagas esto contra un dominio de producción real.

## Orden recomendado de ejecución

1. Completar las variables del entorno.
2. Levantar el stack:
   `docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d`
   (o solo `docker-compose.yml` si no se necesita HTTPS).
3. Carpeta **1. Estado del servicio**.
4. Carpeta **2. Autenticación**: Registro → Login del rol que corresponda.
5. Carpetas **2 a 4**: requests funcionales (mascotas, resumen).
6. Logout.
7. Los casos 401/403 en una sesión sin cookies (ver advertencia en cada
   request: limpiar el cookie jar de Postman para `localhost` antes de
   ejecutarlos, o correrlos antes de cualquier Login).
8. **Rate limiting (login)**, dentro de la carpeta 2, al final y de forma
   deliberada: bloquea temporalmente el login desde la IP actual del entorno
   de pruebas durante la ventana configurada (15 minutos por defecto).

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

## Collection Runner

Para ejecutar toda la colección (o una carpeta) de forma secuencial:
**Postman → Collection Runner** → seleccionar `BIOPET - API`, el entorno
"BIOPET - Local académico", y las carpetas deseadas, respetando el orden
recomendado arriba. Se sugiere **excluir** la subcarpeta "Rate limiting
(login)" de una corrida completa salvo que se quiera bloquear el login
deliberadamente al final.

## Newman (opcional, no ejecutado en esta fase)

El repositorio no usa Newman en ningún script ni en CI. No se agregó como
dependencia npm ni se modificó ningún `package.json`. Si se quiere validar
la colección por línea de comandos (requiere tener Newman instalado
globalmente o vía `npx`, fuera del alcance de este cambio):

```bash
npx newman run docs/postman/BIOPET.postman_collection.json \
  --environment docs/postman/BIOPET-Local.postman_environment.json \
  --folder "1. Estado del servicio"
```

Este comando es solo una referencia documentada; **no se ejecutó** durante
esta fase.

## Capturas de pantalla

No se generan en esta fase. Se reservan para una fase final de evidencias.
