-- db/procs/sp_registrar_consulta_validada.sql
-- Categoria: 5) validacion cruzada (chequeo de integridad entre tablas
-- antes de una operacion).
-- Proposito: registrar una consulta medica solo si la mascota existe y esta
-- activa, y el veterinario existe, esta activo y tiene rol VETERINARIO o
-- ADMIN. Si alguna validacion falla, la operacion aborta con RAISE EXCEPTION
-- y no se inserta nada. Devuelve el id de la consulta creada.
-- Tablas que toca (lectura): mascotas, usuarios.
-- Tablas que toca (escritura): consultas.
-- Parametros:
--   IN  p_mascota_id BIGINT         - mascota atendida.
--   IN  p_veterinario_id BIGINT     - profesional que registra.
--   IN  p_motivo VARCHAR(200)       - motivo de la consulta (obligatorio).
--   IN  p_diagnostico VARCHAR(500)  - diagnostico (opcional, NULL si no aplica).
--   IN  p_tratamiento VARCHAR(500)  - tratamiento (opcional, NULL si no aplica).
--   IN  p_observaciones VARCHAR(500)- observaciones (opcional, NULL si no aplica).
--   OUT p_consulta_id BIGINT        - id de la consulta insertada.
-- Nota: los IN opcionales NO llevan DEFAULT para respetar la restriccion de
-- PostgreSQL de que los OUT no pueden ir despues de un parametro con default.
-- Sin SQL dinamico ni concatenacion.
-- Ejemplo de invocacion (psql):
--   CALL sp_registrar_consulta_validada(1, 2, 'Chequeo anual', NULL, NULL, NULL, NULL);

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