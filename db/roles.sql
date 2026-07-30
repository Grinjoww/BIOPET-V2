-- db/roles.sql
-- Crea la cuenta de aplicacion con privilegios minimos.
-- Se ejecuta ultimo (prefijo 02-), despues de que existan tablas (00-) y datos (01-).
-- Requiere: la variable de entorno DB_APP_PASSWORD debe existir si se desea un valor
-- distinto al de desarrollo puesto aqui abajo; para desarrollo local se deja fijo.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN
        CREATE ROLE biopet_app WITH LOGIN PASSWORD 'biopet_app_dev_pass';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE biopet_db TO biopet_app;
GRANT USAGE ON SCHEMA public TO biopet_app;

-- CRUD restringido solo a las tablas de dominio, sin CREATE/DROP.
GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios, mascotas TO biopet_app;

-- Necesario para que los BIGSERIAL (usuarios.id, mascotas.id) funcionen en INSERT.
GRANT USAGE, SELECT ON SEQUENCE usuarios_id_seq, mascotas_id_seq TO biopet_app;

-- Permite invocar la funcion/trigger existente y futuros procedimientos almacenados.
GRANT EXECUTE ON FUNCTION set_actualizado_en() TO biopet_app;

-- Para que futuras tablas/funciones/secuencias creadas por el rol propietario
-- (via nuevas migraciones Flyway) hereden automaticamente estos mismos privilegios.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO biopet_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO biopet_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO biopet_app;