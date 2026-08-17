-- db/procs/fn_historial_clinico_mascota.sql
-- Categoria: 1) multi-tabla (JOIN complejo).
-- Proposito: devolver el historial clinico consolidado de una mascota activa:
-- datos de la mascota, nombre de su duenio, conteo de consultas y citas,
-- ultima consulta, ultima vacuna aplicada y proxima vacuna pendiente.
-- Tablas que toca (solo lectura): mascotas, usuarios, consultas, vacunas, citas.
-- Parametros:
--   IN  p_mascota_id BIGINT - id de la mascota a consultar (obligatorio).
-- Salida (RETURNS TABLE):
--   mascota VARCHAR, especie VARCHAR, raza VARCHAR, duenio VARCHAR,
--   nConsultas BIGINT, ultimaConsulta TIMESTAMPTZ, ultimaVacuna VARCHAR,
--   proximaVacuna DATE, nCitas BIGINT.
-- Los nombres de columna en camelCase coinciden con los getters de la
-- proyeccion JPA HistorialClinico.
-- Sin SQL dinamico ni concatenacion.
-- Ejemplo de invocacion:
--   SELECT * FROM fn_historial_clinico_mascota(1);

CREATE OR REPLACE FUNCTION fn_historial_clinico_mascota(
    p_mascota_id BIGINT
)
RETURNS TABLE (
    mascota VARCHAR,
    especie VARCHAR,
    raza VARCHAR,
    duenio VARCHAR,
    nConsultas BIGINT,
    ultimaConsulta TIMESTAMPTZ,
    ultimaVacuna VARCHAR,
    proximaVacuna DATE,
    nCitas BIGINT
)
AS $$
BEGIN
    RETURN QUERY
    SELECT m.nombre AS mascota,
           m.especie,
           m.raza,
           u.nombre AS duenio,
           COALESCE(cstats.n_consultas, 0) AS nConsultas,
           cstats.ultima_consulta AS ultimaConsulta,
           vstats.ultima_vacuna AS ultimaVacuna,
           vstats.proxima_vacuna AS proximaVacuna,
           COALESCE(cistats.n_citas, 0) AS nCitas
    FROM mascotas m
    JOIN usuarios u ON u.id = m.duenio_id
    LEFT JOIN (
        SELECT c.mascota_id,
               COUNT(*)::BIGINT AS n_consultas,
               MAX(c.fecha_consulta) AS ultima_consulta
        FROM consultas c
        WHERE c.activo = TRUE
        GROUP BY c.mascota_id
    ) cstats ON cstats.mascota_id = m.id
    LEFT JOIN (
        SELECT v.mascota_id,
               (ARRAY_AGG(v.tipo ORDER BY v.fecha_aplicacion DESC))[1] AS ultima_vacuna,
               MIN(v.proxima_fecha) AS proxima_vacuna
        FROM vacunas v
        WHERE v.activo = TRUE
        GROUP BY v.mascota_id
    ) vstats ON vstats.mascota_id = m.id
    LEFT JOIN (
        SELECT ci.mascota_id,
               COUNT(*)::BIGINT AS n_citas
        FROM citas ci
        WHERE ci.activo = TRUE
        GROUP BY ci.mascota_id
    ) cistats ON cistats.mascota_id = m.id
    WHERE m.id = p_mascota_id
      AND m.activo = TRUE;
END;
$$ LANGUAGE plpgsql;