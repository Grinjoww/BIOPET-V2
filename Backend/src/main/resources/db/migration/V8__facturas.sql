-- V8__facturas.sql
-- FASE 4A - Facturas: cabecera, detalle, pagos, documentos y bitacora SRI.
-- Migracion ADITIVA. Requiere V1 (usuarios, mascotas, set_actualizado_en) y V7
-- (punto_emision, concepto_facturable). No modifica nada previo.
--
-- Alcance deliberado: SOLO modelo persistente. Aqui no se genera ninguna clave
-- de acceso, ningun secuencial y ninguna numeracion; no hay triggers de negocio
-- ni maquina de estados en SQL. Todo eso es responsabilidad del servicio de
-- emision (fases posteriores). PostgreSQL protege aqui unicamente invariantes
-- ESTRUCTURALES (formato, rango, unicidad, integridad referencial).
--
-- ---------------------------------------------------------------------------
-- DECISION 1: "facturas" NO tiene columna "activo".
-- ---------------------------------------------------------------------------
-- El resto de entidades operativas de BIOPET (usuarios, mascotas, citas,
-- consultas, vacunas) usa baja logica con "activo BOOLEAN". Una factura fiscal
-- NO la copia, y es una decision consciente, no un olvido:
--
--   1. Un comprobante autorizado por el SRI no puede "desaparecer": existe en
--      los sistemas del SRI y en la contabilidad del contribuyente. Una baja
--      logica daria la falsa impresion de que si puede.
--   2. "activo" seria una SEGUNDA fuente de verdad sobre el ciclo de vida del
--      documento, en conflicto con "estado". Dos banderas que responden a la
--      misma pregunta acaban divergiendo.
--   3. El riesgo practico es concreto: el patron "WHERE activo = TRUE" esta
--      por todo el repositorio (ver los repositories de V1-V4). Bastaria
--      copiarlo por inercia en un reporte fiscal para ocultar facturas
--      AUTORIZADAS de un cuadre tributario, sin que nada fallase.
--   4. La anulacion fiscal real (que el SRI trata como un documento aparte) no
--      es "activo = FALSE"; se modelara cuando toque, con su propio flujo.
--
-- Ciclo de vida = "estado" y nada mas:
--      BORRADOR   -> documento interno, sin valor fiscal, editable
--      EMITIDA    -> ya tiene clave de acceso y secuencial; inmutable
--      AUTORIZADA -> autorizada por el SRI
--      RECHAZADA  -> devuelta/no autorizada
--
-- Lo unico legitimamente eliminable es un BORRADOR (nunca tuvo clave de acceso
-- ni secuencial, luego no consumio numeracion fiscal). Esa decision pertenece
-- al futuro servicio; en esta fase NO se implementa ningun DELETE, y la propia
-- BD lo desincentiva: las FK de las tablas hijas NO llevan ON DELETE CASCADE.
--
-- ---------------------------------------------------------------------------
-- DECISION 2: snapshots, no getters dinamicos.
-- ---------------------------------------------------------------------------
-- Los valores fiscales emitidos viven en columnas propias de facturas y
-- factura_detalles. Las FK a usuarios, mascotas, punto_emision y
-- concepto_facturable son SOLO contexto y trazabilidad. Si manana cambia el RUC
-- del emisor, la direccion del cliente, el precio de un concepto o el
-- porcentaje de un impuesto, la factura ya emitida no cambia.

-- ============================================================================
-- 1. facturas
-- ============================================================================
CREATE TABLE IF NOT EXISTS facturas (
    id BIGSERIAL PRIMARY KEY,

    -- --- Identidad interna -------------------------------------------------
    -- Propietario FUNCIONAL en BIOPET (quien la ve en "mis facturas"), que no
    -- es necesariamente el receptor tributario: el dueno de la mascota puede
    -- pedir la factura a nombre de su empresa. El receptor tributario real es
    -- el snapshot comprador_* de mas abajo.
    usuario_id BIGINT NOT NULL,
    -- Contexto clinico opcional: una factura puede ser solo de productos.
    mascota_id BIGINT,
    -- Nulo mientras es BORRADOR: todavia no se decidio desde que punto emitir.
    punto_emision_id BIGINT,
    fecha_emision DATE NOT NULL,
    estado VARCHAR(20) NOT NULL,

    -- --- Numeracion fiscal: TODO nulo hasta emitir -------------------------
    -- Ni la clave ni el secuencial se generan en SQL (ver ClaveAccesoGenerator
    -- y la futura reserva concurrente de Fase 4B).
    ambiente SMALLINT,
    establecimiento VARCHAR(3),
    punto_emision VARCHAR(3),
    secuencial BIGINT,
    codigo_numerico VARCHAR(8),
    clave_acceso VARCHAR(49),

    -- --- Estado frente al SRI ----------------------------------------------
    estado_recepcion VARCHAR(20),
    estado_autorizacion VARCHAR(3),
    numero_autorizacion VARCHAR(49),
    fecha_autorizacion TIMESTAMPTZ,
    proximo_intento_en TIMESTAMPTZ,
    intentos_autorizacion INTEGER NOT NULL DEFAULT 0,

    -- --- Snapshot del comprador --------------------------------------------
    -- Congelado desde datos_facturacion en el momento de emitir. Nullable
    -- porque un BORRADOR incompleto debe poder guardarse.
    comprador_tipo_identificacion VARCHAR(2),
    comprador_identificacion VARCHAR(20),
    comprador_razon_social VARCHAR(300),
    comprador_direccion VARCHAR(300),
    comprador_email VARCHAR(255),
    comprador_telefono VARCHAR(20),

    -- --- Snapshot del emisor -----------------------------------------------
    -- Congelado desde emisor_fiscal + punto_emision al emitir.
    emisor_ruc VARCHAR(13),
    emisor_razon_social VARCHAR(300),
    emisor_nombre_comercial VARCHAR(300),
    emisor_direccion_matriz VARCHAR(300),
    emisor_direccion_establecimiento VARCHAR(300),
    emisor_obligado_contabilidad BOOLEAN,
    emisor_contribuyente_especial VARCHAR(13),
    emisor_rimpe BOOLEAN,
    emisor_agente_retencion_resolucion VARCHAR(20),

    -- --- Totales ------------------------------------------------------------
    -- NUMERIC(14,2) = XSD totalDigits=14 / fractionDigits=2 (ver EscalasSri).
    -- NOT NULL DEFAULT 0 no es una restriccion fiscal prematura: un borrador
    -- vacio tiene totales cero, no totales desconocidos.
    total_sin_impuestos NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_descuento NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_impuestos NUMERIC(14,2) NOT NULL DEFAULT 0,
    importe_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    moneda VARCHAR(15),

    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- --- Integridad referencial (conservadora, sin ON DELETE CASCADE) -------
    CONSTRAINT fk_facturas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_facturas_mascota FOREIGN KEY (mascota_id) REFERENCES mascotas(id),
    CONSTRAINT fk_facturas_punto_emision FOREIGN KEY (punto_emision_id) REFERENCES punto_emision(id),

    -- --- Estados (mismo patron VARCHAR + CHECK que chk_citas_estado de V2) --
    CONSTRAINT chk_facturas_estado CHECK (
        estado IN ('BORRADOR', 'EMITIDA', 'AUTORIZADA', 'RECHAZADA')),
    CONSTRAINT chk_facturas_estado_recepcion CHECK (
        estado_recepcion IS NULL OR estado_recepcion IN ('RECIBIDA', 'DEVUELTA')),
    CONSTRAINT chk_facturas_estado_autorizacion CHECK (
        estado_autorizacion IS NULL OR estado_autorizacion IN ('PPR', 'AUT', 'NAT')),
    CONSTRAINT chk_facturas_intentos CHECK (intentos_autorizacion >= 0),

    -- --- Forma de la numeracion --------------------------------------------
    CONSTRAINT chk_facturas_ambiente CHECK (ambiente IS NULL OR ambiente IN (1, 2)),
    CONSTRAINT chk_facturas_establecimiento CHECK (
        establecimiento IS NULL OR establecimiento ~ '^[0-9]{3}$'),
    CONSTRAINT chk_facturas_punto_emision CHECK (
        punto_emision IS NULL OR punto_emision ~ '^[0-9]{3}$'),
    -- 9 digitos: el secuencial ocupa exactamente esa longitud en la clave.
    CONSTRAINT chk_facturas_secuencial CHECK (
        secuencial IS NULL OR secuencial BETWEEN 1 AND 999999999),
    CONSTRAINT chk_facturas_codigo_numerico CHECK (
        codigo_numerico IS NULL OR codigo_numerico ~ '^[0-9]{8}$'),
    -- 49 digitos exactos (TABLA 1 de la Ficha v2.34). El digito verificador
    -- modulo 11 NO se comprueba aqui: eso es logica, y ya vive en
    -- ClaveAccesoGenerator.esValida().
    CONSTRAINT chk_facturas_clave_acceso CHECK (
        clave_acceso IS NULL OR clave_acceso ~ '^[0-9]{49}$'),
    CONSTRAINT chk_facturas_comprador_tipo_identificacion CHECK (
        comprador_tipo_identificacion IS NULL
        OR comprador_tipo_identificacion IN ('04', '05', '06', '07', '08')),
    CONSTRAINT chk_facturas_emisor_ruc CHECK (
        emisor_ruc IS NULL OR emisor_ruc ~ '^[0-9]{13}$'),

    -- --- Totales no negativos ----------------------------------------------
    CONSTRAINT chk_facturas_total_sin_impuestos CHECK (total_sin_impuestos >= 0),
    CONSTRAINT chk_facturas_total_descuento CHECK (total_descuento >= 0),
    CONSTRAINT chk_facturas_total_impuestos CHECK (total_impuestos >= 0),
    CONSTRAINT chk_facturas_importe_total CHECK (importe_total >= 0)
);

-- Clave de acceso unica. En PostgreSQL los NULL son distintos entre si dentro
-- de un indice unico, asi que esto permite N borradores sin clave y a la vez
-- prohibe dos facturas con la misma clave.
CREATE UNIQUE INDEX IF NOT EXISTS idx_facturas_clave_acceso
    ON facturas (clave_acceso);

-- Un secuencial no puede repetirse dentro del MISMO punto de emision y el
-- MISMO ambiente. Indice parcial: solo aplica cuando la factura ya esta
-- numerada. Consecuencia buscada, y probada en los tests:
--     PRUEBAS    001-001-000000001   -> permitido
--     PRODUCCION 001-001-000000001   -> permitido (ambiente distinto)
--     PRUEBAS    001-001-000000001   -> RECHAZADO si ya existia
CREATE UNIQUE INDEX IF NOT EXISTS idx_facturas_numeracion
    ON facturas (punto_emision_id, ambiente, secuencial)
    WHERE punto_emision_id IS NOT NULL
      AND ambiente IS NOT NULL
      AND secuencial IS NOT NULL;

-- Listado "mis facturas" del usuario, filtrado por estado y mas recientes
-- primero.
CREATE INDEX IF NOT EXISTS idx_facturas_usuario_estado_fecha
    ON facturas (usuario_id, estado, fecha_emision DESC);

-- Barrido del futuro reintento de autorizacion.
CREATE INDEX IF NOT EXISTS idx_facturas_estado_autorizacion
    ON facturas (estado, estado_autorizacion);

CREATE INDEX IF NOT EXISTS idx_facturas_mascota ON facturas (mascota_id);

-- ============================================================================
-- 2. factura_detalles
--    Una linea del comprobante. TODOS sus campos son SNAPSHOT: descripcion,
--    precio y tarifa quedan congelados. concepto_facturable_id es solo
--    trazabilidad y es nullable (una linea puede ser un texto libre).
-- ============================================================================
CREATE TABLE IF NOT EXISTS factura_detalles (
    id BIGSERIAL PRIMARY KEY,
    factura_id BIGINT NOT NULL,
    linea INTEGER NOT NULL,
    concepto_facturable_id BIGINT,
    codigo_principal VARCHAR(25) NOT NULL,
    codigo_auxiliar VARCHAR(25),
    descripcion VARCHAR(300) NOT NULL,
    cantidad NUMERIC(18,6) NOT NULL,
    precio_unitario NUMERIC(18,6) NOT NULL,
    descuento NUMERIC(14,2) NOT NULL DEFAULT 0,
    precio_total_sin_impuesto NUMERIC(14,2) NOT NULL,
    impuesto_codigo VARCHAR(2) NOT NULL,
    impuesto_codigo_porcentaje VARCHAR(2) NOT NULL,
    impuesto_tarifa NUMERIC(4,2) NOT NULL,
    base_imponible NUMERIC(14,2) NOT NULL,
    impuesto_valor NUMERIC(14,2) NOT NULL,
    -- Origen clinico de la linea. Deliberadamente NO va en la cabecera: una
    -- misma factura puede mezclar una consulta, dos vacunas y un producto.
    -- Referencia debil a proposito (sin FK polimorfica): las tablas clinicas
    -- no se modifican y una linea puede no tener origen.
    origen_tipo VARCHAR(20),
    origen_id BIGINT,

    CONSTRAINT fk_factura_detalles_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT fk_factura_detalles_concepto FOREIGN KEY (concepto_facturable_id)
        REFERENCES concepto_facturable(id),
    CONSTRAINT uq_factura_detalles_linea UNIQUE (factura_id, linea),
    CONSTRAINT chk_factura_detalles_linea CHECK (linea >= 1),
    CONSTRAINT chk_factura_detalles_cantidad CHECK (cantidad >= 0),
    CONSTRAINT chk_factura_detalles_precio_unitario CHECK (precio_unitario >= 0),
    CONSTRAINT chk_factura_detalles_descuento CHECK (descuento >= 0),
    CONSTRAINT chk_factura_detalles_precio_total CHECK (precio_total_sin_impuesto >= 0),
    CONSTRAINT chk_factura_detalles_impuesto_codigo CHECK (impuesto_codigo IN ('2', '3', '5')),
    CONSTRAINT chk_factura_detalles_impuesto_codigo_porcentaje CHECK (
        impuesto_codigo_porcentaje ~ '^[0-9]{1,2}$'),
    CONSTRAINT chk_factura_detalles_tarifa CHECK (impuesto_tarifa >= 0 AND impuesto_tarifa <= 100),
    CONSTRAINT chk_factura_detalles_base CHECK (base_imponible >= 0),
    CONSTRAINT chk_factura_detalles_impuesto_valor CHECK (impuesto_valor >= 0),
    CONSTRAINT chk_factura_detalles_origen_tipo CHECK (
        origen_tipo IS NULL OR origen_tipo IN ('CONSULTA', 'VACUNA', 'CITA'))
);

CREATE INDEX IF NOT EXISTS idx_factura_detalles_factura ON factura_detalles (factura_id);

-- Para responder "que se facturo a partir de esta consulta/vacuna/cita".
-- Deliberadamente NO es unico: no se prohibe por constraint que un mismo
-- origen aparezca en mas de una factura (p.ej. una nota de credito futura o una
-- refacturacion). Esa regla, si hace falta, sera del servicio.
CREATE INDEX IF NOT EXISTS idx_factura_detalles_origen
    ON factura_detalles (origen_tipo, origen_id);

-- ============================================================================
-- 3. factura_pagos
--    TABLA 24 de la Ficha v2.34; los ocho codigos vigentes son exactamente los
--    de FormaPagoSri (Fase 2). La regla SUM(pagos) = importe_total NO se
--    implementa con trigger: es una invariante de negocio del futuro servicio
--    de emision, no una invariante estructural de la fila.
-- ============================================================================
CREATE TABLE IF NOT EXISTS factura_pagos (
    id BIGSERIAL PRIMARY KEY,
    factura_id BIGINT NOT NULL,
    forma_pago VARCHAR(2) NOT NULL,
    total NUMERIC(14,2) NOT NULL,
    plazo INTEGER,
    unidad_tiempo VARCHAR(20),

    CONSTRAINT fk_factura_pagos_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT chk_factura_pagos_forma_pago CHECK (
        forma_pago IN ('01', '15', '16', '17', '18', '19', '20', '21')),
    CONSTRAINT chk_factura_pagos_total CHECK (total >= 0),
    CONSTRAINT chk_factura_pagos_plazo CHECK (plazo IS NULL OR plazo >= 0)
);

CREATE INDEX IF NOT EXISTS idx_factura_pagos_factura ON factura_pagos (factura_id);

-- ============================================================================
-- 4. factura_documentos
--    Artefactos binarios del comprobante. BYTEA (NO large object / OID): el
--    mapeo JPA usa byte[] sin @Lob, que en el dialecto PostgreSQL de
--    Hibernate 6 resuelve a bytea. @Lob sobre byte[] haria que Hibernate
--    tratase la columna como oid (large object), que necesita transaccion
--    abierta para leerse y deja basura en pg_largeobject. Hay un test que lo
--    comprueba contra el catalogo real (data_type = 'bytea').
-- ============================================================================
CREATE TABLE IF NOT EXISTS factura_documentos (
    id BIGSERIAL PRIMARY KEY,
    factura_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    contenido BYTEA NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    bytes INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_factura_documentos_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT chk_factura_documentos_tipo CHECK (
        tipo IN ('XML_GENERADO', 'XML_FIRMADO', 'XML_AUTORIZADO', 'RIDE_PDF')),
    -- 64 hexadecimales en minuscula: forma canonica de un SHA-256.
    CONSTRAINT chk_factura_documentos_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_factura_documentos_bytes CHECK (bytes >= 0),
    -- Un unico artefacto de cada tipo por factura: el XML firmado es uno solo.
    CONSTRAINT uq_factura_documentos_tipo UNIQUE (factura_id, tipo)
);

-- ============================================================================
-- 5. factura_eventos_sri
--    Bitacora append-only de las llamadas al SRI. En esta fase no la escribe
--    nadie: existe para que el pipeline de recepcion/autorizacion (fases
--    posteriores) tenga donde dejar evidencia auditable de cada intento.
--    "mensajes" es JSONB nativo: se persiste con @JdbcTypeCode(SqlTypes.JSON)
--    de Hibernate 6, sin anadir ninguna libreria externa.
-- ============================================================================
CREATE TABLE IF NOT EXISTS factura_eventos_sri (
    id BIGSERIAL PRIMARY KEY,
    factura_id BIGINT NOT NULL,
    operacion VARCHAR(20) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    mensajes JSONB,
    duracion_ms BIGINT,
    intento INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_factura_eventos_sri_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT chk_factura_eventos_sri_operacion CHECK (
        operacion IN ('RECEPCION', 'AUTORIZACION')),
    CONSTRAINT chk_factura_eventos_sri_resultado CHECK (
        resultado IN ('RECIBIDA', 'DEVUELTA', 'AUT', 'NAT', 'PPR', 'ERROR_TECNICO', 'TIMEOUT')),
    CONSTRAINT chk_factura_eventos_sri_duracion CHECK (duracion_ms IS NULL OR duracion_ms >= 0),
    CONSTRAINT chk_factura_eventos_sri_intento CHECK (intento >= 1)
);

-- Bitacora de una factura, del evento mas reciente al mas antiguo.
CREATE INDEX IF NOT EXISTS idx_factura_eventos_sri_factura_creado
    ON factura_eventos_sri (factura_id, creado_en DESC);

-- ============================================================================
-- 6. Trigger de auditoria
--    Solo "facturas" tiene actualizado_en. Las tablas hijas no lo llevan a
--    proposito: los detalles y pagos se reemplazan en bloque mientras la
--    factura es BORRADOR, y documentos y eventos son append-only (solo
--    creado_en). set_actualizado_en() viene de V1 y no se redefine.
-- ============================================================================
DROP TRIGGER IF EXISTS trg_facturas_actualizado_en ON facturas;
CREATE TRIGGER trg_facturas_actualizado_en
BEFORE UPDATE ON facturas
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

-- ============================================================================
-- 7. Grants para biopet_app (minimo privilegio)
--    Mismo bloque condicional que V5/V6/V7.
--
--    Reparto deliberado del DELETE:
--      * facturas, factura_documentos y factura_eventos_sri -> SIN DELETE.
--        Un comprobante y su evidencia son registro fiscal: la aplicacion no
--        necesita poder borrarlos, y no poder hacerlo es la garantia mas
--        barata de que no ocurrira por accidente.
--      * factura_detalles y factura_pagos -> CON DELETE, porque editar las
--        lineas de un BORRADOR implica quitarlas. La proteccion de las lineas
--        ya emitidas es la ausencia de DELETE sobre su cabecera: sin
--        ON DELETE CASCADE en las FK, la factura nunca se va sola.
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'biopet_app') THEN

        GRANT SELECT, INSERT, UPDATE
            ON facturas, factura_documentos, factura_eventos_sri
            TO biopet_app;

        GRANT SELECT, INSERT, UPDATE, DELETE
            ON factura_detalles, factura_pagos
            TO biopet_app;

        GRANT USAGE, SELECT
            ON SEQUENCE facturas_id_seq, factura_detalles_id_seq,
                        factura_pagos_id_seq, factura_documentos_id_seq,
                        factura_eventos_sri_id_seq
            TO biopet_app;

    END IF;
END
$$;
