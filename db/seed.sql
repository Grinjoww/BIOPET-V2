-- db/seed.sql
-- HISTORICO / NO se monta automaticamente en docker-entrypoint-initdb.d
-- (ver docker-compose.yml). Redundante con
-- Backend/src/main/java/com/biopet/config/DataInitializer.java
-- (CommandLineRunner "seedAdmin"), que crea el mismo admin de desarrollo
-- de forma idempotente DESPUES de que Flyway corre, usando la propia app
-- (UsuarioRepository + PasswordEncoder), sin depender de que este archivo
-- se ejecute antes de que exista la tabla "usuarios".
--
-- Datos semilla deterministas. Requiere que db/schema.sql ya se haya ejecutado.
-- Usuario admin de desarrollo: admin@biopet.ec / Admin123*

INSERT INTO usuarios (nombre, email, password_hash, rol, activo)
SELECT 'Administrador BIOPET',
       'admin@biopet.ec',
       '$2b$12$D5THApMOkV5H4fR8W8B6HOOBmodkTnnBUjLCmB6XzpR8zYDsNDYpG',
       'ROLE_ADMIN',
       TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE email = 'admin@biopet.ec'
);