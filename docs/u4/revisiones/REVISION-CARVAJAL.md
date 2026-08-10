# Revisión Crítica #1 — PFC BIOPET

**Autor:** Carvajal Loor Johan Stalin
**Actividad:** Unidad IV - GA | PFC BIOPET
**Archivo:** docs/u4/revisiones/REVISION-CARVAJAL.md
**Enfoque:** Arquitectura MVC, organización del backend, API REST, contrato OpenAPI, CRUD, autenticación y roles, seguridad, base de datos, Redis, API externa, pruebas, Docker y mantenibilidad.

**Método de revisión:** lectura estática del código fuente de la rama `main` (paquete `com.biopet`, 74 archivos), contraste con la documentación del `README.md` y los ADR-002 a ADR-007, y verificación de los archivos de configuración (`application.yml`, `docker-compose.yml`, migraciones Flyway). Cada observación de este documento apunta a un archivo y línea concretos para que pueda ser comprobada por el equipo original.

---

## 1. Arquitectura MVC y organización del backend

**Fortaleza:** la separación en capas es correcta y consistente en los seis controladores. El flujo `controller → service → repository → entity` no se rompe en ningún punto: los controladores no acceden a repositorios y los servicios no devuelven entidades JPA hacia afuera, sino DTOs implementados como `record` de Java 21 (`MascotaResponse`, `CitaResponse`, etc.). La configuración `spring.jpa.open-in-view: false` en [`application.yml:13`](../../../Backend/src/main/resources/application.yml) refuerza esa frontera: impide que la sesión de Hibernate siga abierta durante la serialización, que es un error frecuente en proyectos Spring Boot académicos.

**Limitación observada:** la conversión de entidad a DTO está escrita a mano y duplicada en cada servicio. El método privado `toResponse()` aparece repetido en `MascotaService`, `CitaService`, `ConsultaService`, `VacunaService`, `UsuarioService` y `AuthService`, con la misma estructura pero sin ningún contrato común. Si mañana se agrega un campo a `Mascota`, hay que recordar tocar el mapeo manualmente; el compilador no avisa.

**Mejora recomendada:** extraer los mapeos a una capa `mapper` (interfaces con MapStruct, que genera el código en tiempo de compilación y detecta campos sin mapear). Como alternativa de bajo costo, si no se quiere agregar dependencias, al menos mover los métodos a clases `*Mapper` propias para que la responsabilidad quede aislada del servicio.

## 2. Resolución del usuario autenticado (consulta duplicada por request)

**Fortaleza:** el uso de `@AuthenticationPrincipal UserDetails` en los controladores evita que la identidad viaje como parámetro manipulable desde el cliente, y el servicio siempre reconstruye el usuario desde la base en lugar de confiar en datos del token más allá del email.

**Limitación observada:** esa reconstrucción se hace **dos veces por cada petición autenticada**. `JwtAuthenticationFilter` ya ejecuta `userDetailsService.loadUserByUsername(email)` en [`JwtAuthenticationFilter.java:62`](../../../Backend/src/main/java/com/biopet/security/JwtAuthenticationFilter.java), lo que implica una consulta a la tabla `usuarios`; luego, el servicio vuelve a consultar la misma fila con `usuarioRepository.findByEmailAndActivoTrue(email)`. En `MascotaService` esa llamada se repite en cinco métodos distintos ([líneas 35, 46, 72, 89 y 100](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)), y el mismo patrón se replica en `CitaService`, `ConsultaService` y `VacunaService`. Es decir, un `GET /api/mascotas` golpea la tabla de usuarios dos veces antes de siquiera tocar la tabla de mascotas.

**Mejora recomendada:** implementar un `UserDetails` propio (por ejemplo `UsuarioPrincipal`) que ya transporte `id` y `rol` desde el filtro. Los servicios recibirían ese principal en lugar del email, se elimina la segunda consulta y desaparece la repetición de cinco `findByEmailAndActivoTrue` idénticos.

## 3. Diseño de la API REST

**Fortaleza:** el diseño respeta el nivel 2 del modelo de madurez de Richardson y lo hace con disciplina: sustantivos en plural (`/api/mascotas`, `/api/citas`, `/api/vacunas`), verbos HTTP con la semántica correcta, `201 Created` en las altas ([`MascotaController.java:44`](../../../Backend/src/main/java/com/biopet/controller/MascotaController.java)), `204 No Content` en las bajas, paginación delegada a `Pageable` de Spring Data y errores estandarizados con `ProblemDetail` (RFC 7807). Ese último punto es especialmente destacable: `GlobalExceptionHandler` devuelve `application/problem+json` de forma uniforme e incluso agrega la cabecera `Retry-After` en el 429 ([`GlobalExceptionHandler.java:83`](../../../Backend/src/main/java/com/biopet/exception/GlobalExceptionHandler.java)).

**Limitación observada:** dos problemas concretos de consistencia.

1. **No hay versionado en la ruta.** Todos los recursos cuelgan de `/api/...` sin `v1`. Cualquier cambio incompatible futuro (renombrar un campo de `MascotaResponse`, por ejemplo) rompe a los clientes sin una vía de convivencia entre versiones.
2. **La restricción del identificador es desigual entre controladores.** `CitaController` aplica `{id:\\d+}` en GET, PUT y DELETE ([líneas 31, 43 y 50](../../../Backend/src/main/java/com/biopet/controller/CitaController.java)), mientras que `MascotaController` solo lo aplica en el GET ([línea 35](../../../Backend/src/main/java/com/biopet/controller/MascotaController.java)) y deja `PUT /{id}` y `DELETE /{id}` sin la restricción. El resultado es que un identificador no numérico produce una respuesta distinta según el recurso y el verbo que se use, lo cual es exactamente el tipo de detalle que un cliente externo descubre en producción.

**Mejora recomendada:** introducir el prefijo `/api/v1/` antes de que existan consumidores externos (ahora el costo es casi nulo) y unificar el patrón `{id:\\d+}` en los cinco controladores de recursos, o retirarlo de todos y dejar que `MethodArgumentTypeMismatchException` — que ya está manejada en [`GlobalExceptionHandler.java:66`](../../../Backend/src/main/java/com/biopet/exception/GlobalExceptionHandler.java) — devuelva siempre un 400 uniforme.

## 4. Documentación OpenAPI / Swagger

**Fortaleza:** springdoc está correctamente integrado, con rutas propias (`/api/docs` para la UI y `/api/openapi` para el JSON), esquema de seguridad declarado y `operationsSorter: method` para que la UI sea legible. Además existe `SwaggerUiTest`, es decir, la disponibilidad de la documentación está cubierta por una prueba automatizada y no solo asumida.

**Limitación observada:** el contrato generado es estructural, no descriptivo. **No existe una sola anotación `@Operation`, `@ApiResponse` ni `@Tag` en todo el backend** (verificado buscando esas anotaciones en `Backend/src/main/java`). En consecuencia, la especificación publicada no documenta ninguno de los códigos de error que el sistema sí implementa con cuidado: el 422 de validación, el 409 de email duplicado, el 429 con `Retry-After` o el 502 de la API externa son invisibles para quien lea el OpenAPI. A esto se suman dos desajustes:

- `OpenApiConfig` declara `version("v0.1.0")` en [`OpenApiConfig.java:21`](../../../Backend/src/main/java/com/biopet/config/OpenApiConfig.java), mientras el repositorio va en `v0.9.0-rc` según el `README.md`. La documentación quedó desincronizada del release.
- El único esquema de seguridad declarado es `bearerAuth` (HTTP Bearer). El filtro efectivamente acepta ese mecanismo ([`JwtAuthenticationFilter.java:84-87`](../../../Backend/src/main/java/com/biopet/security/JwtAuthenticationFilter.java)), así que la declaración no es incorrecta; el problema es que **está incompleta**: el mecanismo que realmente usa el frontend Angular es la cookie `HttpOnly` emitida por `JwtCookieService`, y ese esquema no aparece en el contrato. Quien lea el OpenAPI no puede deducir cómo se autentica el cliente real del sistema.

**Mejora recomendada:** anotar al menos las operaciones de `MascotaController` y `AuthController` con `@Operation` y `@ApiResponses` incluyendo los códigos de error reales; declarar un segundo `SecurityScheme` de tipo `APIKEY` con `in: COOKIE` y nombre `access_token`; y tomar la versión del contrato desde la propiedad de Maven (`@project.version@`) en lugar de escribirla a mano, para que no se vuelva a desincronizar.

## 5. Operaciones CRUD

**Fortaleza:** el CRUD está completo en los cuatro recursos de negocio (mascotas, citas, consultas, vacunas), con validación declarativa mediante `@Valid` sobre DTOs anotados con Bean Validation, y con borrado lógico en lugar de físico: `eliminar()` marca `activo = false` y conserva la fila ([`MascotaService.java:94`](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)). Para un sistema veterinario esto es lo correcto, porque el historial clínico no debería poder destruirse desde la API.

**Limitación observada:** el borrado lógico es de una sola dirección. No existe ningún endpoint para reactivar un registro ni para listar los inactivos: todas las consultas del repositorio filtran por `activoTrue` ([`MascotaRepository.java:14-16`](../../../Backend/src/main/java/com/biopet/repository/MascotaRepository.java)). Una mascota eliminada por error queda en la base pero es inalcanzable por completo desde la aplicación; recuperarla exige acceso directo a PostgreSQL. Además, ese filtro se aplica manualmente en el nombre de cada método derivado, de modo que cualquier consulta nueva que alguien escriba sin el sufijo `ActivoTrue` expondrá registros borrados sin que nada lo advierta.

**Mejora recomendada:** agregar un `PATCH /api/mascotas/{id}/reactivar` restringido a `ROLE_ADMIN`, y mover el filtro de borrado lógico a la entidad con `@SQLRestriction("activo = true")` (Hibernate 6), dejando el filtro como comportamiento por defecto en lugar de una convención de nombres que hay que recordar.

## 6. Autenticación y control de roles

**Fortaleza:** el control de acceso opera en dos niveles complementarios. El rol se valida de forma declarativa con `@PreAuthorize` en cada método del controlador, habilitado por `@EnableMethodSecurity` en [`SecurityConfig.java:32`](../../../Backend/src/main/java/com/biopet/config/SecurityConfig.java); y la propiedad del recurso se valida en el servicio con `verificarPropiedad()`, de modo que un `ROLE_DUENO` no puede leer la mascota de otro aunque conozca su id. Los cuatro roles están además restringidos a nivel de base de datos por un `CHECK` en la migración V1, lo que impide insertar un rol inexistente incluso por fuera de la aplicación.

**Limitación observada:** tres puntos.

1. **Las expresiones de autorización son cadenas literales repetidas.** `hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')` aparece copiada en los seis controladores. Un error de tipeo (`'VETERINARO'`) compila sin problema y solo se detecta si existe una prueba específica para ese endpoint y ese rol.
2. **Hay una comprobación de propiedad que nunca puede fallar.** `MascotaService.actualizar()` y `eliminar()` invocan `verificarPropiedad()` ([líneas 76 y 93](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)), pero esos dos endpoints están restringidos por `@PreAuthorize` a `ADMIN`, `VETERINARIO` y `AUXILIAR` — precisamente los tres roles para los que `tieneAccesoGlobal()` devuelve siempre `true` ([línea 112](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)). La rama que lanza `AccessDeniedException` es, en esos dos caminos, inalcanzable: código muerto que además infla artificialmente la sensación de cobertura.
3. **El registro público declara un campo que el servidor ignora.** `RegistroRequest` exige `@NotNull Rol rol` ([`RegistroRequest.java:13`](../../../Backend/src/main/java/com/biopet/dto/RegistroRequest.java)), pero `AuthService.registrar()` descarta ese valor y fuerza `Rol.ROLE_DUENO` ([`AuthService.java:60`](../../../Backend/src/main/java/com/biopet/service/AuthService.java)). La decisión de seguridad es la correcta —impide escalar privilegios desde un endpoint público—, pero el contrato queda engañoso: un cliente que envíe `"rol": "ROLE_ADMIN"` recibe `201 Created` y cree haber creado un administrador. El campo obligatorio no tiene ningún efecto y, además, aparece como requerido en Swagger.

**Mejora recomendada:** reemplazar las cadenas por meta-anotaciones (`@EsPersonalClinica`, `@EsAdmin`) definidas una sola vez; eliminar la llamada muerta a `verificarPropiedad()` o —mejor— ampliar `@PreAuthorize` para incluir a `DUENO` y que la comprobación de propiedad pase a tener sentido real; y quitar `rol` de `RegistroRequest`, dejando la asignación de roles distintos de `DUENO` exclusivamente en `UsuarioController` bajo permiso de administrador.

## 7. Seguridad

**Fortaleza:** la configuración de cabeceras es más completa de lo habitual en un proyecto de curso: CSP con `default-src 'self'` y `frame-ancestors 'none'`, `X-Frame-Options: DENY`, HSTS a un año con `includeSubDomains` y `preload`, y `Referrer-Policy: no-referrer` ([`SecurityConfig.java:58-67`](../../../Backend/src/main/java/com/biopet/config/SecurityConfig.java)). El token JWT viaja en cookie `HttpOnly` + `SameSite=Strict`, nunca en `localStorage`, y existe revocación efectiva vía lista negra en Redis consultada en cada petición. Todo esto está respaldado por `SecurityHeadersTest` y `SqlInjectionSecurityTest`.

**Limitación observada:** dos riesgos concretos que conviene discutir.

1. **`csrf.disable()` conviviendo con autenticación por cookie.** En [`SecurityConfig.java:55`](../../../Backend/src/main/java/com/biopet/config/SecurityConfig.java) se desactiva la protección CSRF. El argumento habitual —"la API es *stateless*"— aquí no aplica del todo, porque el navegador **sí** adjunta la credencial automáticamente: el filtro lee la cookie antes que la cabecera `Authorization` ([`JwtAuthenticationFilter.java:79-81`](../../../Backend/src/main/java/com/biopet/security/JwtAuthenticationFilter.java)). `SameSite=Strict` mitiga el escenario clásico, pero la mitigación queda delegada por completo al navegador y no al servidor. La decisión puede ser defendible, pero no está justificada en ningún ADR.
2. **El secreto de firma del JWT tiene un valor por defecto versionado en el repositorio.** [`application.yml:37`](../../../Backend/src/main/resources/application.yml) define `${JWT_SECRET:9c8f9a7d...}`. Si la variable de entorno falta, la aplicación **arranca igual** y firma tokens con una clave que está publicada en el código fuente. Un fallo de configuración silencioso se convierte así en una falsificación de tokens trivial. Lo mismo ocurre con `DB_APP_PASSWORD` y `DB_PASSWORD`.

Un tercer detalle menor: `security.cookie.secure` está fijado en `true` mientras el perfil por defecto sirve HTTP en el puerto 8080. Funciona en desarrollo únicamente porque los navegadores tratan `localhost` como contexto seguro; en cualquier despliegue HTTP fuera de `localhost` la cookie no se almacenaría y la sesión fallaría sin mensaje claro.

**Mejora recomendada:** documentar en un ADR la razón de desactivar CSRF y, si se mantiene, considerar exigir una cabecera personalizada (`X-Requested-With`) que el navegador no puede enviar en una petición *cross-site* simple; y eliminar los valores por defecto de los secretos para que la aplicación falle al arrancar cuando falten (`${JWT_SECRET}` sin fallback), convirtiendo un riesgo silencioso en un error visible en el primer segundo.

## 8. Base de datos y persistencia

**Fortaleza:** hay tres decisiones que merecen reconocimiento explícito. Primero, `ddl-auto: validate` ([`application.yml:12`](../../../Backend/src/main/resources/application.yml)): Hibernate nunca modifica el esquema, solo verifica que coincida con las entidades. Segundo, la separación de credenciales: la aplicación se conecta como `biopet_app` mientras Flyway migra como `biopet_user` ([líneas 7-8 y 23-24](../../../Backend/src/main/resources/application.yml)), aplicando mínimo privilegio real y no solo declarado. Tercero, el uso de una función almacenada (`fn_resumen_mascotas_por_especie`) consumida mediante una proyección de interfaz en [`MascotaRepository.java:18`](../../../Backend/src/main/java/com/biopet/repository/MascotaRepository.java), que evita traer entidades completas solo para agregar.

**Limitación observada:** **el esquema está definido en tres lugares y uno de ellos ya está desactualizado.** Existen:

- `Backend/src/main/resources/db/migration/V1__…` a `V4__…` — las migraciones que ejecuta Flyway (tablas `usuarios`, `mascotas`, `citas`, `consultas`, `vacunas`).
- `db/schema.sql` — montado por `docker-compose.yml` en `docker-entrypoint-initdb.d`. Su propio encabezado advierte: *"si en el futuro se crea V2__, V3__, etc. en Flyway, este archivo debe actualizarse manualmente"*. Las migraciones V2, V3 y V4 **ya existen**, y `db/schema.sql` sigue conteniendo únicamente `usuarios` y `mascotas`. El riesgo que el propio archivo anticipó ya se materializó.
- `database/migrations/V1__schema_inicial.sql` — una copia byte a byte de la V1 de Flyway, fuera del classpath y sin referencias en el proyecto. Es un archivo huérfano.

Hoy el arranque no se rompe porque `baseline-on-migrate: true` hace que Flyway acepte la base preexistente y aplique de V2 en adelante. Es decir, el sistema funciona por la combinación afortunada de dos configuraciones, no porque las fuentes estén sincronizadas.

**Mejora recomendada:** dejar Flyway como única fuente de verdad del esquema y retirar `db/schema.sql` del `docker-compose.yml` (Flyway ya corre al arrancar el backend, que además espera a que Postgres esté `healthy`); si se quiere conservar el arranque independiente del backend, generar `db/schema.sql` automáticamente desde las migraciones mediante un objetivo del `Makefile` en lugar de mantenerlo a mano. Y eliminar `database/migrations/`, que solo puede inducir a error.

## 9. Caché con Redis

**Fortaleza:** el proyecto usa Redis en dos modos distintos y ambos con criterio: caché declarativa de Spring (`@Cacheable` / `@CacheEvict`) para el listado paginado de mascotas, y caché manual con TTL explícito para la API externa. El TTL global está parametrizado (`CACHE_TTL_MS`, 5 minutos por defecto) y `cache-null-values: false` evita envenenar la caché con resultados vacíos.

**Limitación observada:** la estrategia de claves y de invalidación es demasiado gruesa.

1. **La clave incluye el email del usuario:** `#email + '-' + #pageable.pageNumber + …` ([`MascotaService.java:32`](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)). Pero el resultado solo depende del email cuando el rol es `DUENO`; para `ADMIN`, `VETERINARIO` y `AUXILIAR` el listado es idéntico. Con diez empleados de la clínica se almacenan diez copias byte a byte del mismo listado, multiplicando la memoria usada y reduciendo la tasa de aciertos sin ningún beneficio.
2. **La invalidación es total:** `@CacheEvict(value = "mascotas", allEntries = true)` en las tres operaciones de escritura ([líneas 54, 69 y 86](../../../Backend/src/main/java/com/biopet/service/MascotaService.java)). Registrar **una** mascota borra la caché de **todos** los usuarios y **todas** las páginas. En una clínica con altas frecuentes, la caché pasaría la mayor parte del tiempo vacía y el beneficio medido en `docs/mediciones/redis/` no se sostendría bajo carga de escritura real.
3. **No hay ninguna personalización del `CacheManager`.** No existe en el proyecto una configuración de `RedisCacheConfiguration` ni de serializador (verificado en `Backend/src/main/java/com/biopet/config/`), de modo que se está serializando un `Page<MascotaResponse>` con la serialización Java por defecto. Es un punto históricamente frágil, porque `PageImpl` no garantiza estabilidad de deserialización entre versiones de Spring Data: una actualización de dependencias puede romper la lectura de la caché en caliente.

**Mejora recomendada:** cambiar la clave a un "alcance" en lugar de una identidad (`"global"` para el personal de clínica y `"duenio-" + id` para el dueño), lo que colapsa N copias en una sola; sustituir el `allEntries = true` por invalidación dirigida a las claves afectadas; y declarar explícitamente un `RedisCacheManager` con serializador JSON (`GenericJackson2JsonRedisSerializer`) cacheando `List<MascotaResponse>` + total en lugar del `Page` completo.

## 10. Integración con la API externa

**Fortaleza:** el patrón *cache-aside* está bien implementado en `ExternalApiService`: normaliza la clave a minúsculas, consulta Redis antes de salir a la red, guarda con TTL propio de 10 minutos y —detalle importante— si Redis falla al escribir, la respuesta al usuario **no** se rompe ([`ExternalApiService.java:62-69`](../../../Backend/src/main/java/com/biopet/integration/ExternalApiService.java)). Los timeouts están configurados explícitamente (3 s de conexión, 5 s de lectura, [`RestTemplateConfig.java:15-18`](../../../Backend/src/main/java/com/biopet/integration/RestTemplateConfig.java)), lo que evita que un tercero lento agote el pool de hilos del backend. Y el error se traduce a un `502 Bad Gateway` con `ProblemDetail`, que es el código semánticamente correcto.

**Limitación observada:** la resiliencia se queda en los timeouts. No hay reintentos, ni *circuit breaker*, ni degradación elegante: si `api-ninjas` está caída, cada petición espera hasta 5 segundos y termina en 502, incluso cuando existía un valor en caché vencido hace un minuto que habría sido perfectamente utilizable. Además, el `catch (Exception ex) {}` de la línea 66 está completamente vacío, sin siquiera un `log.warn`: un fallo persistente de escritura en Redis dejaría la caché inoperante de forma silenciosa y nadie lo notaría hasta ver la factura de llamadas a la API externa. Por último, `resultados.get(0)` toma el primer elemento de la respuesta sin verificar que corresponda realmente a la especie solicitada.

**Mejora recomendada:** aplicar *stale-while-error* — conservar la última respuesta válida con un TTL más largo y servirla cuando la llamada externa falle, indicándolo en el campo `origen` que el DTO ya tiene previsto; registrar el fallo de escritura de Redis con `log.warn` en lugar de silenciarlo; y, si se quiere ir más lejos, añadir Resilience4j con un *circuit breaker* que corte las llamadas tras N fallos consecutivos.

## 11. Pruebas automatizadas

**Fortaleza:** 17 clases de prueba que cubren los seis controladores, el servicio de JWT, las cookies, el *rate limiter* y la auditoría. Destacan tres que no suelen aparecer en proyectos de curso: `SqlInjectionSecurityTest`, `SecurityHeadersTest` y `SwaggerUiTest`. También hay pruebas de integración contra la base (`ResumenEspeciesIntegrationTest`, `TriggerActualizadoEnIntegrationTest`) que verifican la función almacenada y el trigger, es decir, se prueba también la lógica que vive en PostgreSQL y no solo la de Java.

**Limitación observada:** hay un hueco preciso y una inconsistencia de organización.

- **La integración con la API externa no tiene ninguna prueba.** No existe `ExternalApiServiceTest` ni `ExternalApiControllerTest` en `Backend/src/test/java/com/biopet/`. Es la única pieza funcional del sistema sin cobertura propia, y justamente la que más caminos de fallo tiene: caché vacía, caché con JSON corrupto, respuesta vacía del tercero, timeout, y fallo de escritura en Redis. Los cinco caminos que enumeré en la sección 10 son, hoy, no verificados.
- **La organización de los paquetes de prueba es mixta:** `AuthControllerTest`, `MascotaControllerTest` y otros cuelgan del paquete raíz `com.biopet`, mientras que `JwtServiceTest` o `ResumenEspeciesIntegrationTest` sí están en subpaquetes (`com.biopet.security`, `com.biopet.repository`). Al no reflejar la estructura de `main`, cuesta más ubicar qué está probado y qué no.

**Mejora recomendada:** agregar `ExternalApiServiceTest` con `MockRestServiceServer` cubriendo al menos el acierto de caché, el fallo del tercero y la respuesta vacía; y reubicar las clases de prueba para que el árbol de `src/test/java` sea espejo del de `src/main/java`.

## 12. Docker, entorno y mantenibilidad

**Fortaleza:** el `docker-compose.yml` está por encima del promedio: imágenes de terceros fijadas por digest `sha256` y no solo por tag, `healthcheck` en los cuatro servicios y `depends_on` con `condition: service_healthy`, de modo que el backend no arranca antes de que PostgreSQL y Redis estén listos. Eso elimina la clase de fallos intermitentes de arranque que suele obligar a "levantar dos veces". Sumado al `Makefile` y a los ADR numerados, el proyecto es reproducible por alguien externo, que es exactamente lo que esta revisión pudo comprobar en la práctica.

**Limitación observada:** PostgreSQL y Redis publican sus puertos al host (`5432:5432` y `6379:6379`, [`docker-compose.yml:9-10` y `25-26`](../../../docker-compose.yml)) y Redis lo hace **sin contraseña** (`requirepass` no está configurado). Mientras todo corra en una máquina de desarrollo el impacto es nulo, pero cualquiera en la misma red del equipo puede leer la caché y, más importante, la lista negra de tokens; borrar esa lista reviviría tokens ya revocados. Como el propio README declara que el sistema "no está certificado como listo para producción" y que se evaluó "sin evaluación de alta disponibilidad", esto no es un fallo de la entrega, pero sí un punto que debe quedar registrado antes de cualquier despliegue compartido.

Un detalle menor de mantenibilidad: `GlobalExceptionHandler` tiene el último manejador (`errorApiExterna`, líneas 94-100) con una indentación distinta al resto del archivo, señal de que se agregó después sin pasar por un formateador. El proyecto no tiene configurado ningún verificador de estilo (Checkstyle o Spotless) en el `pom.xml`.

**Mejora recomendada:** no publicar los puertos de PostgreSQL y Redis al host salvo cuando se necesiten para depurar (dejarlos accesibles solo dentro de la red de Compose), y añadir `requirepass` a Redis tomando la clave del `.env`; incorporar Spotless al `pom.xml` para que el formato deje de depender del editor de cada integrante.

---

## Cierre

### Fortalezas principales

1. **Disciplina arquitectónica sostenida.** Las capas MVC no se rompen en ningún punto de los seis controladores, y decisiones como `open-in-view: false`, `ddl-auto: validate` y el uso de `record` para los DTOs muestran que el equipo entendió el *porqué* de cada configuración y no solo copió una plantilla.
2. **Mínimo privilegio real en la base de datos.** Separar el usuario de aplicación (`biopet_app`) del usuario de migraciones (`biopet_user`) es una práctica de entorno productivo poco frecuente en un PFC.
3. **Contrato de errores uniforme.** El uso consistente de `ProblemDetail` (RFC 7807), con `Retry-After` en el 429 y un 502 propio para el fallo del tercero, le da a la API una superficie de error predecible.
4. **Pruebas orientadas a riesgo, no solo a cobertura.** `SqlInjectionSecurityTest`, `SecurityHeadersTest` y las pruebas de integración contra la función almacenada demuestran que se probó lo que puede fallar, no únicamente lo que es fácil de probar.
5. **Reproducibilidad verificada.** El *digest-pinning* y los `healthcheck` encadenados permitieron levantar el sistema desde cero sin intervención manual, lo que hizo posible esta misma revisión.

### Debilidades identificadas

1. **El esquema de base de datos tiene tres fuentes y una ya divergió:** `db/schema.sql` solo contiene `usuarios` y `mascotas` pese a que existen las migraciones V2, V3 y V4; el sistema arranca correctamente por efecto de `baseline-on-migrate`, no por sincronización real.
2. **El contrato OpenAPI no describe el comportamiento real:** cero anotaciones `@Operation`/`@ApiResponse` en todo el backend, versión desincronizada (`v0.1.0` frente a `v0.9.0-rc`) y ausencia del esquema de autenticación por cookie, que es el que usa el frontend.
3. **La estrategia de caché no escala con la escritura:** clave por email que duplica entradas idénticas para el personal de clínica, e invalidación `allEntries = true` que vacía toda la caché ante cualquier alta.
4. **Secretos con valor por defecto versionado:** si `JWT_SECRET` falta, la aplicación arranca firmando tokens con una clave publicada en el repositorio, sin ningún aviso.
5. **La integración con la API externa carece de pruebas y de degradación:** es la única pieza funcional sin cobertura, y ante una caída del tercero responde 502 aun teniendo caché vencida utilizable.
6. **Ruido de autorización:** una comprobación de propiedad inalcanzable en `actualizar()`/`eliminar()`, expresiones `hasAnyRole` duplicadas como cadenas en seis controladores, y un campo `rol` obligatorio en el registro público que el servidor descarta.

### Mejoras recomendadas (por prioridad)

| Prioridad | Acción | Impacto |
|---|---|---|
| Alta | Eliminar el valor por defecto de `JWT_SECRET` para que la app falle al arrancar sin él | Cierra un riesgo de falsificación de tokens ante un error de despliegue |
| Alta | Unificar el esquema en Flyway: retirar `db/schema.sql` del Compose y borrar `database/migrations/` | Elimina una divergencia ya existente entre fuentes del esquema |
| Alta | Añadir `ExternalApiServiceTest` y servir caché vencida ante fallo del tercero | Cubre la única pieza sin pruebas y evita 502 evitables |
| Media | Cachear por alcance (`global` / `duenio-{id}`) e invalidar de forma dirigida | Reduce memoria y sostiene la tasa de aciertos bajo escritura |
| Media | Anotar la API con `@Operation`/`@ApiResponses`, declarar el esquema de cookie y tomar la versión del `pom.xml` | Hace que el contrato publicado describa el sistema real |
| Media | Introducir `UsuarioPrincipal` con `id` y `rol` para eliminar la segunda consulta por request | Quita una consulta a `usuarios` en cada petición autenticada |
| Baja | Prefijo `/api/v1/`, `{id:\\d+}` uniforme, meta-anotaciones de roles, Spotless | Consistencia y mantenibilidad a mediano plazo |

### Valoración general

BIOPET es un PFC sólido, y su mérito principal está en las decisiones que **no se ven** desde la interfaz: la separación de credenciales de base de datos, `ddl-auto: validate`, el *digest-pinning* de imágenes, las cabeceras de seguridad y el contrato de errores RFC 7807. Son elecciones que no aportan ninguna funcionalidad visible y que, por eso mismo, suelen omitirse en un trabajo de curso; aquí están presentes y justificadas en ADRs.

Dicho eso, mi valoración es que el proyecto se encuentra en una etapa donde **la calidad de la implementación va por delante de la calidad del contrato y de la configuración**. El código de negocio está mejor cuidado que lo que lo rodea: el esquema tiene tres fuentes con una ya divergente, el OpenAPI publicado describe una API distinta de la que existe, la caché está configurada de una forma que se degrada apenas hay escrituras, y hay secretos con valor por defecto versionado. Ninguno de esos puntos rompe el sistema hoy —y esa es justamente la parte incómoda: son fallos que se manifiestan al desplegar, al escalar o al actualizar dependencias, no al ejecutar `make up`.

La buena noticia es que casi todas las correcciones son de bajo costo y no requieren rediseño: quitar un archivo del Compose, borrar un valor por defecto, cambiar una expresión de clave de caché, añadir una clase de prueba. Con esas seis acciones de prioridad alta y media, BIOPET pasaría de ser un proyecto académico muy bien ejecutado a uno defendible como base de un sistema real.

Cierro señalando lo que considero el mayor acierto metodológico del equipo original: el `README.md` declara sus propias limitaciones de forma explícita en lugar de ocultarlas. Varios de los hallazgos de esta revisión son extensiones técnicas de límites que ellos mismos ya habían reconocido, y eso hace que la revisión cruzada sea un ejercicio de profundización y no de descubrimiento de omisiones.
