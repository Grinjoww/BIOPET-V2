# Catálogo de procedimientos y funciones almacenadas — BIOPET

Inventario al 100% de los SP/funciones de `db/procs/` (F04). Cada fila es trazable
a un archivo `.sql` real del repositorio. Categorías (rúbrica P1): 1) multi-tabla,
2) agregado, 3) reporte, 4) actualización masiva, 5) validación cruzada,
6) código secuencial.

| Nombre | Categoría | Propósito | Parámetros IN/OUT/INOUT | Cursores | Tablas afectadas | Archivo |
|---|---|---|---|---|---|---|
| `fn_resumen_mascotas_por_especie` | 2) Agregado | Resumen de mascotas activas agrupadas por especie, filtrable por dueño | IN `p_duenio_id` BIGINT (default `NULL`) | No (RETURNS TABLE) | Lectura: `mascotas` | `db/procs/fn_resumen_mascotas_por_especie.sql` |
| `fn_historial_clinico_mascota` | 1) Multi-tabla | Historial clínico consolidado de una mascota: datos, dueño, conteos de consultas/citas, última consulta, última y próxima vacuna | IN `p_mascota_id` BIGINT | No (RETURNS TABLE) | Lectura: `mascotas`, `usuarios`, `consultas`, `vacunas`, `citas` | `db/procs/fn_historial_clinico_mascota.sql` |
| `fn_reporte_dashboard` | 3) Reporte | Indicadores de dashboard en una fila para un rango de fechas: mascotas activas, citas programadas, consultas y vacunas en rango, mascotas sin consulta | IN `p_desde` DATE, IN `p_hasta` DATE | No (RETURNS TABLE) | Lectura: `mascotas`, `citas`, `consultas`, `vacunas` | `db/procs/fn_reporte_dashboard.sql` |
| `fn_siguiente_numero_ficha` | 6) Código secuencial | Genera el siguiente número de ficha `PREFIJO-NNNNNN` consumiendo la secuencia dedicada `seq_ficha_biopet` | IN `p_prefijo` VARCHAR (default `'FICHA'`), OUT `p_codigo` VARCHAR | No (OUT escalar) | Secuencia `seq_ficha_biopet` | `db/procs/fn_siguiente_numero_ficha.sql` |
| `sp_actualizar_estado_citas_masivas` | 4) Actualización masiva | UPDATE controlado del estado de citas de un veterinario por estado anterior y fecha límite, con conteo de filas afectadas | IN `p_veterinario_id` BIGINT, IN `p_estado_anterior` VARCHAR(20), IN `p_estado_nuevo` VARCHAR(20), IN `p_fecha_limite` TIMESTAMPTZ, OUT `p_afectadas` BIGINT | No | Escritura: `citas` | `db/procs/sp_actualizar_estado_citas_masivas.sql` |
| `sp_registrar_consulta_validada` | 5) Validación cruzada | Registra una consulta solo si la mascota existe y está activa y el veterinario es VETERINARIO/ADMIN activo; `RAISE EXCEPTION` si no | IN `p_mascota_id` BIGINT, IN `p_veterinario_id` BIGINT, IN `p_motivo` VARCHAR(200), IN `p_diagnostico` VARCHAR(500), IN `p_tratamiento` VARCHAR(500), IN `p_observaciones` VARCHAR(500) (opcionales: `NULL` si no aplican, sin `DEFAULT` por restricción de PostgreSQL sobre OUT tras parámetros con default), OUT `p_consulta_id` BIGINT | No | Lectura: `mascotas`, `usuarios`; escritura: `consultas` | `db/procs/sp_registrar_consulta_validada.sql` |

## Funciones complementarias fuera de db/procs/

| Nombre | Propósito | Archivo |
|---|---|---|
| `set_actualizado_en` | Función trigger que mantiene `actualizado_en` sincronizado en cada `UPDATE` de `usuarios` y `mascotas` (y de `citas`, `consultas`, `vacunas` vía migraciones) | `db/schema.sql` y `Backend/src/main/resources/db/migration/V1__schema_inicial.sql` |

## Notas de invocación (F02)

Las 6 rutinas se invocan formalmente desde Java en
`Backend/src/main/java/com/biopet/repository/ProcedimientoBiopetRepository.java`.
PostgreSQL distingue funciones de procedimientos, por lo que el mecanismo JPA
debe ajustarse al tipo de rutina (no se usa `@Query` con texto libre arbitrario
ni JDBC directo desde servicios):

- Las 4 **funciones** (`fn_*`) se invocan con `@Query(nativeQuery = true)` y
  `SELECT * FROM fn_...( :param )`: PostgreSQL invoca las funciones con `SELECT`,
  no con `CALL`. Las de `RETURNS TABLE` se proyectan a interfaces
  (`ResumenEspecie`, `HistorialClinico`, `ReporteDashboard`) con mapeo por
  nombre de columna (camelCase en SQL ↔ getters).
- Los 2 **procedimientos** (`sp_*`) se invocan con `@Procedure`: Spring Data
  genera `{call sp_...(?)}`, que es la única sintaxis válida para PROCEDURE.
- Los OUT escalares (`p_afectadas`, `p_consulta_id`) se leen con
  `outputParameterName` en `@Procedure`; `fn_siguiente_numero_ficha` declara
  `OUT p_codigo` y se invoca con `SELECT p_codigo FROM fn_siguiente_numero_ficha(...)`
  para que PostgreSQL JDBC devuelva el escalar como columna.
- Sin `@Procedure` sobre funciones: Hibernate generaría `CALL fn_...(...)`, que
  PostgreSQL rechaza con `procedure ... does not exist` (verificación F03 real).

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
- La migración `V5__procedimientos_biopet.sql` replica las definiciones y los
  mismos grants condicionales para el flujo Flyway.

## Verificación automatizada

Cubiertas con pruebas de integración con Testcontainers (PostgreSQL real
desechable) en `Backend/src/test/java/com/biopet/repository/`:

- `ResumenEspeciesIntegrationTest.java` — caso feliz de `fn_resumen_mascotas_por_especie`.
- `ProcedimientosBiopetIntegrationTest.java` — caso feliz y parámetro inválido
  para cada una de las 6 rutinas.
- `BiopetAppRolMinimoPrivilegiosIntegrationTest.java` — privilegios mínimos de
  `biopet_app`: ni de más (sin DDL, sin objetos fuera de permiso) ni de menos
  (CRUD sobre tablas de dominio y EXECUTE de las 6 rutinas).

Pendiente explícito: ejecución real de `mvn clean verify` y de `docker compose up`
(verificaciones F03/F05) — los resultados se pegarán aquí cuando se ejecuten.
