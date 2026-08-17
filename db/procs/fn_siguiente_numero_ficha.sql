-- db/procs/fn_siguiente_numero_ficha.sql
-- Categoria: 6) codigo secuencial (uso de SEQUENCE).
-- Proposito: generar el siguiente numero de ficha secuencial de la clinica,
-- con el formato '<PREFIJO>-NNNNNN' (ej. FICHA-000042), consumiendo un valor
-- de la secuencia dedicada seq_ficha_biopet. Los numeros nunca se repiten
-- aunque la funcion se invoque en paralelo.
-- Tablas que toca: ninguna (solo la secuencia seq_ficha_biopet).
-- Parametros:
--   IN  p_prefijo VARCHAR - prefijo opcional del codigo (default 'FICHA');
--                           se normaliza a mayusculas y sin espacios.
--   OUT p_codigo VARCHAR  - codigo secuencial generado.
-- El OUT permite la invocacion formal desde JPA (@Procedure con
-- outputParameterName = 'p_codigo') via JDBC CallableStatement.
-- Sin SQL dinamico ni concatenacion en queries.
-- Ejemplo de invocacion:
--   SELECT * FROM fn_siguiente_numero_ficha();           -- FICHA-000001
--   SELECT * FROM fn_siguiente_numero_ficha('HIST');     -- HIST-000002

CREATE SEQUENCE IF NOT EXISTS seq_ficha_biopet
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

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