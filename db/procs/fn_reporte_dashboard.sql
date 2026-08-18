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
-- Salida: OUT p_cursor refcursor, una sola fila, con columnas
--   mascotasActivas BIGINT, citasProgramadas BIGINT,
--   consultasEnRango BIGINT, vacunasEnRango BIGINT,
--   mascotasSinConsulta BIGINT.
-- Nota: consultas.fecha_consulta es TIMESTAMPTZ, por eso el rango usa
-- [p_desde, p_hasta + 1 dia) para incluir todo el dia final.
-- Los nombres de columna en camelCase coinciden con los getters de la
-- proyeccion JPA ReporteDashboard.
-- Sin SQL dinamico ni concatenacion.
-- Ejemplo de invocacion:
--   BEGIN;
--   CALL fn_reporte_dashboard('2026-08-01', '2026-08-31', NULL);
--   COMMIT;
--
-- Reclasificada de FUNCTION a PROCEDURE con OUT refcursor (F02, cierre de
-- acceso JPA formal): ver la nota completa en
-- fn_resumen_mascotas_por_especie.sql.

CREATE OR REPLACE PROCEDURE fn_reporte_dashboard(
    IN p_desde DATE,
    IN p_hasta DATE,
    OUT p_cursor refcursor
)
LANGUAGE plpgsql
AS $$
BEGIN
    OPEN p_cursor FOR
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
$$;
