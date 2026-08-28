-- V7__facturacion_catalogos.sql
-- FASE 4A - Catalogos y configuracion del modulo de facturacion electronica SRI.
-- Migracion ADITIVA: no toca ninguna tabla, funcion, trigger ni grant de V1-V6.
-- Se aplica limpiamente sobre una base con datos preexistentes.
--
-- Patrones reutilizados literalmente de V1-V6:
--   * PK           BIGSERIAL PRIMARY KEY (mismo estilo que usuarios/mascotas/...)
--   * timestamps   TIMESTAMPTZ NOT NULL DEFAULT NOW()
--   * auditoria    trigger BEFORE UPDATE -> set_actualizado_en() (creada en V1,
--                  NO se redefine aqui; ver V2/V4, que la reutilizan igual)
--   * enums        VARCHAR + CHECK (mismo criterio que chk_usuarios_rol de V1 y
--                  chk_citas_estado de V2). NO se crea ningun ENUM nativo de
--                  PostgreSQL: el proyecto no usa ese patron en ninguna parte.
--   * grants       bloque DO $$ con guarda "IF EXISTS (... pg_roles ...)" para
--                  biopet_app (identico a V5/V6 y a afterMigrate.sql)
--
-- IMPORTANTE - por que los GRANT van aqui y no solo en afterMigrate.sql:
-- afterMigrate.sql ejecuta "ALTER DEFAULT PRIVILEGES", que por definicion solo
-- afecta a objetos creados DESPUES de esa sentencia. En una base nueva, V7/V8
-- corren ANTES del callback, de modo que las tablas de este modulo no quedarian
-- cubiertas. Por eso se otorgan explicitamente aqui, igual que hacen V5 y V6
-- con sus rutinas. afterMigrate.sql no se modifica en esta fase.
--
-- NO se inserta ningun dato: ni emisor, ni puntos de emision, ni secuenciales,
-- ni tarifas, ni conceptos. En produccion estas tablas quedan vacias. Los
-- fixtures ficticios viven exclusivamente en los tests.

-- ============================================================================
-- 1. emisor_fiscal
--    Datos tributarios NO secretos del contribuyente que emite. El certificado
--    .p12, su contrasena y el ambiente de runtime NO se guardan aqui (ni en
--    ninguna tabla): son configuracion/secretos de despliegue.
-- ============================================================================
CREATE TABLE IF NOT EXISTS emisor_fiscal (
    id BIGSERIAL PRIMARY KEY,
    ruc VARCHAR(13) NOT NULL,
    razon_social VARCHAR(300) NOT NULL,
    nombre_comercial VARCHAR(300),
    direccion_matriz VARCHAR(300) NOT NULL,
    obligado_contabilidad BOOLEAN NOT NULL DEFAULT FALSE,
    contribuyente_especial VARCHAR(13),
    rimpe BOOLEAN NOT NULL DEFAULT FALSE,
    agente_retencion_resolucion VARCHAR(20),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Solo forma estructural (13 digitos), la misma comprobacion que ya hace
    -- ClaveAccesoRequest en el nucleo fiscal de la Fase 2. Deliberadamente NO
    -- se implementa un "algoritmo universal de RUC ecuatoriano": no existe uno
    -- publicado que aplique a todos los tipos de contribuyente, y la validez
    -- tributaria real la resuelve el propio SRI al autorizar (errores 46/63).
    CONSTRAINT chk_emisor_fiscal_ruc CHECK (ruc ~ '^[0-9]{13}$'),
    CONSTRAINT uq_emisor_fiscal_ruc UNIQUE (ruc)
);

-- ============================================================================
-- 2. punto_emision
--    Establecimiento + punto de emision (la "serie" 001-001 de la clave de
--    acceso). NO guarda contador: el contador depende del ambiente y vive en
--    secuencial_emision.
-- ============================================================================
CREATE TABLE IF NOT EXISTS punto_emision (
    id BIGSERIAL PRIMARY KEY,
    emisor_fiscal_id BIGINT NOT NULL,
    establecimiento VARCHAR(3) NOT NULL,
    punto_emision VARCHAR(3) NOT NULL,
    direccion_establecimiento VARCHAR(300),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_punto_emision_emisor FOREIGN KEY (emisor_fiscal_id)
        REFERENCES emisor_fiscal(id),
    CONSTRAINT chk_punto_emision_establecimiento CHECK (establecimiento ~ '^[0-9]{3}$'),
    CONSTRAINT chk_punto_emision_punto CHECK (punto_emision ~ '^[0-9]{3}$'),
    CONSTRAINT uq_punto_emision_serie UNIQUE (emisor_fiscal_id, establecimiento, punto_emision)
);

CREATE INDEX IF NOT EXISTS idx_punto_emision_emisor ON punto_emision (emisor_fiscal_id);

-- ============================================================================
-- 3. secuencial_emision
--    Contador fiscal, UNO POR (punto de emision, ambiente).
--
--    Decision de diseno: el ambiente NO se guarda en emisor_fiscal ni en
--    punto_emision. Un mismo 001-001 tiene dos contadores independientes,
--    porque PRUEBAS y PRODUCCION son numeraciones fiscales distintas y el
--    numero 001-001-000000001 debe poder existir en ambas. El ambiente activo
--    sera configuracion de runtime (SRI_AMBIENTE) y quedara congelado como
--    snapshot en facturas.ambiente al emitir.
--
--    Aqui SOLO se define el modelo persistente. El incremento concurrente
--    (SELECT ... FOR UPDATE / PESSIMISTIC_WRITE) es Fase 4B y no se implementa.
--
--    Nota: "ultimo_secuencial" NO tiene nada que ver con las secuencias
--    BIGSERIAL de las PK. Una PK es un identificador interno; el secuencial
--    fiscal es un numero legal, contiguo y sin huecos por punto y ambiente.
-- ============================================================================
CREATE TABLE IF NOT EXISTS secuencial_emision (
    id BIGSERIAL PRIMARY KEY,
    punto_emision_id BIGINT NOT NULL,
    -- TABLA 4 de la Ficha v2.34: 1 = PRUEBAS, 2 = PRODUCCION. Es un codigo
    -- numerico del catalogo del SRI, no un enum interno de BIOPET; por eso se
    -- persiste como SMALLINT (mismos valores que AmbienteSri de la Fase 2).
    ambiente SMALLINT NOT NULL,
    ultimo_secuencial BIGINT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_secuencial_emision_punto FOREIGN KEY (punto_emision_id)
        REFERENCES punto_emision(id),
    CONSTRAINT chk_secuencial_emision_ambiente CHECK (ambiente IN (1, 2)),
    -- 0 = todavia no se emitio nada; 999999999 = tope de los 9 digitos que la
    -- clave de acceso reserva al secuencial.
    CONSTRAINT chk_secuencial_emision_rango CHECK (ultimo_secuencial BETWEEN 0 AND 999999999),
    CONSTRAINT uq_secuencial_emision_punto_ambiente UNIQUE (punto_emision_id, ambiente)
);

-- ============================================================================
-- 4. tarifa_impuesto
--    Configuracion tributaria VIGENTE POR FECHA. No es una "tarifa
--    veterinaria" ni un precio: es el par (codigo de impuesto, codigo de
--    porcentaje) del catalogo del SRI con el porcentaje que estuvo/esta vigente
--    en un intervalo.
--
--    La tabla admite historico a proposito: cuando cambia un porcentaje se
--    cierra la fila vigente (vigente_hasta) y se inserta otra. La factura ya
--    emitida no se ve afectada, porque su tarifa quedo congelada en
--    factura_detalles.impuesto_tarifa.
--
--    NO SE SEMBRA NINGUNA FILA. No se hardcodea ningun porcentaje ni fecha de
--    vigencia cuya validez no este confirmada documentalmente. En produccion la
--    tabla nace vacia y se puebla por configuracion.
-- ============================================================================
CREATE TABLE IF NOT EXISTS tarifa_impuesto (
    id BIGSERIAL PRIMARY KEY,
    codigo_impuesto VARCHAR(2) NOT NULL,
    codigo_porcentaje VARCHAR(2) NOT NULL,
    descripcion VARCHAR(100) NOT NULL,
    tarifa NUMERIC(4,2) NOT NULL,
    vigente_desde DATE NOT NULL,
    vigente_hasta DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- TABLA 16 de la Ficha v2.34; el XSD oficial restringe <codigo> al patron
    -- [235]. Mismos tres valores que CodigoImpuestoSri (Fase 2).
    CONSTRAINT chk_tarifa_impuesto_codigo CHECK (codigo_impuesto IN ('2', '3', '5')),
    -- TABLA 17: codigo de porcentaje, 1 o 2 digitos. No se enumeran valores
    -- concretos: el catalogo cambia y no se hardcodea aqui.
    CONSTRAINT chk_tarifa_impuesto_codigo_porcentaje CHECK (codigo_porcentaje ~ '^[0-9]{1,2}$'),
    -- NUMERIC(4,2) = XSD totalDigits=4 / fractionDigits=2 (ver EscalasSri).
    CONSTRAINT chk_tarifa_impuesto_tarifa CHECK (tarifa >= 0 AND tarifa <= 100),
    CONSTRAINT chk_tarifa_impuesto_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    CONSTRAINT uq_tarifa_impuesto_vigencia UNIQUE (codigo_impuesto, codigo_porcentaje, vigente_desde)
);

CREATE INDEX IF NOT EXISTS idx_tarifa_impuesto_vigencia
    ON tarifa_impuesto (codigo_impuesto, codigo_porcentaje, vigente_desde DESC);

-- ============================================================================
-- 5. concepto_facturable
--    Catalogo interno de lo que BIOPET puede facturar.
--
--    Guarda el PAR de codigos tributarios (codigo_impuesto, codigo_porcentaje),
--    NO una FK fisica a una fila concreta de tarifa_impuesto. Es deliberado:
--    tarifa_impuesto es historico y su fila vigente cambia con el tiempo;
--    apuntar a una fila obligaria a reescribir todo el catalogo cada vez que
--    cambie un porcentaje. El porcentaje aplicable se resuelve por fecha al
--    emitir y se congela en factura_detalles.impuesto_tarifa.
--
--    NO SE SEMBRA NINGUN CONCEPTO. Nada de "consulta veterinaria = 15%".
-- ============================================================================
CREATE TABLE IF NOT EXISTS concepto_facturable (
    id BIGSERIAL PRIMARY KEY,
    -- 25 = longitud maxima de <codigoPrincipal> en el XSD de factura.
    codigo VARCHAR(25) NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    -- NUMERIC(18,6) = XSD totalDigits=18 / fractionDigits=6 de <precioUnitario>.
    precio_unitario NUMERIC(18,6) NOT NULL,
    codigo_impuesto VARCHAR(2) NOT NULL,
    codigo_porcentaje VARCHAR(2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_concepto_facturable_tipo CHECK (
        tipo IN ('CONSULTA', 'VACUNA', 'PROCEDIMIENTO', 'MEDICAMENTO', 'PRODUCTO', 'OTRO')),
    CONSTRAINT chk_concepto_facturable_precio CHECK (precio_unitario >= 0),
    CONSTRAINT chk_concepto_facturable_codigo_impuesto CHECK (codigo_impuesto IN ('2', '3', '5')),
    CONSTRAINT chk_concepto_facturable_codigo_porcentaje CHECK (codigo_porcentaje ~ '^[0-9]{1,2}$')
);

-- Codigo unico SOLO entre conceptos activos: al dar de baja un concepto su
-- codigo queda libre para reutilizarse, sin perder la fila historica a la que
-- puedan apuntar detalles ya facturados.
CREATE UNIQUE INDEX IF NOT EXISTS idx_concepto_facturable_codigo_activo
    ON concepto_facturable (codigo)
    WHERE activo = TRUE;

CREATE INDEX IF NOT EXISTS idx_concepto_facturable_tipo_activo
    ON concepto_facturable (tipo, activo);

-- ============================================================================
-- 6. datos_facturacion
--    Identidad TRIBUTARIA del cliente, completamente separada de "usuarios".
--
--    usuarios sigue siendo identidad/autenticacion (email, password_hash, rol)
--    y NO se toca: no se le agrega ni una columna. Un mismo usuario puede tener
--    varias identidades de facturacion (relacion 1:N): a nombre propio con
--    cedula, a nombre de su empresa con RUC, etc.
-- ============================================================================
CREATE TABLE IF NOT EXISTS datos_facturacion (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo_identificacion VARCHAR(2) NOT NULL,
    identificacion VARCHAR(20) NOT NULL,
    razon_social VARCHAR(300) NOT NULL,
    direccion VARCHAR(300),
    telefono VARCHAR(20),
    email_facturacion VARCHAR(255),
    predeterminado BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- FK conservadora, sin ON DELETE CASCADE: borrar un usuario con datos de
    -- facturacion debe fallar, no arrastrarlos en silencio.
    CONSTRAINT fk_datos_facturacion_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id),
    -- TABLA 6 de la Ficha v2.34 (tipo de identificacion del comprador).
    CONSTRAINT chk_datos_facturacion_tipo_identificacion CHECK (
        tipo_identificacion IN ('04', '05', '06', '07', '08'))
);

CREATE INDEX IF NOT EXISTS idx_datos_facturacion_usuario ON datos_facturacion (usuario_id);

-- Invariante estructural: un usuario no puede tener DOS identidades activas
-- marcadas como predeterminadas a la vez. Se implementa con indice unico
-- parcial (nativo de PostgreSQL) en lugar de un trigger: es declarativo, lo
-- garantiza el motor y no depende de que el codigo de aplicacion lo recuerde.
CREATE UNIQUE INDEX IF NOT EXISTS idx_datos_facturacion_predeterminado_unico
    ON datos_facturacion (usuario_id)
    WHERE predeterminado = TRUE AND activo = TRUE;

-- ============================================================================
-- 7. Triggers de auditoria
--    set_actualizado_en() ya existe desde V1__schema_inicial.sql; aqui SOLO se
--    enlaza a las tablas nuevas. No se redefine la funcion (mismo criterio que
--    V2 y V4).
-- ============================================================================
DROP TRIGGER IF EXISTS trg_emisor_fiscal_actualizado_en ON emisor_fiscal;
CREATE TRIGGER trg_emisor_fiscal_actualizado_en
BEFORE UPDATE ON emisor_fiscal
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_punto_emision_actualizado_en ON punto_emision;
CREATE TRIGGER trg_punto_emision_actualizado_en
BEFORE UPDATE ON punto_emision
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_secuencial_emision_actualizado_en ON secuencial_emision;
CREATE TRIGGER trg_secuencial_emision_actualizado_en
BEFORE UPDATE ON secuencial_emision
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_tarifa_impuesto_actualizado_en ON tarifa_impuesto;
CREATE TRIGGER trg_tarifa_impuesto_actualizado_en
BEFORE UPDATE ON tarifa_impuesto
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_concepto_facturable_actualizado_en ON concepto_facturable;
CREATE TRIGGER trg_concepto_facturable_actualizado_en
BEFORE UPDATE ON concepto_facturable
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_datos_facturacion_actualizado_en ON datos_facturacion;
CREATE TRIGGER trg_datos_facturacion_actualizado_en
BEFORE UPDATE ON datos_facturacion
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

-- ============================================================================
-- 8. Grants para biopet_app (minimo privilegio)
--    Mismo bloque condicional que V5/V6: no-op si el rol no existe (Render usa
--    el rol de la BD gestionada; los tests de integracion usan test_user).
--    Solo CRUD sobre las tablas del modulo y USAGE/SELECT sobre sus secuencias.
--    Nada de DDL, nada de "ALL TABLES IN SCHEMA".
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT SELECT, INSERT, UPDATE, DELETE
            ON emisor_fiscal, punto_emision, secuencial_emision,
               tarifa_impuesto, concepto_facturable, datos_facturacion
            TO biopet_app;

        GRANT USAGE, SELECT
            ON SEQUENCE emisor_fiscal_id_seq, punto_emision_id_seq,
                        secuencial_emision_id_seq, tarifa_impuesto_id_seq,
                        concepto_facturable_id_seq, datos_facturacion_id_seq
            TO biopet_app;

    END IF;
END
$$;
