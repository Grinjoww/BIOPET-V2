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
| `admin@biopet.com' OR '1'='1` | `email` en `POST /api/auth/login` | Rechazado por `@Email` (Bean Validation, 422) antes de que la petición llegue a `AuthService.login()`, o en su defecto por `BadCredentialsException` (401) — nunca autentica, nunca llega a una consulta SQL con ese contenido interpretado como código |
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
| `loginConEmailDeInyeccionNoAutentica` | El payload de inyección en `email` nunca produce `200`; no emite `access_token`/`refresh_token`; la respuesta no filtra información interna; un login válido posterior sigue funcionando |
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

## Reproducción

```bash
cd Backend
mvn -Dtest=SqlInjectionSecurityTest test
```

## Limitaciones

- No se probaron técnicas de inyección a ciegas (time-based, boolean-based)
  porque no existe ningún parámetro `String` que llegue sin conversión a una
  consulta SQL en este proyecto — el único parámetro dinámico de una consulta
  nativa (`duenioId`) es `Long`.
- No se auditó inyección en encabezados HTTP ni en JSON anidado más allá de
  los campos explícitamente probados.
