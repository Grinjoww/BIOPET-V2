# Catálogo de procedimientos y funciones almacenadas — BIOPET

## fn_resumen_mascotas_por_especie

- **Tipo:** Función PL/pgSQL (no procedimiento), para permitir invocación directa vía
  `@Query(nativeQuery = true)` desde Spring Data sin necesidad de `CallableStatement`.
- **Archivo:** `db/procs/fn_resumen_mascotas_por_especie.sql`
- **Propósito:** Devolver un resumen de mascotas activas agrupadas por especie, con
  el total de mascotas por cada una, opcionalmente filtrado por dueño.
- **Parámetros de entrada:**
  | Parámetro | Tipo | Obligatorio | Descripción |
  |---|---|---|---|
  | `p_duenio_id` | `BIGINT` | No (default `NULL`) | Si se omite, agrega todas las mascotas activas. Si se especifica, filtra solo las del dueño indicado. |
- **Columnas de salida:**
  | Columna | Tipo | Descripción |
  |---|---|---|
  | `especie` | `VARCHAR` | Especie de la mascota. |
  | `total` | `BIGINT` | Cantidad de mascotas activas de esa especie. |
- **Tablas afectadas (solo lectura):** `mascotas` (filtro `activo = true`, agrupado por `especie`).
- **Endpoint que la expone:** `GET /api/mascotas/resumen-especies?duenioId={opcional}`
- **Control de acceso:** un usuario con rol distinto de `ADMIN` no puede ver el resumen
  de otro dueño; el `duenioId` recibido se ignora y se fuerza al propio del usuario
  autenticado. Un `ADMIN` puede omitir el parámetro (resumen global) o pasar cualquier `duenioId`.
- **Privilegios:** `biopet_app` tiene `EXECUTE` sobre esta función automáticamente vía el
  `ALTER DEFAULT PRIVILEGES` declarado en `db/roles.sql` — verificado el 29/07/2026 conectando
  directo como `biopet_app` y ejecutando la función sin necesidad de GRANT adicional.

## set_actualizado_en

- **Tipo:** Función PL/pgSQL de tipo *trigger* (`RETURNS TRIGGER`), no invocable
  directamente desde una sentencia `SELECT`.
- **Archivo:** `db/schema.sql` (líneas de definición de la función y de los dos
  triggers que la invocan), replicada en `Backend/src/main/resources/db/migration/V1__schema_inicial.sql`.
- **Propósito:** Mantener `actualizado_en` sincronizado automáticamente en cada
  `UPDATE`, sin depender de que el backend (Hibernate/JPA) recuerde setear ese
  campo manualmente en cada operación de escritura.
- **Parámetros de entrada:** ninguno (las funciones trigger reciben el registro
  vía las variables implícitas `NEW`/`OLD`, no por lista de parámetros).
- **Comportamiento:** asigna `NEW.actualizado_en = NOW()` y retorna `NEW`.
- **Tablas afectadas:** `usuarios` y `mascotas`, cada una con su propio trigger
  `BEFORE UPDATE` (`trg_usuarios_actualizado_en`, `trg_mascotas_actualizado_en`)
  que ejecuta esta misma función.
- **Endpoint que la expone:** ninguno directamente — se dispara de forma
  automática en cualquier `UPDATE` sobre esas dos tablas (por ejemplo, al usar
  `PUT /api/mascotas/{id}`), sin que el código Java la invoque explícitamente.
- **Privilegios:** `biopet_app` tiene `EXECUTE` mediante un `GRANT` explícito en
  `db/roles.sql` (no vía `ALTER DEFAULT PRIVILEGES`, porque esta función se crea
  en `db/schema.sql`, que se monta *antes* que `db/roles.sql` — el default
  privilege solo cubre objetos creados *después* de declararlo).

## Verificación automatizada

Cubierta con test de integración usando Testcontainers (PostgreSQL real desechable):
`Backend/src/test/java/com/biopet/repository/ResumenEspeciesIntegrationTest.java`.

Verificado también de forma manual antes del test automatizado:
```bash
docker compose exec -T postgres psql -U biopet_app -d biopet_db \
  -c "SELECT * FROM fn_resumen_mascotas_por_especie();"
```
Resultado: `Perro | 1` (29/07/2026).

El trigger `set_actualizado_en` se verifica de forma implícita en cualquier test
que haga un `UPDATE` y compruebe `actualizado_en` (por ejemplo, los tests de
`MascotaService.actualizar`). No cuenta con un test de integración dedicado
exclusivamente al trigger en sí — si la guía exige verificación explícita por
procedimiento, este es un punto pendiente a considerar.