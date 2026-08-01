# ADR-007: Estrategia híbrida de acceso a datos con JPA y funciones PostgreSQL

## Identificador
ADR-007

## Título
Estrategia híbrida de acceso a datos con JPA y funciones PostgreSQL

## Estado
Aceptado — implementado y verificado en la rama `fred/proc-resumen-especies`
(PR #9, commit `cdc8ce7`) de la Tercera Entrega (v0.9.0-rc), con el catálogo
de objetos SQL actualizado en el commit `2be9618`.

## Fecha
Tercera Entrega (julio de 2026).

## Contexto

BIOPET usa Spring Data JPA e Hibernate (`UsuarioRepository`,
`MascotaRepository`) para las operaciones CRUD sobre las entidades del
dominio, con `spring.jpa.hibernate.ddl-auto: validate`
(`Backend/src/main/resources/application.yml`) — Hibernate nunca crea ni
modifica el esquema, que es propiedad exclusiva de Flyway 9.22.3 (gestionado
por `spring-boot-starter-parent` 3.2.12, `spring.flyway.locations:
classpath:db/migration`, migración `V1__schema_inicial.sql`). PostgreSQL 16
es el motor principal; Redis 7 se usa para la blacklist de JWT revocados
(`TokenBlacklistService`) y para la caché declarativa de listados
(`MascotaService`, `@Cacheable`/`@CacheEvict`), sin relación con el acceso a
datos relacional que trata este ADR.

Desde la Entrega 1B, todo el acceso a datos de BIOPET pasaba exclusivamente
por métodos derivados de Spring Data (`findBy...`, `save`, `findById`), sin
ningún objeto SQL propio versionado. La Tercera Entrega (Bloque A.2 de la
guía) exige adoptar y evidenciar una estrategia híbrida: los CRUD elementales
de una sola tabla permanecen en el ORM, mientras que cualquier operación que
implique agregaciones, joins, reportes heterogéneos o reglas transaccionales
cruzadas debe encapsularse en un procedimiento o función SQL versionado,
invocado con parámetros enlazados, nunca por concatenación.

Como primer caso real de esa naturaleza, el resumen de mascotas activas
agrupadas por especie (`COUNT` + `GROUP BY especie`, con filtro opcional por
dueño) ya se implementó como una función PL/pgSQL versionada
(`db/procs/fn_resumen_mascotas_por_especie.sql`), invocada desde
`MascotaRepository` mediante `@Query(nativeQuery = true)` con el parámetro
nombrado `:duenioId`, y catalogada en `docs/basedatos/CATALOGO-SP.md`. Faltaba
formalizar, como ADR independiente, el criterio general que separa qué queda
en JPA y qué pasa al motor — hasta ahora solo estaba implícito en el código y
en el catálogo.

## Decisión

Se adopta y se formaliza la siguiente separación de responsabilidades:

**Se mantiene en JPA (Spring Data, `UsuarioRepository`/`MascotaRepository`)**:
- Creación (`save`) y consulta por clave primaria (`findById`,
  `findByIdAndActivoTrue`).
- Actualización de atributos escalares propios de una entidad (`save` sobre
  una entidad ya cargada, usado por `MascotaService.actualizar`).
- Eliminación lógica (*soft delete*) expresada como cambio del atributo
  booleano propio de la entidad: `Mascota.activo`
  (`Backend/src/main/java/com/biopet/entity/Mascota.java`), nunca un
  `DELETE` físico.
- Listados y búsquedas triviales sobre atributos escalares directos, con
  paginación: `findAllByActivoTrue(Pageable)`,
  `findAllByDuenioIdAndActivoTrue(Long, Pageable)`, `findByEmail`,
  `findByEmailAndActivoTrue`, `existsByEmail`.
- Relaciones de entidades mapeadas con anotaciones JPA estándar (`@Entity`,
  `@Table`), sin lógica SQL manual.

**Se traslada a una función o procedimiento PostgreSQL** toda operación que
no sea un CRUD elemental según lo anterior, en particular: agregaciones
(`COUNT`/`SUM`/`AVG`/`GROUP BY`), joins o sub-consultas entre más de una
tabla, reportes con una forma de salida distinta a una entidad del dominio,
actualizaciones o eliminaciones masivas con criterios no triviales, y
cualquier operación donde el motor ofrezca una ventaja real de rendimiento,
atomicidad o de aprovechamiento de índices frente a resolverla en Java. El
caso ya implementado, `fn_resumen_mascotas_por_especie`, es exactamente este
tipo de operación: una agregación agrupada que hubiera requerido traer todas
las filas a memoria para contarlas en Java, o construir JPQL con `GROUP BY`
menos eficiente que el plan de ejecución nativo de PostgreSQL sobre la tabla
`mascotas`.

Reglas operativas para cualquier objeto SQL de este tipo, presente o futuro:
- Se versiona como archivo `.sql` bajo `db/procs/`, con el esquema de
  nombres `fn_<verbo>_<sustantivo>.sql` (funciones) o
  `sp_<verbo>_<sustantivo>.sql` (procedimientos) — ya cumplido por
  `fn_resumen_mascotas_por_especie.sql`.
- Se invoca desde el repositorio Spring Data con parámetros **enlazados y
  nombrados** (`@Query(value = "...", nativeQuery = true)` con `@Param`, o
  `@Procedure`/`@NamedStoredProcedureQuery` cuando aplique) — nunca mediante
  concatenación de cadenas ni SQL dinámico (`EXECUTE IMMEDIATE`,
  `sp_executesql` o equivalente).
- Se documenta en `docs/basedatos/CATALOGO-SP.md`: nombre, propósito,
  parámetros de entrada/salida, tablas afectadas y privilegios de la cuenta
  de aplicación (`biopet_app`, ver ADR-004).
- Se cubre, cuando es viable, con una prueba de integración contra
  PostgreSQL real vía Testcontainers, no solo contra H2 en memoria.

## Alternativas consideradas

**Alternativa A — Todo en JPA (incluidas agregaciones y reportes vía JPQL o
cómputo en Java).**
Ventaja: un único mecanismo de acceso a datos, sin coexistencia de
paradigmas, curva de aprendizaje mínima para el equipo. Desventaja: las
agregaciones agrupadas (como el resumen por especie) requerirían traer todas
las filas a memoria para contarlas en Java, o expresarse en JPQL con `GROUP
BY`, perdiendo la posibilidad de que el planificador de PostgreSQL optimice
la consulta con sus propios índices; además, no satisface el Bloque A.2 de
la guía, que exige explícitamente encapsular este tipo de operación en el
motor.

**Alternativa B — Todo en procedimientos o funciones almacenadas (incluso
el CRUD elemental de una sola tabla).**
Ventaja: centraliza toda la lógica de acceso a datos en la base, con un
único lugar de auditoría SQL. Desventaja: sobre-ingeniería evidente para
operaciones triviales (`save`, `findById`); renuncia al mapeo objeto-relacional
idiomático que ya ofrece Spring Data sin coste adicional; complica las
pruebas unitarias que hoy corren contra H2 en memoria para los repositorios
simples (`UsuarioRepositoryTest`, pruebas basadas en `@DataJpaTest`), que
tendrían que reescribirse contra PostgreSQL real; no aporta ninguna ventaja
de rendimiento o atomicidad para un `INSERT`/`SELECT por id` de una sola
fila.

**Alternativa C — Estrategia híbrida (seleccionada).**
Ventaja: cada operación usa el mecanismo apropiado a su complejidad real;
ya está parcialmente implementada, probada y catalogada
(`fn_resumen_mascotas_por_especie`); cumple exactamente el criterio A.2 de
la guía de la Tercera Entrega, que exige justificar esta misma separación.
Desventaja: exige que el equipo entienda y respete el criterio de cuándo usar
cada mecanismo (ver Consecuencias negativas), y mantener dos superficies de
prueba distintas.

## Consecuencias positivas

- El CRUD elemental permanece simple y mantenible, sin SQL manual para
  operaciones triviales de una sola tabla.
- Se evita el acoplamiento innecesario a PL/pgSQL para lo que Spring Data
  ya resuelve de forma idiomática.
- Las consultas complejas (agregaciones) se ejecutan y optimizan dentro del
  motor, aprovechando su planificador e índices en vez de mover ese cómputo
  al backend.
- Todo el SQL no trivial queda versionado (`db/procs/`) junto al resto del
  esquema gestionado por Flyway, en vez de vivir disperso o construido en
  tiempo de ejecución.
- Trazabilidad completa: cada objeto SQL tiene una entrada verificable en
  `docs/basedatos/CATALOGO-SP.md` con sus parámetros, tablas afectadas y
  privilegios.
- Reducción del riesgo de inyección SQL: toda invocación usa parámetros
  nombrados o enlazados; se verificó (`grep` sobre
  `Backend/src/main/java/`) que no existe ninguna concatenación de cadenas
  en la construcción de una consulta JPQL, HQL o SQL nativo en todo el
  código actual.

## Consecuencias negativas y compromisos

- Coexisten dos mecanismos de acceso a datos (Spring Data derivado y
  funciones nativas), y cualquier persona que se incorpore al proyecto debe
  conocer el criterio de esta decisión para saber dónde implementar una
  nueva operación.
- Las operaciones encapsuladas en el motor requieren, para una verificación
  rigurosa, pruebas de integración contra PostgreSQL real (Testcontainers,
  `ResumenEspeciesIntegrationTest`), más lentas que un test unitario contra
  H2 y dependientes de que el entorno de ejecución tenga Docker disponible.
- El proyecto queda parcialmente dependiente de características específicas
  de PostgreSQL (PL/pgSQL); portar el sistema a otro motor relacional
  obligaría a reescribir estos objetos, no solo a cambiar un *driver*.
- El catálogo (`docs/basedatos/CATALOGO-SP.md`) y los archivos de
  migración/`db/procs/` deben mantenerse sincronizados manualmente cada vez
  que se agregue o modifique un objeto SQL — riesgo de la misma naturaleza
  que la triple fuente de esquema ya documentada en ADR-004 (`db/schema.sql`
  como copia manual de `V1__schema_inicial.sql`).

## Evidencia y trazabilidad

**Repositorios JPA (CRUD elemental):**
- `Backend/src/main/java/com/biopet/repository/UsuarioRepository.java`
- `Backend/src/main/java/com/biopet/repository/MascotaRepository.java`

**Función PostgreSQL versionada:**
- `db/procs/fn_resumen_mascotas_por_especie.sql`

**Invocación desde Spring Data (parámetro nombrado, sin concatenación):**
- `MascotaRepository.resumenPorEspecie(Long duenioId)`, anotada
  `@Query(value = "SELECT * FROM fn_resumen_mascotas_por_especie(:duenioId)",
  nativeQuery = true)`.

**Esquema y migraciones (fuente de verdad del DDL):**
- `Backend/src/main/resources/db/migration/V1__schema_inicial.sql` (Flyway).
- `Backend/src/main/resources/application.yml` (`ddl-auto: validate`,
  `flyway.locations: classpath:db/migration`).

**Catálogo de objetos SQL:**
- `docs/basedatos/CATALOGO-SP.md` (documenta `fn_resumen_mascotas_por_especie`
  y el trigger `set_actualizado_en`).

**Entidad con soft delete:**
- `Backend/src/main/java/com/biopet/entity/Mascota.java` (campo `activo`).

**Prueba de integración:**
- `Backend/src/test/java/com/biopet/repository/ResumenEspeciesIntegrationTest.java`
  (Testcontainers, PostgreSQL real).

No se incluyen capturas de pantalla en esta fase.

## Limitaciones

- Solo existe, a la fecha, un único objeto SQL no elemental en todo el
  sistema (`fn_resumen_mascotas_por_especie`); la estrategia híbrida no ha
  sido ejercitada todavía contra un procedimiento (`sp_`, con efectos de
  escritura) ni contra una operación transaccional multi-tabla — se
  documenta como punto a vigilar si el dominio crece en la Entrega Final.
- El trigger `set_actualizado_en` (también PL/pgSQL, definido en
  `db/schema.sql`) no cuenta con una prueba de integración dedicada
  exclusivamente a él, según el propio `docs/basedatos/CATALOGO-SP.md`; se
  verifica solo de forma indirecta a través de pruebas que comprueban
  `actualizado_en` tras un `UPDATE`.
- `db/schema.sql` sigue siendo, como ya señala ADR-004, una copia manual de
  `V1__schema_inicial.sql`; cualquier objeto SQL nuevo derivado de este ADR
  deberá replicarse en ambos lugares hasta que esa duplicación se resuelva.

## Referencias a otros documentos

- `ADR-004-postgresql.md` (estrategia de base de datos reproducible, cuenta
  de aplicación `biopet_app` y sus privilegios de `EXECUTE` sobre funciones).
- `docs/basedatos/CATALOGO-SP.md` (catálogo detallado de los objetos SQL).
