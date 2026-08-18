-- db/procs/sp_actualizar_estado_citas_masivas.sql
-- Categoria: 4) actualizacion masiva (UPDATE/DELETE controlado).
-- Proposito: cambiar en un solo UPDATE el estado de todas las citas activas
-- de un veterinario que esten en un estado anterior dado y cuya fecha_hora
-- sea anterior o igual a un limite, devolviendo cuantas filas se afectaron.
-- Tablas que toca (escritura): citas.
-- Parametros:
--   IN  p_veterinario_id BIGINT     - veterinario propietario de las citas.
--   IN  p_estado_anterior VARCHAR   - estado que deben tener las citas a actualizar.
--   IN  p_estado_nuevo VARCHAR      - estado que se aplica (validado contra el CHECK).
--   IN  p_fecha_limite TIMESTAMPTZ  - tope de fecha_hora (inclusive).
--   OUT p_afectadas BIGINT          - numero de filas modificadas.
-- Sin SQL dinamico ni concatenacion.
-- Ejemplo de invocacion (psql):
--   CALL sp_actualizar_estado_citas_masivas(
--        2, 'PROGRAMADA', 'COMPLETADA', '2026-08-16 23:59:59', NULL);

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