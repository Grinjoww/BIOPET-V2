# DRAFT-ADR-006 (handoff Fred) — Acceso a datos: CRUD directo vs procedimientos almacenados (estrategia híbrida)

> **Handoff para Jaime** — borrador de contenido, NO normalizado. Jaime decide
> numeración final y posible fusión con `docs/adr/ADR-007-acceso-datos.md`
> (entrega anterior, mismo tema). Este draft aporta la evidencia de la rama
> `fred/f01-f05-sp-acceso-datos` (F01–F05) con los 6 objetos SQL finales.
> Formato Nygard: contexto, decisión, alternativas, consecuencias, evidencia.

## Contexto

BIOPET usa Spring Data JPA/Hibernate con `ddl-auto: validate` (el esquema es
propiedad de Flyway, nunca de Hibernate). Hasta la Entrega 1B todo el acceso a
datos pasaba por métodos derivados de Spring Data (`findBy...`, `save`,
`findById`) — cero objetos SQL propios.

El Bloque A.2 de la guía de la Tercera Entrega exige adoptar y evidenciar una
estrategia híbrida: los CRUD elementales de una sola tabla permanecen en el
ORM; las operaciones con agregaciones, joins multi-tabla, reportes
heterogéneos o reglas transaccionales cruzadas se encapsulan en
procedimientos/funciones PostgreSQL versionados, invocados con parámetros
enlazados, nunca por concatenación.

En la rama `fred/f01-f05-sp-acceso-datos` se implementó el set completo: **4
funciones (`fn_*`) y 2 procedimientos (`sp_*`)** en `db/procs/`, replicados en
la migración `V5__procedimientos_biopet.sql` (Flyway) y en `db/schema.sql` (la
triple fuente de esquema que ya documenta ADR-004), con privilegios mínimos
para la cuenta `biopet_app` (`zz_grants_biopet_app.sql`, F05) y un catálogo
único en `docs/basedatos/CATALOGOSP.md`.

## Decisión

**Se mantiene en JPA (Spring Data):** el CRUD elemental — `save`, `findById`,
`findAllByActivoTrue(Pageable)`, `findByEmail`, soft delete como cambio del
atributo booleano `activo`, listados triviales paginados.

**Se traslada al motor PostgreSQL** toda operación que no sea CRUD elemental:

| Objeto | Clasificación (CATALOGOSP.md) | Operación |
|---|---|---|
| `fn_resumen_mascotas_por_especie` | Agregado | `COUNT` + `GROUP BY especie` con filtro opcional por dueño |
| `fn_historial_clinico_mascota` | Multi-tabla | Historial consolidado: datos + dueño + conteos + última consulta + vacunas (6 tablas) |
| `fn_reporte_dashboard` | Reporte | Indicadores en una fila para un rango de fechas (4 tablas) |
| `fn_siguiente_numero_ficha` | Código secuencial | `PREFIJO-NNNNNN` consumiendo `seq_ficha_biopet` |
| `sp_actualizar_estado_citas_masivas` | Actualización masiva | `UPDATE` controlado con conteo de filas afectadas |
| `sp_registrar_consulta_validada` | Validación cruzada | Inserta consulta solo si mascota activa y veterinario válido; `RAISE EXCEPTION` si no |

**Reglas operativas** (heredadas y ampliadas de ADR-007-acceso-datos):
- Archivo `.sql` versionado en `db/procs/` con convención `fn_<verbo>_<sustantivo>.sql` / `sp_<verbo>_<sustantivo>.sql`.
- Invocación con parámetros enlazados y nombrados: las `fn_*` con
  `@Query(nativeQuery=true)` + `@Param` (PostgreSQL invoca funciones con
  `SELECT`); los `sp_*` con `@Procedure` (Spring Data genera `{call ...}`) —
  todo centralizado en `ProcedimientoBiopetRepository`, que no expone CRUD.
- Documentación en `docs/basedatos/CATALOGOSP.md` (parámetros IN/OUT, tablas,
  privilegios) y prueba de integración con Testcontainers (PostgreSQL real).
- `GRANT EXECUTE` únicamente a `biopet_app` (privilegios mínimos, F05).

## Alternativas consideradas

**A — Todo en JPA (JPQL/GROUP BY o cómputo en Java).** Descartada: agregaciones
y reportes exigirían traer filas a memoria o JPQL con `GROUP BY` que pierde los
planes de ejecución e índices nativos; no cumple el Bloque A.2 de la guía.

**B — Todo en procedimientos almacenados (incluido el CRUD).** Descartada:
sobre-ingeniería para `save`/`findById`; renuncia al mapeo ORM idiomático;
obliga a reescribir las pruebas H2 de los repositorios simples contra
PostgreSQL real sin ganancia de rendimiento ni atomicidad.

**C — Híbrido (seleccionada).** Cada operación usa el mecanismo apropiado a su
complejidad. Coste: dos superficies de prueba y un criterio que el equipo debe
respetar al agregar operaciones nuevas.

## Consecuencias

**Positivas:**
- CRUD trivial simple y sin SQL manual; SQL no trivial versionado, auditable y
  con parámetros enlazados (mitiga inyección; `audit-sql-dynamic.sh` en CI
  escanea `db/procs/` automáticamente).
- Las agregaciones/reportes se ejecutan y optimizan dentro del motor.
- Trazabilidad completa: cada objeto tiene fila en CATALOGOSP.md, prueba de
  integración y privilegios explícitos.

**Negativas y compromisos:**
- Coexisten dos mecanismos de acceso; la persona nueva debe conocer el criterio.
- Dependencia de PL/pgSQL: portar a otro motor exige reescribir estos objetos.
- Triple fuente de esquema (migración Flyway + `db/schema.sql` + `db/procs/`)
  debe mantenerse sincronizada a mano (riesgo ya documentado en ADR-004).
- Estado real verificado: **solo `fn_resumen_mascotas_por_especie` tiene
  endpoint conectado hoy** (`GET /api/mascotas/resumen-especies`, REQ-F-021 /
  REQ-NF-013). Las otras 5 rutinas están definidas, catalogadas y probadas
  (ProcedimientosBiopetIntegrationTest, BiopetAppRolMinimoPrivilegiosIntegrationTest)
  pero **sin endpoint aún** — ver `docs/trazabilidad/handoff-fred-req-sp.md`.

## Archivos y evidencia de respaldo

- `db/procs/fn_resumen_mascotas_por_especie.sql`, `fn_historial_clinico_mascota.sql`,
  `fn_reporte_dashboard.sql`, `fn_siguiente_numero_ficha.sql`,
  `sp_actualizar_estado_citas_masivas.sql`, `sp_registrar_consulta_validada.sql`,
  `zz_grants_biopet_app.sql` (F05).
- `Backend/src/main/resources/db/migration/V5__procedimientos_biopet.sql` (Flyway).
- `Backend/src/main/java/com/biopet/repository/ProcedimientoBiopetRepository.java`
  (invocación formal JPA, F02).
- `Backend/src/test/java/com/biopet/repository/ProcedimientosBiopetIntegrationTest.java`,
  `BiopetAppRolMinimoPrivilegiosIntegrationTest.java`,
  `ResumenEspeciesIntegrationTest.java` (Testcontainers, PostgreSQL real).
- `docs/basedatos/CATALOGOSP.md` (catálogo único).
- Matriz: `docs/trazabilidad/matriz.csv` (REQ-F-021, REQ-NF-013 con `tipo_acceso=SP`).
- ADR previo a fusionar/decidir: `docs/adr/ADR-007-acceso-datos.md`.

## Referencias

- `ADR-004-postgresql.md` (cuenta `biopet_app`, esquema reproducible).
- `docs/adr/ADR-007-acceso-datos.md` (decisión híbrida previa, 1 SP).