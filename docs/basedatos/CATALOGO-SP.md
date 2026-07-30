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

## Verificación automatizada

Cubierta con test de integración usando Testcontainers (PostgreSQL real desechable):
`Backend/src/test/java/com/biopet/repository/ResumenEspeciesIntegrationTest.java`.

Verificado también de forma manual antes del test automatizado:
```bash
docker compose exec -T postgres psql -U biopet_app -d biopet_db \
  -c "SELECT * FROM fn_resumen_mascotas_por_especie();"
```
Resultado: `Perro | 1` (29/07/2026).