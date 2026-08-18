# Handoff F13 (Fred) — Mapa requisito funcional → procedimiento/función SQL

> **Para Zaida**: lista para copiar a `docs/trazabilidad/matriz.csv` (columna
> `modulo_codigo` / evidencia) cuando actualices la matriz. NO toco `matriz.csv`
> en esta rama. Verificado contra el código actual de `main` (post PR #8).
> El catálogo técnico completo de cada objeto está en `docs/basedatos/CATALOGOSP.md`.

## Requisitos que usan un SP/función (mapa directo)

| id_requisito | tipo_acceso | Objeto SQL | Ruta exacta | Endpoint / uso | Evidencia |
|---|---|---|---|---|---|
| REQ-F-021 | SP | `fn_resumen_mascotas_por_especie` | `db/procs/fn_resumen_mascotas_por_especie.sql` | `GET /api/mascotas/resumen-especies` (`MascotaController:63` → `MascotaService.resumenPorEspecie:104`) | `ResumenEspeciesIntegrationTest` (Testcontainers, PostgreSQL real) |
| REQ-NF-013 | SP | `fn_resumen_mascotas_por_especie` | `db/procs/fn_resumen_mascotas_por_especie.sql` | `GET /api/mascotas/resumen-especies` (mismo SP de REQ-F-021) | `ResumenEspeciesIntegrationTest` + `docs/basedatos/CATALOGOSP.md` |

## Objetos SQL definidos, catalogados y probados — sin requisito/endpoint aún

Las 6 rutinas están versionadas, replicadas en Flyway (`V5__procedimientos_biopet.sql`),
catalogadas y con prueba de integración real, pero **solo el resumen por especie
tiene endpoint conectado hoy** (verificado por grep en `main`). Si la matriz
tiene requisitos pendientes que correspondan a estas operaciones (REQ-F-014,
REQ-F-016, REQ-F-017, REQ-F-018, REQ-F-019, REQ-F-020, REQ-F-022 están
`pendiente` en `matriz.csv`), el mapa para completarlos es:

| Objeto SQL | Ruta exacta | Operación | Invocación (repo) | Candidatos en matriz (estado `pendiente`) |
|---|---|---|---|---|
| `fn_historial_clinico_mascota` | `db/procs/fn_historial_clinico_mascota.sql` | Historial clínico consolidado (6 tablas: datos, dueño, conteos, última consulta, vacunas) | `ProcedimientoBiopetRepository.historialClinicoMascota:29` (`@Query` nativa, `@Param`) | REQ-F-016 (HU-015/CU-15) |
| `fn_reporte_dashboard` | `db/procs/fn_reporte_dashboard.sql` | Indicadores dashboard en rango de fechas (4 tablas) | `ProcedimientoBiopetRepository.reporteDashboard:32` (`@Query` nativa) | REQ-F-017 (HU-016/CU-16) |
| `fn_siguiente_numero_ficha` | `db/procs/fn_siguiente_numero_ficha.sql` | Código `PREFIJO-NNNNNN` vía `seq_ficha_biopet` | `ProcedimientoBiopetRepository.siguienteNumeroFicha:36` (`@Query` nativa) | REQ-F-018 (HU-017/CU-17) y afines |
| `sp_actualizar_estado_citas_masivas` | `db/procs/sp_actualizar_estado_citas_masivas.sql` | UPDATE masivo controlado de citas con conteo de afectadas | `ProcedimientoBiopetRepository.actualizarEstadoCitasMasivas:39` (`@Procedure`, `{call ...}`) | REQ-F-015 (HU-014/CU-14, estado `implementado`) |
| `sp_registrar_consulta_validada` | `db/procs/sp_registrar_consulta_validada.sql` | Inserta consulta solo si mascota activa + veterinario válido; `RAISE EXCEPTION` si no | `ProcedimientoBiopetRepository.registrarConsultaValidada:44` (`@Procedure`) | REQ-F-013 (HU-012/CU-12, estado `implementado`) |

## Notas de verificación (para la matriz)

1. **Columna `tipo_acceso`**: los requisitos con SP usan `SP` (REQ-F-021 y
   REQ-NF-013 ya lo tienen así en `matriz.csv`).
2. **Columna `modulo_codigo`**: usar la ruta `db/procs/<archivo>.sql` exacta
   (tabla de arriba) o el nombre del método del repositorio.
3. **Pruebas automatizadas asociadas** (columna `prueba_automatizada`):
   - `ResumenEspeciesIntegrationTest` → `fn_resumen_mascotas_por_especie`
   - `ProcedimientosBiopetIntegrationTest` → las 6 rutinas (ejecuta los 6
     archivos contra PostgreSQL real vía Testcontainers)
   - `BiopetAppRolMinimoPrivilegiosIntegrationTest` → confirma que `biopet_app`
     solo tiene `EXECUTE` sobre las 6 rutinas (F05)
4. **CI**: `sql-audit` (workflow `ci.yml`) escanea `db/procs/*.sql` buscando
   SQL dinámico inseguro — cualquier objeto nuevo queda cubierto
   automáticamente sin tocar el workflow.
5. **No inventado**: la columna de candidatos marca los requisitos `pendiente`
   de `matriz.csv` que por descripción (HU/CU en SRS) coinciden con la
   operación; la decisión de asociación final es de Zaida con el SRS en mano.

## Control de acceso (F05, complementa la matriz)

`db/procs/zz_grants_biopet_app.sql` otorga `GRANT EXECUTE` de las 6 rutinas
solo a la cuenta de aplicación `biopet_app` (privilegios mínimos, ADR-004) —
evidencia en `BiopetAppRolMinimoPrivilegiosIntegrationTest`.