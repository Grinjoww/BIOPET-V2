# BIOPET — Unidad IV | GA — Revisión Cruzada del PFC

Sistema web de gestión veterinaria desarrollado como Proyecto Fin de Curso (PFC) de la asignatura **Aplicaciones Web**.

Este repositorio corresponde al trabajo de **GA de la Unidad IV** y utiliza como proyecto de referencia el PFC **BIOPET**, manteniendo íntegro su código fuente y su historial de desarrollo para realizar sobre él el proceso de revisión, análisis, retroalimentación e investigación solicitado para la práctica.

---

## Organización del trabajo en la Unidad IV

Para esta actividad se está aplicando la modalidad de trabajo indicada para los grupos de GA: el **PFC es desarrollado y completado por sus integrantes originales**, mientras que los compañeros asignados al grupo de GA realizan una **revisión cruzada del proyecto**, con el objetivo de conocer el trabajo desarrollado por otro equipo, identificar fortalezas, detectar posibles limitaciones y plantear recomendaciones de mejora.

Por esta razón, este repositorio **no representa un nuevo desarrollo de BIOPET ni un cambio de autoría del PFC**.

El proyecto técnico fue desarrollado y completado por su equipo original de PFC:

- **Mariscal Cabrera Jaime Josue**
- **Beltrán Montiel Fred Adrian**
- **Taipe Mora Zaida Melissa**

Para la actividad de GA de Unidad IV, el grupo está conformado por:

- **Mariscal Cabrera Jaime Josue**
- **Carvajal Loor Johan Stalin**
- **Fajardo Montes Michael Xavier**

BIOPET fue seleccionado como el PFC que será analizado por este grupo.

---

## ¿Cómo se está trabajando?

El proceso se divide en dos responsabilidades claramente diferenciadas.

### 1. Desarrollo y finalización del PFC

La implementación funcional de BIOPET corresponde a los integrantes originales del PFC.

Antes de iniciar la revisión cruzada de GA, el proyecto fue completado e integrado con los elementos requeridos para la práctica, incluyendo los recursos REST, documentación de API, seguridad, pruebas automatizadas, persistencia, caché y demás componentes técnicos del sistema.

Por lo tanto, **Carvajal y Fajardo reciben un PFC ya desarrollado y funcional para su evaluación**.

Ellos no tienen como responsabilidad reconstruir módulos, apropiarse del código existente ni repetir el desarrollo realizado por el equipo original.

### 2. Revisión cruzada del grupo GA

A partir de la versión terminada de BIOPET, los integrantes del grupo GA que no pertenecen al equipo original realizan la revisión del proyecto.

Su participación comprende principalmente:

- analizar la organización y arquitectura del sistema;
- revisar el uso del patrón MVC;
- evaluar la API REST y su documentación;
- revisar aspectos de seguridad, pruebas, mantenibilidad y estructura general;
- identificar fortalezas del PFC;
- detectar posibles limitaciones o aspectos mejorables;
- proponer recomendaciones justificadas;
- realizar la investigación correspondiente a la Unidad IV;
- aportar a la documentación y al informe final de la práctica.

El objetivo de esta dinámica es que los estudiantes puedan **examinar un PFC desarrollado por otros compañeros y generar retroalimentación técnica sobre un trabajo distinto al propio**, conservando al mismo tiempo la autoría original de cada contribución.

---

## Responsabilidades dentro del grupo GA

| Integrante | Responsabilidad en esta actividad |
|---|---|
| **Jaime Mariscal** | Presentación del PFC seleccionado, evidencias técnicas de BIOPET, documentación del flujo MVC y API REST, integración de los aportes y elaboración del informe final. |
| **Johan Carvajal** | Revisión crítica independiente de BIOPET, identificación de fortalezas y debilidades, y desarrollo de la investigación comparativa SOAP vs REST. |
| **Michael Fajardo** | Segunda revisión crítica independiente de BIOPET y desarrollo de la investigación sobre Jamstack, PWA e inteligencia artificial generativa aplicada al desarrollo web. |

Las revisiones de Carvajal y Fajardo se documentarán de forma independiente para posteriormente realizar un análisis conjunto de las coincidencias, diferencias y recomendaciones encontradas.

---

## Evidencia de la revisión cruzada

La documentación específica de Unidad IV se organizará principalmente dentro de:

```text
docs/u4/
```

La estructura prevista es:

```text
docs/u4/
├── PFC-SELECCIONADO.md
├── EVIDENCIA-API-REST.md
├── RETROALIMENTACION-PFC.md
│
├── revisiones/
│   ├── REVISION-CARVAJAL.md
│   └── REVISION-FAJARDO.md
│
└── investigacion/
    ├── SOAP-VS-REST.md
    └── TENDENCIAS-WEB.md
```

De esta manera se diferencia claramente:

- el **código e historial técnico del PFC original**;
- las **revisiones realizadas por los integrantes del GA**;
- la **investigación de Unidad IV**;
- y la **integración final para el informe de la práctica**.

---

## Autoría e historial del repositorio

Este repositorio fue creado tomando como base la rama `main` del repositorio final de BIOPET y **conservando intencionalmente su historial de Git**.

Esto permite mantener la trazabilidad y reconocer correctamente qué integrantes desarrollaron originalmente cada parte del sistema.

Por este motivo, en la sección **Contributors** de GitHub pueden aparecer integrantes del equipo original del PFC, como Fred Beltrán y Zaida Taipe, aunque no formen parte del grupo actual de GA.

Su presencia en el historial **no significa que formen parte del grupo GA**, sino que GitHub reconoce correctamente los commits que realizaron durante el desarrollo original de BIOPET.

Del mismo modo, Carvajal y Fajardo comenzarán a registrar sus propias contribuciones en este repositorio mediante commits relacionados con:

- revisión crítica;
- retroalimentación;
- investigación;
- documentación;
- y elaboración del informe de Unidad IV.

De esta forma se conserva la autoría real del proyecto y, al mismo tiempo, queda registrada de manera independiente la participación correspondiente a la actividad GA.

---

## Alcance de este repositorio

Este repositorio tiene dos propósitos:

1. **Conservar una versión funcional y trazable del PFC BIOPET que será objeto de análisis.**
2. **Centralizar las evidencias, revisiones, investigación y documentación desarrolladas por el grupo GA durante la Unidad IV.**

No se pretende modificar el historial para atribuir el desarrollo del PFC a los integrantes nuevos del grupo.

La finalidad académica es utilizar un proyecto ya desarrollado como base para realizar **evaluación cruzada, retroalimentación técnica, análisis de tecnologías web y elaboración del informe correspondiente a la Unidad IV**.

---

## Proyecto técnico utilizado para la revisión

BIOPET implementa una arquitectura web con:

- **Angular 17.3** para el frontend.
- **Spring Boot 3.2.12 / Java 21** para el backend.
- **PostgreSQL 16** para persistencia.
- **Redis 7** para caché y soporte de revocación.
- **Spring Security + JWT** para autenticación y autorización.
- **OpenAPI / Swagger UI** para documentación de la API.
- **Flyway** para migraciones.
- **JUnit y JaCoCo** para pruebas y cobertura.
- **Docker Compose** para orquestación del entorno.

A partir de este punto se conserva la documentación técnica propia del PFC.

## Estado del proyecto

`v0.9.0-rc` es una **candidata a versión de la Tercera Entrega**, no una
versión de producción. El sistema es funcional y está respaldado por
pruebas automatizadas y evidencia reproducible (ver
[Pruebas y cobertura](#pruebas-y-cobertura) y [Limitaciones](#limitaciones)),
pero usa un certificado TLS autofirmado, credenciales de desarrollo y no ha
sido evaluado en un entorno equivalente a producción. **No debe
desplegarse tal cual en producción.**

## Stack tecnológico

| Componente | Versión / detalle |
|---|---|
| Backend | Java 21, Spring Boot 3.2.12 (`Backend/pom.xml`) |
| Seguridad | Spring Security 6, JWT firmado por el backend (`jjwt` 0.12.6, HMAC-SHA256) |
| Persistencia | Spring Data JPA + Hibernate, PostgreSQL 16, Flyway |
| Caché / revocación | Redis 7 (Spring Data Redis + Spring Cache) |
| Documentación de API | springdoc-openapi 2.5.0 (Swagger UI) |
| Cobertura | JaCoCo 0.8.12 (`jacoco-maven-plugin`) |
| Frontend | Angular 17.3, TypeScript 5.4, componentes standalone |
| Orquestación | Docker Compose, `Makefile` |

## Arquitectura

Angular (frontend) ↔ Spring Boot (backend) ↔ PostgreSQL + Redis, todo
orquestado con Docker Compose. El backend expone HTTP en el puerto 8080 y,
con el perfil `tls` activo, HTTPS/TLS 1.3 en el puerto 8443. El detalle
completo, con relaciones verificadas contra el código, está en:

- [`docs/diagrams/c4-contenedores/`](docs/diagrams/c4-contenedores/) — C4 Nivel 2 (contenedores).
- [`docs/diagrams/c4-componentes-backend/C4-L3-backend.md`](docs/diagrams/c4-componentes-backend/C4-L3-backend.md) — C4 Nivel 3 (componentes del backend).

### Flujo MVC de una petición autenticada

El siguiente diagrama de secuencia muestra el ciclo de vida de una petición autenticada dentro de BIOPET, desde el cliente Angular hasta PostgreSQL y el retorno de la respuesta JSON.

[Ver diagrama de secuencia del flujo MVC](docs/diagramas/flujo-mvc-springboot.png)

![Flujo MVC Spring Boot](docs/diagramas/flujo-mvc-springboot.png)

#### Descripción del flujo

1. **Cliente Angular:** `MascotaApiService.listar()` envía la petición `GET /api/mascotas` incluyendo la cookie de autenticación.
2. **JwtAuthenticationFilter:** `doFilterInternal()` intercepta la petición y obtiene el JWT mediante `resolveToken(request)`.
3. **SecurityContextHolder:** Spring registra la autenticación del usuario validado.
4. **MascotaController:** `listar(Pageable, UserDetails)` recibe la petición autenticada y los parámetros de paginación.
5. **MascotaService:** `listar(Pageable, String email)` ejecuta la lógica de negocio dentro de una transacción de solo lectura.
6. **UsuarioRepository:** `findByEmailAndActivoTrue(email)` obtiene al usuario autenticado.
7. **MascotaRepository:** según el rol, ejecuta `findAllByActivoTrue(pageable)` o `findAllByDuenioIdAndActivoTrue(...)`.
8. **PostgreSQL:** Hibernate genera y ejecuta la consulta SQL correspondiente sobre la tabla `mascotas`.
9. **MascotaResponse:** las entidades obtenidas se transforman al DTO mediante `map(this::toResponse)`.
10. **Jackson:** `MappingJackson2HttpMessageConverter` serializa el resultado a JSON.
11. **Cliente Angular:** recibe la respuesta HTTP `200 OK` con el contenido paginado en formato JSON.

## Inicio rápido

```bash
# 1. Clonar el repositorio
git clone https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git
cd PFC-VET-ENTR3-v0.9.0-rc

# 2. Copiar variables de entorno
cp .env.example .env

# 3. Levantar el sistema (postgres, redis, backend, frontend)
make up

# 4. Verificar que los 4 servicios estén healthy
docker compose ps

# 5. Acceder a la aplicación
# Frontend:        http://localhost:4200
# Swagger UI:       http://localhost:8080/api/swagger-ui.html
# Actuator Health:  http://localhost:8080/actuator/health
```

Sin pasos manuales adicionales (no requiere IntelliJ ni pgAdmin): el
esquema de PostgreSQL, los roles de base de datos y el usuario
administrador se inicializan automáticamente (ver
[Variables de entorno](#variables-de-entorno) y
[Autenticación](#autenticación-actual)).

## Variables de entorno

Copia `.env.example` como `.env` y ajusta solo si es necesario. Ningún
valor por defecto de `.env.example` es un secreto real: son valores de
desarrollo, ya documentados en el propio archivo.

| Variable | Para qué sirve |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Base, usuario propietario y contraseña con que Postgres se inicializa (Flyway usa esta cuenta). |
| `DB_URL` | URL JDBC que usa Spring Boot (`postgres` es el nombre del servicio en `docker-compose.yml`, no un host externo). |
| `DB_APP_USER`, `DB_APP_PASSWORD` | Cuenta de aplicación de privilegios mínimos (`biopet_app`, sin DDL) que usa Hibernate en tiempo de ejecución; ver `db/roles.sql` y `docs/adr/ADR-004-postgresql.md`. |
| `JWT_SECRET` | Clave HMAC de desarrollo para firmar los JWT. No reutilizar en un entorno real. |
| `JWT_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | Duración del access token y del refresh token, en milisegundos. |
| `JWT_ISSUER` / `JWT_AUDIENCE` | Claims `iss`/`aud` incluidos en cada token. |
| `REDIS_HOST` / `REDIS_PORT` | Conexión al servicio Redis. |
| `CACHE_TTL_MS` | TTL de las entradas de caché de Spring Cache (listado de mascotas). |
| `CORS_ALLOWED_ORIGINS` | Origen permitido para el frontend (nunca `*`, siempre un valor concreto junto con `allowCredentials(true)`). |
| `TLS_KEYSTORE_PASSWORD`, `TLS_KEY_ALIAS`, `TLS_HTTPS_PORT`, `TLS_HTTP_PORT` | Solo aplican con el perfil `tls` (ver [TLS/HTTPS](#tlshttps)); no se definen en `.env.example`, tienen valores por defecto en `application-tls.yml`. |

## Comandos del Makefile

Objetivos reales, verificados contra `Makefile` (raíz del repositorio):

| Comando | Estado | Función |
|---|---|---|
| `make up` | Implementado | Levanta el sistema completo (`docker compose up --build -d`). |
| `make down` | Implementado | Detiene los contenedores **sin borrar volúmenes** (los datos de Postgres/Redis se conservan). |
| `make test` | Implementado | Ejecuta las pruebas del backend (`cd Backend && mvn test`). No aplica por sí solo el umbral de cobertura (ver [Pruebas y cobertura](#pruebas-y-cobertura)). |
| `make bench` | Implementado | Ejecuta un benchmark k6 (`k6 run k6/listado-mascotas.js`) contra el endpoint de listado de mascotas. Para las 6 corridas oficiales (frío/caliente) usar los comandos documentados en `docs/mediciones/perf/REPORT.md`; este objetivo corre una única corrida rápida. |
| `make audit` | Implementado | Ejecuta la auditoría de seguridad OWASP (`scripts/security-evidence.sh`) y genera evidencia cruda en `docs/mediciones/sec/raw/` (config de docker-compose, headers HTTP/HTTPS, resultado de `mvn clean verify`). |
| `make clean` | Implementado | Detiene contenedores y elimina huérfanos, conservando los datos. |
| `make reset-db` | Implementado (**destructivo**) | Elimina también los volúmenes (borra los datos de Postgres y Redis) para reiniciar desde cero. |
| `make lighthouse` | Implementado | Ejecuta `scripts/run-lighthouse.sh` contra el frontend servido por Docker. Requiere `make up` previo. A la fecha de este README no hay resultados versionados en `docs/mediciones/lighthouse/`. |

## Ejecución HTTP y HTTPS

**HTTP (por defecto, puerto 8080):**

```bash
make up
```

**HTTPS/TLS 1.3 (además del HTTP interno en 8080; ver [TLS/HTTPS](#tlshttps)):**

```bash
# 1. Generar el keystore local una sola vez (no se versiona)
scripts/generate-dev-keystore.ps1   # Windows
scripts/generate-dev-keystore.sh    # Linux/macOS

# 2. Levantar el stack combinado
docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d
```

Con el overlay `docker-compose.tls.yml` activo, el backend queda accesible
en `https://localhost:8443` (y sigue respondiendo en `http://localhost:8080`
para tráfico interno).

## Pruebas y cobertura

```bash
cd Backend
mvn clean verify
```

`mvn clean verify` ejecuta la suite completa y, en la fase `verify`, aplica
el umbral automático de cobertura (`jacoco:check`); `make test` (`mvn test`)
solo ejecuta las pruebas y genera el reporte, sin exigir el umbral.

Resultado real más reciente:

| Métrica | Valor |
|---|---|
| Pruebas ejecutadas | 109 |
| Fallos | 0 |
| Errores | 0 |
| Omitidas | 0 |
| Cobertura LINE | 95.87 % |
| Cobertura BRANCH | 76.87 % |
| Cobertura COMPLEXITY | 80.00 % |
| Umbral automático (`jacoco:check`, regla `BUNDLE`) | ≥ 60 % en LINE, BRANCH y COMPLEXITY |
| Resultado | `BUILD SUCCESS` |

Detalle y exclusiones justificadas en
[`docs/mediciones/sec/jacoco-summary.md`](docs/mediciones/sec/jacoco-summary.md).
`Backend/target/` no se versiona: estos reportes se regeneran localmente en
cada `mvn clean verify`.

## Autenticación actual

- JWT firmado por el propio backend (HMAC-SHA256), **no completamente
  stateless**: la revocación depende de una lista negra en Redis (ver
  abajo). Access token y refresh token son tokens separados, generados por
  el mismo mecanismo y distinguidos por el claim `typ`.
- Los tokens se entregan mediante **cookies** `access_token` y
  `refresh_token`, con los atributos `HttpOnly`, `Secure` y
  `SameSite=Strict` (`Path=/` y `Path=/api/auth` respectivamente). El
  frontend Angular **no usa `localStorage` ni agrega manualmente
  `Authorization: Bearer`**: envía las cookies automáticamente
  (`credentials: include`) y depende del *cookie jar* del navegador (o de
  Postman, al probar la API).
- `POST /api/auth/refresh` **no recibe el refresh token en el body**: lo
  lee directamente de la cookie `refresh_token` y responde emitiendo una
  nueva cookie de access token.
- `POST /api/auth/logout` revoca en Redis cada token presente
  (`TokenBlacklistService`, TTL igual al tiempo de vida restante) y borra
  ambas cookies en el cliente; es idempotente (responde `204` incluso sin
  sesión activa).
- Soporte adicional de `Authorization: Bearer` en el backend para clientes
  no-navegador, pero **no es el flujo del frontend web**.

Detalle completo, incluidos los 10 claims del JWT (7 estándar de RFC 7519 +
3 propios), en
[`docs/adr/ADR-006-autenticacion-seguridad.md`](docs/adr/ADR-006-autenticacion-seguridad.md).

## Autorización

Cuatro roles reales (`Rol` / columna `usuarios.rol`, con *constraint* en
`V1__schema_inicial.sql`): `ROLE_ADMIN`, `ROLE_VETERINARIO`,
`ROLE_AUXILIAR`, `ROLE_DUENO`.

| Operación | Roles permitidos (verificado en `MascotaController`) |
|---|---|
| `GET /api/mascotas`, `GET /api/mascotas/{id}`, `GET /api/mascotas/resumen-especies` | `ADMIN`, `VETERINARIO`, `AUXILIAR`, `DUENO` |
| `POST /api/mascotas`, `PUT /api/mascotas/{id}`, `DELETE /api/mascotas/{id}` | `ADMIN`, `VETERINARIO`, `AUXILIAR` únicamente — `DUENO` recibe 403, incluso sobre su propia mascota |

Además del rol, `ROLE_DUENO` está sujeto a **control por propietario**
(`MascotaService.verificarPropiedad`): solo puede listar y consultar sus
propias mascotas; acceder a la de otro dueño responde 403.
`ADMIN`/`VETERINARIO`/`AUXILIAR` tienen alcance global (no están limitados
por propiedad).

Diferencia entre códigos: **401** cuando no hay autenticación válida
(sin cookie, cookie inválida o revocada); **403** cuando hay autenticación
válida pero el rol o la propiedad no lo permiten.

El registro público (`POST /api/auth/registro`) siempre asigna
`ROLE_DUENO`, sin importar el valor de `rol` enviado en el body (el campo
es obligatorio por validación, pero el servidor lo ignora): no existe alta
pública de `ADMIN`/`VETERINARIO`/`AUXILIAR`.

## Rate limiting

Límite de intentos fallidos de login por IP (`LoginRateLimiterService`,
en memoria, `ConcurrentHashMap`, **estado por instancia del backend, no
distribuido**):

- Intentos 1 a 5 fallidos consecutivos: responden **401**.
- Intento 6: responde **429** con cabecera `Retry-After` (segundos).
- El contador se reinicia tras un login exitoso y es independiente entre
  IP distintas.

## ProblemDetail y códigos reales

Todas las respuestas de error usan el formato uniforme `ProblemDetail`
(RFC 7807, `application/problem+json`: `type`, `title`, `status`,
`detail`, `instance`). Códigos confirmados en el código
(`GlobalExceptionHandler`, `ProblemAuthenticationEntryPoint`,
`ProblemAccessDeniedHandler`):

| Código | Cuándo ocurre |
|---|---|
| 400 | Parámetro con formato incompatible (`MethodArgumentTypeMismatchException`), por ejemplo `duenioId` no numérico. |
| 401 | Sin autenticación válida, o credenciales incorrectas en login. |
| 403 | Autenticado pero sin el rol o la propiedad requeridos. |
| 404 | Recurso inexistente (`RecursoNoEncontradoException`). |
| 409 | Conflicto de datos: email ya registrado (`EmailDuplicadoException`). |
| 422 | Validación de Bean Validation fallida (`MethodArgumentNotValidException`). |
| 429 | Rate limiting de login excedido. |

## TLS/HTTPS

Perfil Spring `tls` (`SPRING_PROFILES_INCLUDE=tls`, activado por
`docker-compose.tls.yml`): agrega un conector HTTPS en el puerto **8443**
(único protocolo habilitado: **TLS 1.3**) junto al conector HTTP interno en
el puerto **8080** (`TomcatDualConnectorConfig`). El certificado es PKCS12,
**autofirmado y exclusivamente académico** (nunca válido para producción),
generado localmente con `scripts/generate-dev-keystore.ps1`/`.sh` y
**nunca versionado** (`Backend/certs/`, excluido por `.gitignore`).

## Endpoints actuales

Verificados contra `AuthController`, `MascotaController` y
`UsuarioController`:

| Método | Ruta | Autenticación | Rol requerido |
|---|---|---|---|
| POST | `/api/auth/registro` | No | — (crea siempre `ROLE_DUENO`) |
| POST | `/api/auth/login` | No | — |
| POST | `/api/auth/refresh` | Cookie `refresh_token` | — |
| POST | `/api/auth/logout` | Cookie (idempotente sin sesión) | — |
| GET | `/api/usuarios/me` | Cookie `access_token` | Cualquier rol autenticado |
| GET | `/api/mascotas` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR`/`DUENO` |
| GET | `/api/mascotas/{id}` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR`/`DUENO` (propiedad para `DUENO`) |
| GET | `/api/mascotas/resumen-especies` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR`/`DUENO` |
| POST | `/api/mascotas` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR` |
| PUT | `/api/mascotas/{id}` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR` |
| DELETE | `/api/mascotas/{id}` | Cookie `access_token` | `ADMIN`/`VETERINARIO`/`AUXILIAR` (baja lógica) |
| GET | `/actuator/health` | No | — |
| GET | `/api/swagger-ui.html` | No | — |

### Cuenta de desarrollo sembrada

El backend crea automáticamente, en cada arranque
(`DataInitializer.seedAdmin`, de forma idempotente), un único usuario:
`admin@biopet.ec`, rol `ROLE_ADMIN`. **Es una cuenta académica de
desarrollo**, no un dato real: la contraseña está definida en el propio
`DataInitializer.java` (y replicada en `db/seed.sql`); este README no la
reproduce. **Antes de cualquier despliegue fuera del entorno académico,
esta cuenta debe eliminarse o su contraseña debe externalizarse** (por
ejemplo, mediante un gestor de secretos), ya que hoy es un valor fijo en
el código fuente.

También puedes registrar un usuario nuevo con `POST /api/auth/registro`
(siempre queda como `ROLE_DUENO`):

```json
{
  "nombre": "Usuario de ejemplo",
  "email": "usuario@example.test",
  "password": "ClaveDeEjemplo123*",
  "rol": "ROLE_DUENO"
}
```

Un usuario `ROLE_DUENO` puede consultar sus propias mascotas, pero crear,
actualizar o eliminar requiere una cuenta `ADMIN`/`VETERINARIO`/`AUXILIAR`.

## Postman

Colección y entorno reproducibles, verificados contra el código actual
(40 requests, cookies automáticas, sin JWT ni `Authorization: Bearer` en
el flujo principal):

- [`docs/postman/BIOPET.postman_collection.json`](docs/postman/BIOPET.postman_collection.json)
- [`docs/postman/BIOPET-Local.postman_environment.json`](docs/postman/BIOPET-Local.postman_environment.json)
- Instrucciones de uso: [`docs/postman/README.md`](docs/postman/README.md)

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura (ADR-002 a ADR-007); ver especialmente [`ADR-006-autenticacion-seguridad.md`](docs/adr/ADR-006-autenticacion-seguridad.md) (autenticación y seguridad) y [`ADR-007-acceso-datos.md`](docs/adr/ADR-007-acceso-datos.md) (estrategia híbrida de acceso a datos). |
| [`docs/diagrams/c4-componentes-backend/C4-L3-backend.md`](docs/diagrams/c4-componentes-backend/C4-L3-backend.md) | C4 Nivel 3: componentes del backend. |
| [`docs/mediciones/sec/`](docs/mediciones/sec/) | Evidencia OWASP (A01, A02, A03, A05, A07, A09) y resumen JaCoCo, con [`REPORT.md`](docs/mediciones/sec/REPORT.md) como índice. |
| [`docs/mediciones/redis/`](docs/mediciones/redis/) | Evidencia cruda de caché: configuración `maxmemory`/`maxmemory-policy`, tamaño de la base (`DBSIZE`), TTL y claves activas del caché de listado de mascotas. |
| [`docs/mediciones/postgres/`](docs/mediciones/postgres/) | Evidencia formal de privilegios de rol: confirma que `biopet_app` opera con privilegios mínimos (`arwd`, sin `Superuser`/`Create role`/`Create DB`/owner completo) sobre `usuarios` y `mascotas`. |
| [`docs/mediciones/DATA-DICTIONARY.md`](docs/mediciones/DATA-DICTIONARY.md) | Diccionario de datos de todas las mediciones (seguridad, cobertura, rendimiento, usabilidad). |
| [`docs/informe/`](docs/informe/README.md) | Código fuente LaTeX del informe final de la Tercera Entrega; ver `docs/informe/README.md` para compilarlo. |
| [`docs/requisitos/`](docs/requisitos/) | SRS, historias de usuario, casos de uso. |
| [`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv) | Matriz de trazabilidad de requisitos. |
| [`docs/basedatos/CATALOGO-SP.md`](docs/basedatos/CATALOGO-SP.md) | Catálogo de funciones/procedimientos de PostgreSQL. |
| [`docs/etica/ETHICS.md`](docs/etica/ETHICS.md) | Declaración ética y de gestión de datos. |

## Reproducibilidad de imágenes Docker

Las imágenes de terceros están fijadas por **digest sha256**, no solo por
*tag*, para que una reconstrucción futura no use, sin darse cuenta, una
versión distinta si el *tag* se actualiza silenciosamente:

- `postgres:16-alpine` y `redis:7-alpine` en `docker-compose.yml`.
- `maven:3.9-eclipse-temurin-21` y `eclipse-temurin:21-jre-alpine` en `Backend/Dockerfile`.

Las imágenes `backend` y `frontend` se construyen localmente desde el
código del repositorio (`Dockerfile` propio): su reproducibilidad depende
de que el código fuente esté versionado, no de un pin de registro externo.

**Para actualizar un digest:**

```bash
docker pull <imagen:tag>
docker inspect --format='{{index .RepoDigests 0}}' <imagen:tag>
# o, sin descargar la imagen completa:
docker buildx imagetools inspect <imagen:tag>
```

Copia la línea completa `imagen:tag@sha256:...` en el `image:` (o `FROM`)
correspondiente, valida con `docker compose config`, y confirma con
`make up && docker compose ps` que el sistema arranca correctamente con la
nueva imagen.

## Limitaciones

- El certificado TLS es autofirmado y exclusivamente académico; no válido
  para producción.
- El rate limiting de login es en memoria y por instancia del backend, no
  distribuido.
- Todo el sistema se evaluó como **una sola instancia** del backend
  (`docker-compose.yml`, sin réplicas).
- Los logs de auditoría (`AUTH_AUDIT`) son locales al proceso/contenedor,
  sin integración con un SIEM centralizado.
- Las mediciones de usabilidad (SUS) y de accesibilidad automatizada
  (Lighthouse) todavía no se han ejecutado y no tienen resultados
  versionados.
- **Este sistema no está listo para producción**: certificado académico,
  secretos de desarrollo en `.env.example`/código, sin SIEM, sin evaluación
  de alta disponibilidad.
