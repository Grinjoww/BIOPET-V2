CREATE TABLE IF NOT EXISTS consultas (
    id BIGSERIAL PRIMARY KEY,
    mascota_id BIGINT NOT NULL,
    veterinario_id BIGINT NOT NULL,
    fecha_consulta TIMESTAMPTZ NOT NULL,
    motivo VARCHAR(200) NOT NULL,
    diagnostico VARCHAR(500),
    tratamiento VARCHAR(500),
    observaciones VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_consultas_mascota FOREIGN KEY (mascota_id) REFERENCES mascotas(id),
    CONSTRAINT fk_consultas_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuarios(id)
);

CREATE INDEX IF NOT EXISTS idx_consultas_mascota ON consultas(mascota_id);
CREATE INDEX IF NOT EXISTS idx_consultas_veterinario ON consultas(veterinario_id);