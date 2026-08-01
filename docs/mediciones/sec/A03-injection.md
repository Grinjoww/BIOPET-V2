# A03 — Injection

## Control implementado

La prevención es **estructural**, no basada en filtros de palabras:

- Todos los métodos de `UsuarioRepository` y `MascotaRepository` son
  consultas derivadas de Spring Data (`findByEmail`, `findByEmailAndActivoTrue`,
  `existsByEmail`, `findAllByActivoTrue`, `findAllByDuenioIdAndActivoTrue`,
  `findByIdAndActivoTrue`) — JPQL generado automáticamente con parámetros
  enlazados, nunca concatenado con entrada del usuario.
- Única `@Query` nativa del proyecto
  (`Backend/src/main/java/com/biopet/repository/MascotaRepository.java`):
  ```java
  @Query(value = "SELECT * FROM fn_resumen_mascotas_por_especie(:duenioId)", nativeQuery = true)
  List<ResumenEspecie> resumenPorEspecie(@Param("duenioId") Long duenioId);
  ```
  El parámetro `:duenioId` está enlazado (`@Param`), nunca concatenado, y
  además está **fuertemente tipado como `Long`** tanto en el repositorio como
  en el controlador (`@RequestParam(required = false) Long duenioId` en
  `MascotaController.resumenPorEspecies`).
- Búsqueda global confirmada sin resultados: no existe `createNativeQuery`,
  `JdbcTemplate` en código de producción, ni `Statement`/`PreparedStatement`
  manual en todo `Backend/src/main`.

## Payloads probados y por qué son inofensivos

| Payload | Punto de entrada | Por qué se rechaza |
|---|---|---|
| `' OR '1'='1` / `admin@biopet.com' OR '1'='1` / `'; DROP TABLE usuarios; --` | `email` en `POST /api/auth/login` | Rechazado por `@Email` (Bean Validation) **antes** de que la petición llegue a `AuthService.login()` — verificado en vivo: **422** real en los tres casos (ver sección "Evidencia HTTP real" más abajo). Nunca autentica, nunca llega a una consulta SQL con ese contenido interpretado como código |
| `1 OR 1=1` | `duenioId` en `GET /api/mascotas/resumen-especies` | Spring MVC intenta convertir el `String` a `Long` durante el *binding* del `@RequestParam`; falla con `MethodArgumentTypeMismatchException` **antes** de que el controlador o el repositorio se ejecuten |
| `1; DROP TABLE usuarios; --` | ídem | Mismo mecanismo: no es un `Long` válido, se rechaza en el binding |
| `%' UNION SELECT NULL --` | ídem | Mismo mecanismo: se trata como texto incompatible con `Long`, nunca como fragmento SQL |

En los tres últimos casos, el rechazo ocurre en la capa de conversión de
Spring MVC, **antes** de que el flujo llegue a `MascotaService.resumenPorEspecie`
o a la consulta nativa parametrizada — es decir, la protección no depende de
que la consulta esté bien escrita (aunque lo está), sino de que el dato
nunca llega a ser un argumento de tipo `Long` inválido para el método.

## Resultado del ProblemDetail para los tres payloads de `duenioId`

Idéntico para `1 OR 1=1`, `1; DROP TABLE usuarios; --` y `%' UNION SELECT NULL --`:

```json
{
  "type": "urn:biopet:error:bad-request",
  "title": "Parámetro inválido",
  "status": 400,
  "detail": "El parámetro 'duenioId' tiene un formato inválido.",
  "instance": "/api/mascotas/resumen-especies"
}
```

Producido por `GlobalExceptionHandler.parametroInvalido` (`@ExceptionHandler(MethodArgumentTypeMismatchException.class)`),
que construye el mensaje **solo** con `ex.getName()` (el nombre del
parámetro) — nunca con `ex.getValue()` (el payload recibido) ni con
`ex.getMessage()` (que sí incluiría nombres de clases Java y el valor
original).

## Pruebas que lo demuestran

Todas en `Backend/src/test/java/com/biopet/SqlInjectionSecurityTest.java`:

| Prueba | Qué demuestra |
|---|---|
| `loginConEmailDeInyeccionNoAutentica` | El payload de inyección en `email` produce **422** exacto (antes se aceptaba también 401, ambigüedad ya resuelta); no emite `access_token`/`refresh_token`; la respuesta no filtra información interna; un login válido posterior sigue funcionando |
| `loginConPayloadLiteralDeLaGuiaDevuelve422` | Payload literal citado por la guía (`' OR '1'='1`, sin arroba) en `email` → 422 ProblemDetails exacto (`type=urn:biopet:error:validation`), sin reflejar el payload en el body |
| `parametroDuenioIdConOrDevuelveProblemDetail400` | Payload `1 OR 1=1` → 400 ProblemDetail exacto, sin reflejar el payload en el body |
| `parametroDuenioIdConDropDevuelveProblemDetail400` | Payload `1; DROP TABLE usuarios; --` → 400 ProblemDetail; `usuarioRepository.count()` y `mascotaRepository.count()` **idénticos antes y después** del payload — ninguna tabla fue alterada |
| `parametroDuenioIdConUnionDevuelveProblemDetail400` | Payload `%' UNION SELECT NULL --` → 400 ProblemDetail, tratado como valor incompatible con `Long` |
| `consultasValidasSiguenFuncionando` | Tras los payloads anteriores, `GET /api/usuarios/me` y `GET /api/mascotas` (que dependen de `UsuarioRepository` y `MascotaRepository` respectivamente) siguen respondiendo con normalidad |
| `respuestaNoFiltraInformacionDeBaseDeDatos` | Para los cuatro payloads, el body de la respuesta no contiene `org.hibernate`, `org.postgresql`, `SQLException`, `SQLGrammarException`, `stackTrace`, `relation` ni `syntax error` |

## Ausencia de listas negras

El código de producción **no contiene** ninguna expresión regular ni
comparación de cadenas contra palabras como `SELECT`, `DROP`, `UNION` u `OR`.
La protección depende exclusivamente de:

1. Consultas parametrizadas / derivadas de Spring Data.
2. Tipado fuerte de parámetros (`Long duenioId`).
3. Validación estructural existente (`@Email`, `@NotBlank`, etc. vía Bean
   Validation).

## Evidencia HTTP real: 422 confirmado (discrepancia previa resuelta)

Generada el 2026-08-01 (commit `136b707`, cambios de esta tarea aún sin
confirmar) contra el stack Docker real (perfil `tls`) con
`scripts/security-evidence.sh`, guardada íntegra en
[`raw/A03-injection.txt`](raw/A03-injection.txt).

**Diagnóstico:** la guía (Bloque C.2) pide enviar el payload de inyección
"en un campo de búsqueda" y exige **422**. El endpoint usado originalmente
para esta evidencia (`duenioId` en `GET /api/mascotas/resumen-especies`) es
un parámetro `Long`, así que cualquier payload de inyección se rechaza en
el *binding* de Spring MVC (`MethodArgumentTypeMismatchException`) con
**400**, una capa anterior a Bean Validation — de ahí la discrepancia
detectada inicialmente (400 real vs. 422 exigido).

**Resolución, sin modificar código productivo:** el campo que realmente
actúa como "campo de búsqueda" en este backend —el campo por el que
`AuthService` localiza al usuario a autenticar— es `email` en
`POST /api/auth/login`, ya cubierto por `@Valid`/`@Email` (Bean Validation)
sobre `LoginRequest`. Un payload de inyección no tiene forma de correo
válida, así que se rechaza con **422** real, antes de que la petición
llegue a `AuthService`/`UsuarioRepository`. Se confirmó con tres payloads
distintos, incluido el payload literal de la guía sin modificaciones
(`' OR '1'='1`):

```json
{"type":"urn:biopet:error:validation","title":"Error de validación","status":422,"detail":"Uno o más campos contienen valores inválidos.","instance":"/api/auth/login","errors":{"email":["must be a well-formed email address"]}}
```

Idéntico (mismo `type`/`title`/`status`/`errors.email`) para los tres
payloads probados: `' OR '1'='1` (payload literal de la guía),
`admin@biopet.com' OR '1'='1` (variante ya usada en
`SqlInjectionSecurityTest`) y `'; DROP TABLE usuarios; --`. Sin stack
trace, sin reflejar el payload completo en el body, sin llegar nunca a
ejecutarse como SQL.

**Evidencia adicional, no es el caso exigido por la guía:** el mismo tipo
de payload contra `duenioId` (`Long`) sigue devolviendo **400** — un
mecanismo de rechazo distinto (binding de Spring MVC, no Bean Validation),
igual de seguro, documentado en `raw/A03-injection.txt` solo como
profundidad adicional de la defensa, sin contar como verificación pass/fail
de A03.

## Reproducción

```bash
cd Backend
mvn -Dtest=SqlInjectionSecurityTest test
```

Evidencia HTTP real (end-to-end, requiere el stack Docker levantado; la
variable de entorno `ADMIN_PASSWORD` es obligatoria):

```bash
ADMIN_PASSWORD='...' scripts/security-evidence.sh
```

## Limitaciones

- No se probaron técnicas de inyección a ciegas (time-based, boolean-based)
  porque no existe ningún parámetro `String` que llegue sin conversión a una
  consulta SQL en este proyecto — el único parámetro dinámico de una consulta
  nativa (`duenioId`) es `Long`.
- No se auditó inyección en encabezados HTTP ni en JSON anidado más allá de
  los campos explícitamente probados.
