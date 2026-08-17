-- V5__procedimientos_biopet.sql
-- Migracion Flyway con las 6 rutinas de db/procs/ (F01) + grants condicionales
-- para biopet_app (F05). Idempotente: CREATE OR REPLACE / IF NOT EXISTS.
-- No modifica V1-V4; se aplica despues de que existan tablas y roles.
-- Fuente de verdad: db/procs/*.sql (docker-compose monta el directorio completo).

CREATE SEQUENCE IF NOT EXISTS seq_ficha_biopet
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

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

CREATE OR REPLACE FUNCTION fn_siguiente_numero_ficha(
    p_prefijo VARCHAR DEFAULT 'FICHA',
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

CREATE OR REPLACE PROCEDURE sp_actualizar_estado_citas_masivas(
    p_veterinario_id BIGINT,
    p_estado_anterior VARCHAR(20),
    p_estado_nuevo VARCHAR(20),
    p_fecha_limite TIMESTAMPTZ,
    OUT p_afectadas BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_estado_anterior NOT IN ('PROGRAMADA','CANCELADA','COMPLETADA') THEN
        RAISE EXCEPTION 'Estado anterior invalido: % (permitidos: PROGRAMADA, CANCELADA, COMPLETADA)', p_estado_anterior;
    END IF;
    IF p_estado_nuevo NOT IN ('PROGRAMADA','CANCELADA','COMPLETADA') THEN
        RAISE EXCEPTION 'Estado nuevo invalido: % (permitidos: PROGRAMADA, CANCELADA, COMPLETADA)', p_estado_nuevo;
    END IF;
    IF p_fecha_limite IS NULL THEN
        RAISE EXCEPTION 'p_fecha_limite no puede ser NULL';
    END IF;

    WITH actualizadas AS (
        UPDATE citas
        SET estado = p_estado_nuevo
        WHERE veterinario_id = p_veterinario_id
          AND estado = p_estado_anterior
          AND fecha_hora <= p_fecha_limite
          AND activo = TRUE
        RETURNING id
    )
    SELECT COUNT(*) INTO p_afectadas
    FROM actualizadas;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_registrar_consulta_validada(
    p_mascota_id BIGINT,
    p_veterinario_id BIGINT,
    p_motivo VARCHAR(200),
    p_diagnostico VARCHAR(500),
    p_tratamiento VARCHAR(500),
    p_observaciones VARCHAR(500),
    OUT p_consulta_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_mascota_activa BOOLEAN;
    v_rol VARCHAR(30);
BEGIN
    IF p_motivo IS NULL OR TRIM(p_motivo) = '' THEN
        RAISE EXCEPTION 'El motivo de la consulta no puede estar vacio';
    END IF;

    SELECT m.activo INTO v_mascota_activa
    FROM mascotas m
    WHERE m.id = p_mascota_id;

    IF v_mascota_activa IS NULL THEN
        RAISE EXCEPTION 'La mascota % no existe', p_mascota_id;
    END IF;
    IF NOT v_mascota_activa THEN
        RAISE EXCEPTION 'La mascota % esta inactiva y no puede recibir consultas', p_mascota_id;
    END IF;

    SELECT u.rol INTO v_rol
    FROM usuarios u
    WHERE u.id = p_veterinario_id AND u.activo = TRUE;

    IF v_rol IS NULL THEN
        RAISE EXCEPTION 'El veterinario % no existe o esta inactivo', p_veterinario_id;
    END IF;
    IF v_rol NOT IN ('ROLE_VETERINARIO', 'ROLE_ADMIN') THEN
        RAISE EXCEPTION 'El usuario % (rol %) no esta autorizado para registrar consultas', p_veterinario_id, v_rol;
    END IF;

    INSERT INTO consultas (
        mascota_id, veterinario_id, fecha_consulta, motivo,
        diagnostico, tratamiento, observaciones
    )
    VALUES (
        p_mascota_id, p_veterinario_id, NOW(), p_motivo,
        p_diagnostico, p_tratamiento, p_observaciones
    )
    RETURNING id INTO p_consulta_id;
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT EXECUTE ON FUNCTION fn_resumen_mascotas_por_especie(BIGINT) TO biopet_app;
        GRANT EXECUTE ON FUNCTION fn_historial_clinico_mascota(BIGINT) TO biopet_app;
        GRANT EXECUTE ON FUNCTION fn_reporte_dashboard(DATE, DATE) TO biopet_app;
        GRANT EXECUTE ON FUNCTION fn_siguiente_numero_ficha(VARCHAR) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE sp_actualizar_estado_citas_masivas(BIGINT, VARCHAR, VARCHAR, TIMESTAMPTZ) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE sp_registrar_consulta_validada(BIGINT, BIGINT, VARCHAR, VARCHAR, VARCHAR, VARCHAR) TO biopet_app;
        GRANT USAGE, SELECT ON SEQUENCE seq_ficha_biopet TO biopet_app;

    END IF;
END
$$;
