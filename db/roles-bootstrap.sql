-- db/roles-bootstrap.sql
-- Bootstrap MINIMO del rol de aplicacion, sin ninguna dependencia de
-- objetos de esquema (tablas/secuencias/funciones/procedimientos): todos
-- esos objetos son gestionados exclusivamente por Flyway (V1-V6) y sus
-- privilegios se otorgan despues, en el callback
-- Backend/src/main/resources/db/migration/afterMigrate.sql.
--
-- Se monta en /docker-entrypoint-initdb.d/ y corre una sola vez, cuando
-- el volumen de PostgreSQL esta vacio, ANTES de que exista ninguna tabla
-- y ANTES de que Spring Boot/Flyway arranquen.
--
-- Motivo de por que esto SI debe ser bootstrap (a diferencia del resto de
-- db/roles.sql, ver ese archivo): Backend/src/main/resources/application.yml
-- configura spring.datasource.username=${DB_APP_USER:biopet_app} para el
-- pool principal (Hikari/JPA) del backend -- no el superusuario. Ese rol
-- debe existir, con permiso de conexion, antes de que el contenedor del
-- backend intente arrancar, o el arranque falla por autenticacion, sin
-- relacion alguna con el estado de Flyway.
--
-- Deliberadamente NO otorga privilegios sobre tablas/secuencias/
-- funciones/procedimientos ni declara ALTER DEFAULT PRIVILEGES aqui: esos
-- dependen de objetos que Flyway todavia no ha creado en un volumen nuevo.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN
        CREATE ROLE biopet_app WITH LOGIN PASSWORD 'biopet_app_dev_pass';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE biopet_db TO biopet_app;
GRANT USAGE ON SCHEMA public TO biopet_app;
