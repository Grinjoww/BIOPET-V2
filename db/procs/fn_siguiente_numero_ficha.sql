-- db/procs/fn_siguiente_numero_ficha.sql
-- Categoria: 6) codigo secuencial (uso de SEQUENCE).
-- Proposito: generar el siguiente numero de ficha secuencial de la clinica,
-- con el formato '<PREFIJO>-NNNNNN' (ej. FICHA-000042), consumiendo un valor
-- de la secuencia dedicada seq_ficha_biopet. Los numeros nunca se repiten
-- aunque la rutina se invoque en paralelo.
-- Tablas que toca: ninguna (solo la secuencia seq_ficha_biopet).
-- Parametros:
--   IN  p_prefijo VARCHAR - prefijo opcional del codigo (default 'FICHA');
--                           se normaliza a mayusculas y sin espacios.
--   OUT p_codigo VARCHAR  - codigo secuencial generado (escalar directo,
--                           sin refcursor: es un unico valor, no una tabla).
-- Ejemplo de invocacion:
--   CALL fn_siguiente_numero_ficha('HIST', NULL);   -- HIST-000002
--
-- Reclasificada de FUNCTION a PROCEDURE (F02, cierre de acceso JPA formal):
-- Spring Data @Procedure invoca siempre con la sentencia CALL, valida solo
-- sobre PROCEDURE (ver la nota completa en
-- fn_resumen_mascotas_por_especie.sql). A diferencia de las otras 3
-- rutinas fn_*, esta no necesita OUT refcursor porque su resultado ya era
-- un escalar (un unico VARCHAR), no una tabla: un PROCEDURE con OUT
-- escalar directo es exactamente el mismo patron que ya usan sp_* y
-- funciona con @Procedure sin ningun ajuste adicional.
-- p_prefijo ya no tiene DEFAULT: PostgreSQL prohibe un parametro OUT
-- despues de uno con DEFAULT en un PROCEDURE. El llamador debe pasar el
-- prefijo de forma explicita (ProcedimientoBiopetRepository ya lo hace).
-- La normalizacion a 'FICHA' cuando el valor es NULL o vacio se mantiene
-- intacta dentro del cuerpo de la rutina.

CREATE SEQUENCE IF NOT EXISTS seq_ficha_biopet
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

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
