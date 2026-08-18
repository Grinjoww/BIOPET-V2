-- V6__formalizar_procedimientos_jpa.sql
-- F02 (cierre de acceso JPA formal): reclasifica las 4 rutinas fn_* de
-- FUNCTION (creadas en V5) a PROCEDURE, conservando su nombre fn_... por
-- compatibilidad con el resto del repositorio (codigo, tests, SRS,
-- trazabilidad). No reescribe V5 (ya publicada/aplicada; reescribirla
-- produciria checksum mismatch en bases donde ya corrio): esta es una
-- migracion nueva, aditiva, que se aplica DESPUES de V5.
--
-- Motivo tecnico (verificacion real F03): Spring Data JPA / Hibernate
-- generan siempre una sentencia CALL para @Procedure, y PostgreSQL solo
-- acepta CALL sobre objetos PROCEDURE, nunca sobre FUNCTION (con o sin
-- parametros OUT, con o sin refcursor). Antes de este cierre, las 4
-- rutinas fn_* eran FUNCTION (RETURNS TABLE / OUT escalar directo)
-- invocadas con @Query(nativeQuery = true), no con un mecanismo JPA
-- formal. Fuente de verdad final: db/procs/*.sql (identica a esta
-- migracion salvo comentarios).

-- 1) Eliminar las 4 FUNCTION creadas en V5. Los parametros OUT no forman
--    parte de la firma de identidad de una rutina en PostgreSQL, asi que
--    estas firmas coinciden exactamente con las declaradas en V5.
DROP FUNCTION IF EXISTS fn_resumen_mascotas_por_especie(BIGINT);
DROP FUNCTION IF EXISTS fn_historial_clinico_mascota(BIGINT);
DROP FUNCTION IF EXISTS fn_reporte_dashboard(DATE, DATE);
DROP FUNCTION IF EXISTS fn_siguiente_numero_ficha(VARCHAR);

-- 2) Crear las 4 PROCEDURE formales. Las 3 que devuelven un conjunto de
--    filas usan OUT refcursor (un PROCEDURE no admite RETURNS TABLE); se
--    invocan con @Procedure + @NamedStoredProcedureQuery (REF_CURSOR)
--    desde Backend/src/main/java/com/biopet/entity/Mascota.java. El
--    cursor solo es legible dentro de la transaccion en la que se abre.
--    fn_siguiente_numero_ficha mantiene OUT escalar directo (igual patron
--    que sp_*, sin refcursor). p_duenio_id y p_prefijo pierden su DEFAULT:
--    PostgreSQL prohibe un parametro OUT despues de uno con DEFAULT
--    (misma restriccion que ya aplicaba a sp_registrar_consulta_validada
--    en V5); el llamador debe pasar el valor de forma explicita (asi lo
--    hace ya ProcedimientoBiopetRepository).

CREATE OR REPLACE PROCEDURE fn_resumen_mascotas_por_especie(
    IN p_duenio_id BIGINT,
    OUT p_cursor refcursor
)
LANGUAGE plpgsql
AS $$
BEGIN
    OPEN p_cursor FOR
        SELECT m.especie, COUNT(*)::BIGINT AS total
        FROM mascotas m
        WHERE m.activo = TRUE
          AND (p_duenio_id IS NULL OR m.duenio_id = p_duenio_id)
        GROUP BY m.especie
        ORDER BY total DESC;
END;
$$;

CREATE OR REPLACE PROCEDURE fn_historial_clinico_mascota(
    IN p_mascota_id BIGINT,
    OUT p_cursor refcursor
)
LANGUAGE plpgsql
AS $$
BEGIN
    OPEN p_cursor FOR
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
$$;

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

CREATE OR REPLACE PROCEDURE fn_siguiente_numero_ficha(
    IN p_prefijo VARCHAR,
    OUT p_codigo VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_prefijo VARCHAR;
    v_siguiente BIGINT;
BEGIN
    v_prefijo := UPPER(TRIM(p_prefijo));
    IF v_prefijo IS NULL OR v_prefijo = '' THEN
        v_prefijo := 'FICHA';
    END IF;

    SELECT nextval('seq_ficha_biopet') INTO v_siguiente;

    p_codigo := v_prefijo || '-' || LPAD(v_siguiente::TEXT, 6, '0');
END;
$$;

-- 3) Grants: DROP FUNCTION revoca implicitamente los grants sobre los
--    objetos eliminados. Se otorgan de nuevo, ahora sobre PROCEDURE, con
--    los mismos privilegios minimos que V5 ya establecia para biopet_app.
--    Condicional (no-op si el rol no existe), igual que V5 y
--    db/procs/zz_grants_biopet_app.sql.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT EXECUTE ON PROCEDURE fn_resumen_mascotas_por_especie(BIGINT) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_historial_clinico_mascota(BIGINT) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_reporte_dashboard(DATE, DATE) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_siguiente_numero_ficha(VARCHAR) TO biopet_app;

    END IF;
END
$$;
