# Catálogo de procedimientos y funciones almacenadas — BIOPET

Inventario al 100% de las rutinas de `db/procs/` (F04). Cada fila es trazable
a un archivo `.sql` real del repositorio. Categorías (rúbrica P1): 1) multi-tabla,
2) agregado, 3) reporte, 4) actualización masiva, 5) validación cruzada,
6) código secuencial.

**Nota (F02, cierre de acceso JPA formal):** las 6 rutinas son objetos
PostgreSQL `PROCEDURE`. Las 4 con prefijo `fn_*` originalmente eran
`FUNCTION`; se reclasificaron a `PROCEDURE` (conservando el nombre `fn_...`
por compatibilidad con el resto del repositorio) para poder invocarse desde
JPA con `@Procedure` — ver "Notas de invocación" más abajo para el motivo
técnico exacto.

**Estado final vs. migraciones Flyway:** los archivos de `db/procs/`
representan el estado final v1.0.0 (las 4 `fn_*` ya como `PROCEDURE`). Esto
**no** se aplicó reescribiendo `V5__procedimientos_biopet.sql` — esa
migración ya estaba publicada en `main` y pudo haberse aplicado en bases
persistentes; reescribirla habría producido `checksum mismatch` en Flyway.
`V5` se dejó intacta con su contenido histórico original (las 4 `fn_*` como
`FUNCTION`, invocadas en su momento con `@Query(nativeQuery = true)`). La
reclasificación a `PROCEDURE` se hizo en una migración nueva y aditiva,
`Backend/src/main/resources/db/migration/V6__formalizar_procedimientos_jpa.sql`,
que hace `DROP FUNCTION IF EXISTS` de las 4 rutinas creadas por V5 y las
vuelve a crear como `PROCEDURE` con la firma final. `V6` se aplica sobre
cualquier base que ya tenga V1–V5 (persistente o nueva); Flyway ejecuta
V1→V6 en orden en un checkout nuevo.

| Nombre | Tipo PostgreSQL | Categoría | Propósito | Parámetros IN/OUT/INOUT | Cursores | Tablas afectadas | Archivo |
|---|---|---|---|---|---|---|---|
| `fn_resumen_mascotas_por_especie` | PROCEDURE | 2) Agregado | Resumen de mascotas activas agrupadas por especie, filtrable por dueño | IN `p_duenio_id` BIGINT (`NULL` explícito para el resumen global; sin `DEFAULT` por restricción de PostgreSQL sobre OUT tras parámetros con default), OUT `p_cursor` refcursor | Sí (OUT refcursor) | Lectura: `mascotas` | `db/procs/fn_resumen_mascotas_por_especie.sql` |
| `fn_historial_clinico_mascota` | PROCEDURE | 1) Multi-tabla | Historial clínico consolidado de una mascota: datos, dueño, conteos de consultas/citas, última consulta, última y próxima vacuna | IN `p_mascota_id` BIGINT, OUT `p_cursor` refcursor | Sí (OUT refcursor) | Lectura: `mascotas`, `usuarios`, `consultas`, `vacunas`, `citas` | `db/procs/fn_historial_clinico_mascota.sql` |
| `fn_reporte_dashboard` | PROCEDURE | 3) Reporte | Indicadores de dashboard en una fila para un rango de fechas: mascotas activas, citas programadas, consultas y vacunas en rango, mascotas sin consulta | IN `p_desde` DATE, IN `p_hasta` DATE, OUT `p_cursor` refcursor | Sí (OUT refcursor) | Lectura: `mascotas`, `citas`, `consultas`, `vacunas` | `db/procs/fn_reporte_dashboard.sql` |
| `fn_siguiente_numero_ficha` | PROCEDURE | 6) Código secuencial | Genera el siguiente número de ficha `PREFIJO-NNNNNN` consumiendo la secuencia dedicada `seq_ficha_biopet` | IN `p_prefijo` VARCHAR (explícito; `NULL`/vacío se normaliza a `'FICHA'` dentro de la rutina; sin `DEFAULT` por la misma restricción de PostgreSQL), OUT `p_codigo` VARCHAR | No (OUT escalar) | Secuencia `seq_ficha_biopet` | `db/procs/fn_siguiente_numero_ficha.sql` |
| `sp_actualizar_estado_citas_masivas` | PROCEDURE | 4) Actualización masiva | UPDATE controlado del estado de citas de un veterinario por estado anterior y fecha límite, con conteo de filas afectadas | IN `p_veterinario_id` BIGINT, IN `p_estado_anterior` VARCHAR(20), IN `p_estado_nuevo` VARCHAR(20), IN `p_fecha_limite` TIMESTAMPTZ, OUT `p_afectadas` BIGINT | No | Escritura: `citas` | `db/procs/sp_actualizar_estado_citas_masivas.sql` |
| `sp_registrar_consulta_validada` | PROCEDURE | 5) Validación cruzada | Registra una consulta solo si la mascota existe y está activa y el veterinario es VETERINARIO/ADMIN activo; `RAISE EXCEPTION` si no | IN `p_mascota_id` BIGINT, IN `p_veterinario_id` BIGINT, IN `p_motivo` VARCHAR(200), IN `p_diagnostico` VARCHAR(500), IN `p_tratamiento` VARCHAR(500), IN `p_observaciones` VARCHAR(500) (opcionales: `NULL` si no aplican, sin `DEFAULT` por restricción de PostgreSQL sobre OUT tras parámetros con default), OUT `p_consulta_id` BIGINT | No | Lectura: `mascotas`, `usuarios`; escritura: `consultas` | `db/procs/sp_registrar_consulta_validada.sql` |

## Funciones complementarias fuera de db/procs/

| Nombre | Propósito | Archivo |
|---|---|---|
| `set_actualizado_en` | Función trigger que mantiene `actualizado_en` sincronizado en cada `UPDATE` de `usuarios` y `mascotas` (y de `citas`, `consultas`, `vacunas` vía migraciones) | `db/schema.sql` y `Backend/src/main/resources/db/migration/V1__schema_inicial.sql` |

## Notas de invocación (F02)

Las 6 rutinas se invocan formalmente desde Java en
`Backend/src/main/java/com/biopet/repository/ProcedimientoBiopetRepository.java`,
las 6 con `@Procedure` (mecanismo JPA formal). No se usa `@Query` con texto
libre arbitrario ni JDBC directo desde servicios; `@Query(nativeQuery = true)`
no se considera invocación formal a efectos de este requisito.

**Motivo técnico de la reclasificación `FUNCTION` → `PROCEDURE` (verificación
real F03):** Spring Data JPA / Hibernate generan siempre una sentencia
`CALL` para `@Procedure`, y PostgreSQL solo acepta `CALL` sobre objetos
`PROCEDURE`. Antes de este cierre, las 4 rutinas `fn_*` eran `FUNCTION`
(`RETURNS TABLE` u `OUT` escalar directo, creadas en `V5`) invocadas con
`@Query(nativeQuery = true)`; probar `@Procedure` directamente sobre esas
`FUNCTION` — incluida una variante intermedia con `OUT p_cursor refcursor`
sin cambiar el tipo de objeto — fue rechazado en ambos casos por PostgreSQL
con el error real `"... is not a procedure. Hint: To call a function, use
SELECT"`. Por eso las 4 rutinas `fn_*` se reclasificaron de `FUNCTION` a
`PROCEDURE` real (conservando su nombre `fn_...`) en `V6`, que es el mismo
tipo de objeto que ya usaban `sp_*` y que sí acepta `CALL`:

- Las 3 rutinas que devuelven un **conjunto de filas**
  (`fn_resumen_mascotas_por_especie`, `fn_historial_clinico_mascota`,
  `fn_reporte_dashboard`) exponen un único parámetro `OUT p_cursor
  refcursor` — un `PROCEDURE` no admite `RETURNS TABLE`, así que el cursor
  es el mecanismo para devolver varias filas. Se invocan con
  `@Procedure(name = "fn_...")` referenciando un
  `@NamedStoredProcedureQuery` declarado explícitamente en
  `Backend/src/main/java/com/biopet/entity/Mascota.java` con
  `@StoredProcedureParameter(mode = ParameterMode.REF_CURSOR, type =
  void.class)`. Fue necesario declarar el `@NamedStoredProcedureQuery` de
  forma explícita porque `@Procedure` con parámetros auto-derivados de los
  metadatos JDBC (sin `@NamedStoredProcedureQuery`) genera un `CALL` que
  omite el placeholder del parámetro `refcursor`, y PostgreSQL lo rechaza
  por discordancia de aridad con `procedure ... does not exist`
  (verificación real F03). El cursor abierto por `OPEN p_cursor FOR SELECT
  ...` solo es legible dentro de la misma transacción en la que se abre;
  probar con `@Transactional(readOnly = true)` en el propio método del
  repositorio falla con `InvalidDataAccessApiUsageException` ("sin
  transacción circundante"), porque Spring Data no reconoce esa transacción
  autogestionada por el propio proxy como "circundante". Por eso estos 3
  métodos no declaran su propio `@Transactional`: dependen de que el
  código llamador ya esté dentro de una transacción, como ya lo está
  `MascotaService.resumenPorEspecie` (`@Transactional(readOnly = true)`), y
  como declaran explícitamente los tests de integración que los
  ejercitan. El resultado se proyecta a interfaces (`ResumenEspecie`,
  `HistorialClinico`, `ReporteDashboard`) con mapeo por nombre de columna
  (camelCase en SQL ↔ getters).
- `fn_siguiente_numero_ficha` ya devolvía un escalar (un único `VARCHAR`),
  no una tabla, así que no necesita `refcursor` ni
  `@NamedStoredProcedureQuery`: usa `OUT p_codigo VARCHAR` directo,
  exactamente el mismo patrón que ya usan `sp_*`, y se invoca con
  `@Procedure(procedureName = "fn_siguiente_numero_ficha",
  outputParameterName = "p_codigo")`.
- Los 2 **procedimientos** (`sp_*`) no cambiaron: ya eran `PROCEDURE` y ya
  se invocaban con `@Procedure` (`{call sp_...(?)}`), que es la sentencia
  válida para `PROCEDURE`. Los OUT escalares (`p_afectadas`,
  `p_consulta_id`) se leen con `outputParameterName`.

Consecuencia documental: con este cierre, las 6 rutinas de `db/procs/`
son objetos `PROCEDURE`; ninguna es ya una `FUNCTION` real, aunque 4
conserven el prefijo `fn_` por compatibilidad con el resto del repositorio
(código, tests, SRS, trazabilidad). Esto es el estado final v1.0.0 tal como
queda **después de aplicar V6**; `V5`, tomada de forma aislada, sigue
creando esas 4 rutinas como `FUNCTION` (su contenido histórico no se
modificó — ver nota "Estado final vs. migraciones Flyway" más arriba). Ver
"Tipo PostgreSQL" en la tabla de arriba.

## Control de acceso

- `fn_resumen_mascotas_por_especie`: un usuario con rol distinto de `ADMIN` no
  puede ver el resumen de otro dueño; el `duenioId` recibido se ignora y se fuerza
  al propio del usuario autenticado (`MascotaService.resumenPorEspecie`).
- El resto de rutinas se ejecutan con los privilegios del llamador
  (`SECURITY INVOKER`, valor por defecto), por lo que `biopet_app` requiere los
  privilegios mínimos descritos en `db/roles.sql` (CRUD sobre las tablas de
  dominio, `USAGE, SELECT` sobre sus secuencias y `EXECUTE` sobre las rutinas).

## Privilegios de biopet_app (F05)

- `db/roles.sql` concede CRUD sobre `usuarios` y `mascotas` de forma explícita y,
  vía `ALTER DEFAULT PRIVILEGES`, CRUD sobre tablas creadas después (citas,
  consultas, vacunas), `USAGE, SELECT` sobre secuencias y `EXECUTE` sobre
  funciones creadas después.
- `db/procs/zz_grants_biopet_app.sql` concede explícitamente `EXECUTE` sobre las
  6 rutinas y `USAGE, SELECT` sobre `seq_ficha_biopet`, de forma condicional
  (no-op si el rol u objeto no existe), para cubrir también los PROCEDURE (que
  no siempre quedan cubiertos por los default privileges sobre FUNCTIONS).
- Para el flujo Flyway, `V5__procedimientos_biopet.sql` crea las rutinas y
  sus grants originales (las 4 `fn_*` como `FUNCTION`); `V6__formalizar_procedimientos_jpa.sql`
  hace `DROP FUNCTION` de esas 4 y las vuelve a crear como `PROCEDURE`, y
  vuelve a conceder `EXECUTE` sobre ellas a `biopet_app` (el `DROP`
  revoca implícitamente los grants anteriores sobre los objetos
  eliminados).

## Verificación automatizada

Cubiertas con pruebas de integración con Testcontainers (PostgreSQL real
desechable) en `Backend/src/test/java/com/biopet/repository/`:

- `ResumenEspeciesIntegrationTest.java` — caso feliz de `fn_resumen_mascotas_por_especie`.
- `ProcedimientosBiopetIntegrationTest.java` — caso feliz y parámetro inválido
  para cada una de las 6 rutinas.
- `BiopetAppRolMinimoPrivilegiosIntegrationTest.java` — privilegios mínimos de
  `biopet_app`: ni de más (sin DDL, sin objetos fuera de permiso) ni de menos
  (CRUD sobre tablas de dominio y EXECUTE de las 6 rutinas).

`mvn clean verify` ejecutado con Docker/Testcontainers real (PostgreSQL 16),
Flyway aplicando V1→V6: 205+ tests, 0 failures, 0 errors, BUILD SUCCESS,
cobertura JaCoCo LINE y BRANCH por encima del umbral 70 %.
