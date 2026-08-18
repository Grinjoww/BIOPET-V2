-- db/procs/fn_resumen_mascotas_por_especie.sql
-- Resumen de mascotas activas agrupadas por especie.
-- Si p_duenio_id es NULL, devuelve el resumen global (uso tipico: rol ADMIN).
-- Si viene con valor, filtra solo las mascotas de ese dueno.
-- Sin SQL dinamico, sin concatenacion, parametro con nombre.
--
-- Reclasificada de FUNCTION a PROCEDURE con OUT refcursor (F02, cierre de
-- acceso JPA formal): Spring Data @Procedure invoca siempre con la
-- sentencia CALL (verificado en F03: sobre una FUNCTION, PostgreSQL la
-- rechaza con "... is not a procedure. Hint: To call a function, use
-- SELECT", incluso con un parametro OUT de tipo refcursor). CALL solo es
-- valido sobre PROCEDURE. Se mantiene el nombre `fn_...` (por
-- compatibilidad con el resto del repositorio y la documentacion) pero el
-- objeto pasa a ser PROCEDURE; devuelve el resultado via OUT refcursor
-- porque PROCEDURE no admite RETURNS TABLE.
-- p_duenio_id ya no tiene DEFAULT: PostgreSQL prohibe un parametro OUT
-- despues de uno con DEFAULT en un PROCEDURE (mismo motivo por el que
-- sp_registrar_consulta_validada tampoco usa DEFAULT en sus VARCHAR
-- opcionales). El llamador debe pasar NULL de forma explicita para pedir
-- el resumen global (asi lo hacen ya ProcedimientoBiopetRepository y los
-- tests de integracion).

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
