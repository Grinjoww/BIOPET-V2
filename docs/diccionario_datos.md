# Diccionario de datos — BIOPET Entrega 1B

## Tabla: usuarios

| Columna | Tipo PostgreSQL 16 | Nulo | Default | Descripción |
|---|---:|---|---|---|
| id | BIGSERIAL | No | Secuencia | Identificador único del usuario. |
| nombre | VARCHAR(100) | No | — | Nombre completo del usuario. |
| email | VARCHAR(255) | No | — | Correo de autenticación. Tiene índice único. |
| password_hash | VARCHAR(255) | No | — | Hash BCrypt costo 12. Nunca se expone en JSON. |
| rol | VARCHAR(30) | No | ROLE_DUENO | Rol de seguridad: ROLE_ADMIN, ROLE_VETERINARIO, ROLE_DUENO, ROLE_AUXILIAR. |
| activo | BOOLEAN | No | TRUE | Permite soft delete o desactivación. |
| creado_en | TIMESTAMPTZ | No | NOW() | Fecha de creación en UTC. |
| actualizado_en | TIMESTAMPTZ | No | NOW() | Fecha de última actualización, modificada por trigger. |

## Tabla: mascotas

| Columna | Tipo PostgreSQL 16 | Nulo | Default | Descripción |
|---|---:|---|---|---|
| id | BIGSERIAL | No | Secuencia | Identificador único de la mascota. |
| duenio_id | BIGINT | No | — | FK hacia usuarios(id). |
| nombre | VARCHAR(50) | No | — | Nombre de la mascota. |
| especie | VARCHAR(30) | No | — | Especie: canino, felino, etc. |
| raza | VARCHAR(50) | No | — | Raza registrada. |
| fecha_nacimiento | DATE | No | — | Fecha de nacimiento de la mascota. |
| activo | BOOLEAN | No | TRUE | Soft delete de registros. |
| creado_en | TIMESTAMPTZ | No | NOW() | Fecha de creación. |
| actualizado_en | TIMESTAMPTZ | No | NOW() | Fecha actualizada por trigger. |

## Correspondencia Java ↔ PostgreSQL

| Entidad | Atributo Java | Tipo Java | Tipo PostgreSQL | Restricción |
|---|---|---:|---:|---|
| Usuario | id | Long | BIGSERIAL | PK |
| Usuario | email | String | VARCHAR(255) | UNIQUE NOT NULL |
| Usuario | passwordHash | String | VARCHAR(255) | NOT NULL, BCrypt costo 12 |
| Usuario | rol | Rol enum | VARCHAR(30) | CHECK |
| Usuario | activo | boolean | BOOLEAN | DEFAULT TRUE |
| Usuario | creadoEn | Instant | TIMESTAMPTZ | DEFAULT NOW() |
| Mascota | id | Long | BIGSERIAL | PK |
| Mascota | duenio | Usuario | BIGINT | FK usuarios(id) |
| Mascota | nombre | String | VARCHAR(50) | NOT NULL |
| Mascota | especie | String | VARCHAR(30) | NOT NULL |
| Mascota | raza | String | VARCHAR(50) | NOT NULL |
| Mascota | fechaNacimiento | LocalDate | DATE | NOT NULL |
| Mascota | activo | boolean | BOOLEAN | DEFAULT TRUE |