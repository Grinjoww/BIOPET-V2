-- db/seed.sql
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