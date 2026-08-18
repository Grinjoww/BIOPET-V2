-- Backend/src/main/resources/db/migration/afterMigrate.sql
-- Callback de Flyway (nombre reservado por convencion: se ejecuta
-- automaticamente al final de cada "migrate" exitoso, con la misma
-- conexion configurada en spring.flyway.* -- en este proyecto,
-- spring.flyway.user resuelve por defecto a "biopet_user" (variable de
-- entorno DB_USER, ver Backend/src/main/resources/application.yml), el
-- mismo rol propietario que crea todos los objetos de V1-V6). NO es una
-- migracion versionada:
-- no aparece en flyway_schema_history y se reejecuta en cada arranque de
-- Spring Boot / "mvn clean verify"; por eso cada sentencia de aqui es
-- repetible sin efectos adversos (GRANT es idempotente en PostgreSQL).
--
-- Proposito (F02, cierre del fix de doble inicializacion del esquema):
-- otorgar a biopet_app los privilegios sobre los objetos que solo existen
-- DESPUES de que Flyway corrio -- V1-V4 (tablas de dominio + su funcion
-- trigger) y V5-V6 (rutinas PostgreSQL). db/roles-bootstrap.sql (montado
-- en /docker-entrypoint-initdb.d/, ejecutado ANTES de Flyway sobre un
-- volumen vacio) ya crea el rol biopet_app y le da CONNECT + USAGE ON
-- SCHEMA -- nada mas -- precisamente porque estos GRANT no pueden
-- ejecutarse ahi sin que las tablas ya existan.
--
-- Guarda: no-op seguro si biopet_app no existe (p.ej. Render, donde el
-- backend usa el rol de la BD gestionada -- ver docs/despliegue/DEPLOYMENT.md,
-- seccion "Rol de aplicacion"). Mismo patron ya usado en
-- V5__procedimientos_biopet.sql, V6__formalizar_procedimientos_jpa.sql y
-- db/procs/zz_grants_biopet_app.sql.
--
-- Deliberadamente NO se usa "GRANT ... ON ALL TABLES IN SCHEMA public":
-- eso incluiria flyway_schema_history (tabla administrativa de Flyway) y
-- daria a biopet_app permiso de escritura sobre su propio historial de
-- migraciones. Los grants de abajo son explicitos, objeto por objeto.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        -- CRUD sobre las tablas de dominio (V1-V4). Sin CREATE/DROP.
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON usuarios, mascotas, citas, consultas, vacunas
            TO biopet_app;

        -- Secuencias de las claves BIGSERIAL de esas mismas tablas.
        GRANT USAGE, SELECT
            ON SEQUENCE usuarios_id_seq, mascotas_id_seq, citas_id_seq,
                        consultas_id_seq, vacunas_id_seq
            TO biopet_app;

        -- Funcion trigger de auditoria de fecha (V1).
        GRANT EXECUTE ON FUNCTION set_actualizado_en() TO biopet_app;

        -- Las 6 rutinas finales (V5/V6) requeridas por el acceso JPA
        -- formal. Ya quedan grantadas tambien dentro de V5/V6 con la
        -- misma guarda condicional (no se modifico V5 ni V6); repetirlo
        -- aqui es intencional -- este callback es la fuente unica y
        -- explicita de "que privilegios tiene biopet_app hoy" -- y no
        -- tiene efecto adverso, porque GRANT es idempotente.
        GRANT EXECUTE ON PROCEDURE fn_resumen_mascotas_por_especie(BIGINT) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_historial_clinico_mascota(BIGINT) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_reporte_dashboard(DATE, DATE) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE fn_siguiente_numero_ficha(VARCHAR) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE sp_actualizar_estado_citas_masivas(BIGINT, VARCHAR, VARCHAR, TIMESTAMPTZ) TO biopet_app;
        GRANT EXECUTE ON PROCEDURE sp_registrar_consulta_validada(BIGINT, BIGINT, VARCHAR, VARCHAR, VARCHAR, VARCHAR) TO biopet_app;

        -- Privilegios por defecto sobre objetos FUTUROS creados por el
        -- rol que ejecuta este callback (spring.flyway.user -- por
        -- defecto biopet_user via la variable de entorno DB_USER, pero
        -- NO se hardcodea ese nombre: sin clausula "FOR ROLE",
        -- ALTER DEFAULT PRIVILEGES aplica implicitamente al rol que
        -- ejecuta la sentencia, sea cual sea su nombre real en cada
        -- entorno -- p.ej. "test_user" en los tests de integracion con
        -- Testcontainers, ver BiopetAppRolMinimoPrivilegiosIntegrationTest
        -- y ProcedimientosBiopetIntegrationTest, que usan un rol distinto
        -- de biopet_user y fallarian con "role biopet_user does not
        -- exist" si se fijara el nombre literal). Para que nuevas
        -- migraciones (V7+) no requieran editar este callback a mano.
        -- Reemplaza la intencion original de las mismas tres sentencias
        -- en la version historica de db/roles.sql (que tampoco fijaba el
        -- rol explicitamente, por el mismo motivo).
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO biopet_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO biopet_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            GRANT EXECUTE ON FUNCTIONS TO biopet_app;

    END IF;
END
$$;
