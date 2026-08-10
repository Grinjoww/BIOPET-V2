-- V4__vacunas.sql
-- Reservada para Zaida (U4). No modificar si ya fue aplicada; crear V5__ para cambios.
-- Requiere V1 (usuarios, mascotas) ya aplicada.

CREATE TABLE IF NOT EXISTS vacunas (
    id BIGSERIAL PRIMARY KEY,
    mascota_id BIGINT NOT NULL,
    veterinario_id BIGINT,
    tipo VARCHAR(60) NOT NULL,
    fecha_aplicacion DATE NOT NULL,
    proxima_fecha DATE,
    observaciones VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_vacunas_mascota FOREIGN KEY (mascota_id) REFERENCES mascotas(id),
    CONSTRAINT fk_vacunas_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuarios(id)
);

CREATE INDEX IF NOT EXISTS idx_vacunas_mascota ON vacunas (mascota_id);
CREATE INDEX IF NOT EXISTS idx_vacunas_veterinario ON vacunas (veterinario_id);
CREATE INDEX IF NOT EXISTS idx_vacunas_activo ON vacunas (activo);

-- set_actualizado_en() ya existe desde V1__schema_inicial.sql
DROP TRIGGER IF EXISTS trg_vacunas_actualizado_en ON vacunas;
CREATE TRIGGER trg_vacunas_actualizado_en
BEFORE UPDATE ON vacunas
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();
