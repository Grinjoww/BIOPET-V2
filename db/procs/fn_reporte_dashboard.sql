-- db/procs/fn_reporte_dashboard.sql
-- Categoria: 3) reporte (vista consolidada para dashboard/reporte).
-- Proposito: devolver en una sola fila los indicadores principales del
-- dashboard de BIOPET para un rango de fechas: mascotas activas, citas
-- programadas, consultas y vacunas en el rango, y mascotas sin ninguna
-- consulta registrada (candidatas a seguimiento).
-- Tablas que toca (solo lectura): mascotas, citas, consultas, vacunas.
-- Parametros:
--   IN  p_desde DATE - inicio del rango (inclusive).
--   IN  p_hasta DATE - fin del rango (inclusive).
-- Salida (RETURNS TABLE), una sola fila:
--   mascotasActivas BIGINT, citasProgramadas BIGINT,
--   consultasEnRango BIGINT, vacunasEnRango BIGINT,
--   mascotasSinConsulta BIGINT.
-- Nota: consultas.fecha_consulta es TIMESTAMPTZ, por eso el rango usa
-- [p_desde, p_hasta + 1 dia) para incluir todo el dia final.
-- Los nombres de columna en camelCase coinciden con los getters de la
-- proyeccion JPA ReporteDashboard.
-- Sin SQL dinamico ni concatenacion.
-- Ejemplo de invocacion:
--   SELECT * FROM fn_reporte_dashboard('2026-08-01', '2026-08-31');

CREATE OR REPLACE FUNCTION fn_reporte_dashboard(
    p_desde DATE,
    p_hasta DATE
)
RETURNS TABLE (
    mascotasActivas BIGINT,
    citasProgramadas BIGINT,
    consultasEnRango BIGINT,
    vacunasEnRango BIGINT,
    mascotasSinConsulta BIGINT
)
AS $$
BEGIN
    RETURN QUERY
    SELECT
        (SELECT COUNT(*)::BIGINT FROM mascotas m WHERE m.activo = TRUE) AS mascotasActivas,
        (SELECT COUNT(*)::BIGINT FROM citas ci
         WHERE ci.activo = TRUE AND ci.estado = 'PROGRAMADA') AS citasProgramadas,
        (SELECT COUNT(*)::BIGINT FROM consultas c
         WHERE c.activo = TRUE
           AND c.fecha_consulta >= p_desde
           AND c.fecha_consulta < (p_hasta + INTERVAL '1 day')) AS consultasEnRango,
        (SELECT COUNT(*)::BIGINT FROM vacunas v
         WHERE v.activo = TRUE
           AND v.fecha_aplicacion >= p_desde
           AND v.fecha_aplicacion <= p_hasta) AS vacunasEnRango,
        (SELECT COUNT(*)::BIGINT FROM mascotas m
         WHERE m.activo = TRUE
           AND NOT EXISTS (
               SELECT 1 FROM consultas c
               WHERE c.mascota_id = m.id AND c.activo = TRUE
           )) AS mascotasSinConsulta;
END;
$$ LANGUAGE plpgsql;