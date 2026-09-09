-- V10__respaldos_dia_hora_y_auditoria.sql
-- Correccion del docente sobre la Fase de respaldos (V9), mas el modulo de
-- auditoria pedido en la misma revision. Migracion ADITIVA sobre V1-V9: no
-- toca ninguna tabla, funcion, trigger, grant ni dato de facturacion/SRI
-- (V7/V8) ni del resto del esquema (V1-V6).
--
-- POR QUE V10 Y NO SE EDITA V9:
-- V9 ya estaba aplicada (success=true en flyway_schema_history) en la base
-- de trabajo persistente biopet_db_1m_v2 antes de esta fase -comprobado con
-- "SELECT version, success FROM flyway_schema_history" antes de escribir
-- este archivo. Reescribir una migracion ya aplicada rompe Flyway (el
-- checksum grabado en flyway_schema_history ya no coincidiria) y es
-- exactamente lo que Flyway esta disenado para impedir. Por eso esta fase
-- llega como V10, no como una edicion de V9.
--
-- ============================================================================
-- 1. respaldo_configuracion: intervalo (V9) -> dia+hora (correccion del
--    docente: "una opcion de configuracion donde el administrador pueda
--    definir el DIA y la HORA en que se realizara el respaldo", sin exponer
--    CRON al ADMIN). Se reemplazan las columnas, no se agregan al lado de
--    las viejas: la fila es un singleton de configuracion VIGENTE, no un
--    historial, asi que no hay ambiguedad de "cual de las dos manda".
-- ============================================================================
ALTER TABLE respaldo_configuracion
    DROP CONSTRAINT IF EXISTS chk_respaldo_configuracion_intervalo_unidad,
    DROP CONSTRAINT IF EXISTS chk_respaldo_configuracion_intervalo_valor,
    DROP COLUMN IF EXISTS intervalo_valor,
    DROP COLUMN IF EXISTS intervalo_unidad;

ALTER TABLE respaldo_configuracion
    ADD COLUMN dia_semana VARCHAR(10) NOT NULL DEFAULT 'MONDAY',
    ADD COLUMN hora TIME NOT NULL DEFAULT '02:00:00';

ALTER TABLE respaldo_configuracion
    ADD CONSTRAINT chk_respaldo_configuracion_dia_semana CHECK (
        dia_semana IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'));

-- Los DEFAULT de arriba solo importan si la fila singleton (id=1) ya
-- existiera con datos de V9 (una demo previa que ya la creo perezosamente).
-- En una base nueva la fila no existe todavia -RespaldoConfiguracionService
-- la sigue creando de forma perezosa, ahora con esos mismos valores
-- (lunes 02:00)- asi que este ALTER tampoco siembra datos por si mismo.

-- ============================================================================
-- 2. auditoria_evento: auditoria persistida y consultable SOLO por ADMIN.
--    Dos origenes hacia la MISMA tabla (ver AuditoriaEvento.java):
--      - AuditoriaHttpFilter: toda peticion AUTENTICADA POST/PUT/PATCH/DELETE
--        contra /api/** (salvo /api/auth/** y /api/admin/respaldos/**, que
--        ya tienen sus propios eventos nombrados mas abajo).
--      - Eventos nombrados: los de autenticacion que YA emitia
--        AuthenticationAuditService (LOGIN_SUCCESS, LOGIN_FAILURE,
--        LOGIN_RATE_LIMITED, REGISTRO_RATE_LIMITED, REFRESH_SUCCESS,
--        REFRESH_FAILURE, LOGOUT_SUCCESS, TOKEN_REVOKED -reutilizados tal
--        cual, no se inventan nombres nuevos-) y los 5 de respaldos
--        (BACKUP_CONFIG_UPDATED, BACKUP_MANUAL_REQUESTED,
--        BACKUP_AUTOMATIC_STARTED, BACKUP_COMPLETED, BACKUP_FAILED).
--    Deliberadamente SIN columnas para contrasenas, JWT, certificado, XML
--    fiscal ni el cuerpo de la peticion: ver AuditoriaService.registrar, el
--    UNICO punto de escritura, que nunca recibe esos datos para empezar.
-- ============================================================================
CREATE TABLE IF NOT EXISTS auditoria_evento (
    id BIGSERIAL PRIMARY KEY,
    fecha_hora TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_email VARCHAR(255),
    accion VARCHAR(60) NOT NULL,
    modulo VARCHAR(60) NOT NULL,
    recurso VARCHAR(255),
    metodo_http VARCHAR(10),
    resultado VARCHAR(20) NOT NULL,
    status_http INTEGER
);

-- fecha_hora DESC: es el orden por defecto del listado paginado (mas
-- reciente primero). usuario_email/accion/modulo: los 3 filtros de la
-- pantalla que no son un rango de fechas.
CREATE INDEX IF NOT EXISTS idx_auditoria_evento_fecha_hora ON auditoria_evento (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_evento_usuario_email ON auditoria_evento (usuario_email);
CREATE INDEX IF NOT EXISTS idx_auditoria_evento_accion ON auditoria_evento (accion);
CREATE INDEX IF NOT EXISTS idx_auditoria_evento_modulo ON auditoria_evento (modulo);

-- ============================================================================
-- 3. Grants para biopet_app (minimo privilegio)
--    Mismo bloque condicional que V5/V6/V7/V9: no-op si el rol no existe.
--    auditoria_evento es de solo insercion+lectura para la aplicacion (nunca
--    se actualiza ni se borra una fila de auditoria), pero se otorgan los 4
--    verbos igual que el resto de tablas del proyecto -no hay un precedente
--    de privilegios mas finos que INSERT/SELECT/UPDATE/DELETE en ninguna
--    otra migracion, y mantenerlo consistente es mas simple que inventar una
--    excepcion aqui.
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT SELECT, INSERT, UPDATE, DELETE
            ON auditoria_evento
            TO biopet_app;

        GRANT USAGE, SELECT
            ON SEQUENCE auditoria_evento_id_seq
            TO biopet_app;

    END IF;
END
$$;
