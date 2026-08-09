-- V2__citas.sql
-- Reservada exclusivamente para el modulo de Citas (Jaime).
-- Fred usara V3 para Consultas y Zaida V4 para Vacunas.
-- Flyway ejecuta este archivo en orden, despues de V1. No modificar si ya fue
-- aplicado; crear V5__ (o la siguiente libre) para cambios futuros de citas.

CREATE TABLE IF NOT EXISTS citas (
    id BIGSERIAL PRIMARY KEY,
    mascota_id BIGINT NOT NULL,
    veterinario_id BIGINT NOT NULL,
    fecha_hora TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
    motivo VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_citas_mascota FOREIGN KEY (mascota_id) REFERENCES mascotas(id),
    CONSTRAINT fk_citas_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuarios(id)
);

ALTER TABLE citas
    ADD CONSTRAINT chk_citas_estado
    CHECK (estado IN ('PROGRAMADA','CANCELADA','COMPLETADA'));

CREATE INDEX IF NOT EXISTS idx_citas_mascota ON citas (mascota_id);
CREATE INDEX IF NOT EXISTS idx_citas_veterinario ON citas (veterinario_id);
CREATE INDEX IF NOT EXISTS idx_citas_activo ON citas (activo);

-- Reutiliza la funcion set_actualizado_en() ya creada en V1__schema_inicial.sql;
-- no se redefine aqui.
DROP TRIGGER IF EXISTS trg_citas_actualizado_en ON citas;
CREATE TRIGGER trg_citas_actualizado_en
BEFORE UPDATE ON citas
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();
