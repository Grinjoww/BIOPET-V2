-- db/procs/zz_grants_biopet_app.sql
-- Grants explicitos para el rol biopet_app sobre las rutinas de db/procs/.
-- Se ejecuta al final del directorio 03-procs (prefijo zz_) y tambien se
-- replica al final de la migracion V5__procedimientos_biopet.sql.
-- Todo es condicional (DO + IF EXISTS) para que sea inofensivo en entornos
-- donde biopet_app o los objetos no existan (ej. Testcontainers).
-- Motivo: los ALTER DEFAULT PRIVILEGES de db/roles.sql cubren funciones creadas
-- despues de roles.sql, pero no es garantia universal para PROCEDURE; se dejan
-- explicitos para que biopet_app conserve exactamente EXECUTE (ni mas ni menos).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'fn_resumen_mascotas_por_especie') THEN
            GRANT EXECUTE ON PROCEDURE fn_resumen_mascotas_por_especie(BIGINT) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'fn_historial_clinico_mascota') THEN
            GRANT EXECUTE ON PROCEDURE fn_historial_clinico_mascota(BIGINT) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'fn_reporte_dashboard') THEN
            GRANT EXECUTE ON PROCEDURE fn_reporte_dashboard(DATE, DATE) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'fn_siguiente_numero_ficha') THEN
            GRANT EXECUTE ON PROCEDURE fn_siguiente_numero_ficha(VARCHAR) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'sp_actualizar_estado_citas_masivas') THEN
            GRANT EXECUTE ON PROCEDURE sp_actualizar_estado_citas_masivas(BIGINT, VARCHAR, VARCHAR, TIMESTAMPTZ) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proname = 'sp_registrar_consulta_validada') THEN
            GRANT EXECUTE ON PROCEDURE sp_registrar_consulta_validada(BIGINT, BIGINT, VARCHAR, VARCHAR, VARCHAR, VARCHAR) TO biopet_app;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_class WHERE relname = 'seq_ficha_biopet' AND relkind = 'S') THEN
            GRANT USAGE, SELECT ON SEQUENCE seq_ficha_biopet TO biopet_app;
        END IF;

    END IF;
END
$$;