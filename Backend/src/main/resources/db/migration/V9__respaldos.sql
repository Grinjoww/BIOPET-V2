-- V9__respaldos.sql
-- Modulo administrativo de RESPALDOS (Proyecto Final de Administracion de
-- Bases de Datos). Migracion ADITIVA: no toca ninguna tabla, funcion,
-- trigger, grant ni dato de V1-V8. No tiene relacion alguna con
-- facturacion electronica / SRI: no crea ninguna FK hacia esas tablas ni
-- las modifica.
--
-- Patrones reutilizados literalmente de V1-V8 (ver V7 para el mismo
-- razonamiento in extenso):
--   * PK           BIGSERIAL PRIMARY KEY (respaldo_historial)
--   * timestamps   TIMESTAMPTZ (nunca TIMESTAMP a secas, mismo criterio que
--                  el resto del esquema)
--   * enums        VARCHAR + CHECK, NO un ENUM nativo de PostgreSQL (el
--                  proyecto no usa ese patron en ninguna parte)
--   * grants       bloque DO $$ con guarda "IF EXISTS (... pg_roles ...)"
--                  para biopet_app, identico a V5/V6/V7 y a
--                  afterMigrate.sql. Van AQUI y no solo en
--                  afterMigrate.sql por la misma razon documentada en V7:
--                  afterMigrate corre DESPUES de Flyway, asi que en una
--                  base nueva estas tablas no quedarian cubiertas por sus
--                  GRANT explicitos (si solo se dependiera de
--                  "ALTER DEFAULT PRIVILEGES", que solo afecta objetos
--                  creados DESPUES de esa sentencia).
--
-- NO se inserta ningun dato: ni siquiera la fila de configuracion. La
-- aplicacion crea esa unica fila (id=1) de forma perezosa, con
-- activo=false, en el primer acceso -ver RespaldoConfiguracionService-,
-- exactamente igual que V7/V8 dejan sus catalogos fiscales vacios en
-- produccion. Los respaldos automaticos NUNCA arrancan solos por el mero
-- hecho de aplicar esta migracion.

-- ============================================================================
-- 1. respaldo_configuracion
--    Fila UNICA (singleton): la columna id esta fijada a 1 por el CHECK, no
--    hay BIGSERIAL. Evita la ambiguedad de "cual fila es la vigente" sin
--    necesitar una tabla de una sola columna ni un patron distinto al resto
--    del esquema. actualizado_en/actualizado_por se escriben EXCLUSIVAMENTE
--    desde el guardado explicito de configuracion (quien cambio
--    activo/intervalo y cuando); las ejecuciones automaticas/manuales
--    actualizan ultimo_respaldo_en/proximo_respaldo_en sin tocar esas dos
--    columnas, por eso NO llevan un trigger BEFORE UPDATE que las
--    pisotearia en cada ejecucion -a diferencia de set_actualizado_en() en
--    V1, aqui el timestamp de auditoria lo fija la aplicacion a proposito.
-- ============================================================================
CREATE TABLE IF NOT EXISTS respaldo_configuracion (
    id BIGINT PRIMARY KEY DEFAULT 1,
    activo BOOLEAN NOT NULL DEFAULT FALSE,
    intervalo_valor INTEGER NOT NULL DEFAULT 60,
    intervalo_unidad VARCHAR(10) NOT NULL DEFAULT 'MINUTOS',
    ultimo_respaldo_en TIMESTAMPTZ,
    proximo_respaldo_en TIMESTAMPTZ,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_por VARCHAR(255),
    CONSTRAINT chk_respaldo_configuracion_id_singleton CHECK (id = 1),
    CONSTRAINT chk_respaldo_configuracion_intervalo_unidad
        CHECK (intervalo_unidad IN ('MINUTOS', 'HORAS', 'DIAS')),
    CONSTRAINT chk_respaldo_configuracion_intervalo_valor CHECK (intervalo_valor > 0)
);

-- ============================================================================
-- 2. respaldo_historial
--    Una fila por intento de respaldo (manual o automatico), sin importar
--    el resultado. estado transiciona EN_PROCESO -> EXITOSO|FALLIDO; nunca
--    se borra una fila para "reintentar" -cada intento es una fila nueva.
--    Deliberadamente SIN columna binaria: el archivo .dump vive en disco
--    (BACKUP_STORAGE_PATH), aqui solo su nombre y tamano.
-- ============================================================================
CREATE TABLE IF NOT EXISTS respaldo_historial (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(10) NOT NULL,
    estado VARCHAR(10) NOT NULL,
    iniciado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizado_en TIMESTAMPTZ,
    duracion_ms BIGINT,
    nombre_archivo VARCHAR(255),
    tamano_bytes BIGINT,
    -- Mensaje SIEMPRE curado por la aplicacion (categorias fijas: timeout,
    -- codigo de salida, error de E/S...), nunca el stderr crudo de pg_dump
    -- ni ninguna cadena de conexion. El detalle completo, si existe, queda
    -- solo en el log del servidor -mismo criterio que ya aplica
    -- GlobalExceptionHandler para los fallos de firma/SRI.
    mensaje_error_seguro VARCHAR(500),
    -- NULL para AUTOMATICO (no lo dispara ninguna persona); email del
    -- ADMIN para MANUAL.
    ejecutado_por VARCHAR(255),
    CONSTRAINT chk_respaldo_historial_tipo CHECK (tipo IN ('MANUAL', 'AUTOMATICO')),
    CONSTRAINT chk_respaldo_historial_estado CHECK (estado IN ('EN_PROCESO', 'EXITOSO', 'FALLIDO'))
);

-- Para el listado paginado (ORDER BY iniciado_en DESC) y para el chequeo de
-- concurrencia (existe alguna fila EN_PROCESO).
CREATE INDEX IF NOT EXISTS idx_respaldo_historial_iniciado_en ON respaldo_historial (iniciado_en DESC);
CREATE INDEX IF NOT EXISTS idx_respaldo_historial_estado ON respaldo_historial (estado);

-- ============================================================================
-- 3. Grants para biopet_app (minimo privilegio)
--    Mismo bloque condicional que V5/V6/V7: no-op si el rol no existe
--    (Render usa el rol de la BD gestionada; los tests de integracion usan
--    test_user). Solo CRUD sobre las dos tablas y USAGE/SELECT sobre la
--    unica secuencia real (respaldo_historial_id_seq; respaldo_configuracion
--    no tiene secuencia, su id es fijo). Nada de DDL, nada de
--    "ALL TABLES IN SCHEMA".
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT SELECT, INSERT, UPDATE, DELETE
            ON respaldo_configuracion, respaldo_historial
            TO biopet_app;

        GRANT USAGE, SELECT
            ON SEQUENCE respaldo_historial_id_seq
            TO biopet_app;

    END IF;
END
$$;
