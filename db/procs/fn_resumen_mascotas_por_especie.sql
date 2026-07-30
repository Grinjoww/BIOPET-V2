-- db/procs/fn_resumen_mascotas_por_especie.sql
-- Resumen de mascotas activas agrupadas por especie.
-- Si p_duenio_id es NULL, devuelve el resumen global (uso tipico: rol ADMIN).
-- Si viene con valor, filtra solo las mascotas de ese dueno.
-- Sin SQL dinamico, sin concatenacion, parametro con nombre.

CREATE OR REPLACE FUNCTION fn_resumen_mascotas_por_especie(
    p_duenio_id BIGINT DEFAULT NULL
)
RETURNS TABLE (
    especie VARCHAR,
    total BIGINT
)
AS $$
BEGIN
    RETURN QUERY
    SELECT m.especie, COUNT(*)::BIGINT AS total
    FROM mascotas m
    WHERE m.activo = TRUE
      AND (p_duenio_id IS NULL OR m.duenio_id = p_duenio_id)
    GROUP BY m.especie
    ORDER BY total DESC;
END;
$$ LANGUAGE plpgsql;