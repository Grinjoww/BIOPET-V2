-- ============================================================================
-- scripts/db/cargar-demo-1m.sql
-- ----------------------------------------------------------------------------
-- Carga de DEMOSTRACION: > 1.000.000 de filas sinteticas sobre el esquema
-- ACTUAL de BIOPET-V2 (tablas de dominio creadas por las migraciones V1-V4).
--
-- QUE ES Y QUE NO ES ESTE SCRIPT
--   * NO es una migracion Flyway. Vive fuera de
--     Backend/src/main/resources/db/migration/ a proposito: no se registra en
--     flyway_schema_history, no se ejecuta al arrancar el backend y no altera
--     el esquema. Se ejecuta a mano, solo para poblar una base de demostracion.
--   * NO toca NADA de facturacion electronica / SRI. No escribe en facturas,
--     factura_detalles, factura_documentos, factura_eventos_sri, factura_pagos,
--     secuencial_emision, emisor_fiscal, punto_emision, tarifa_impuesto,
--     concepto_facturable ni datos_facturacion. Solo LEE dos de ellas
--     (SELECT count(*)) como guarda previa a la purga, para no romper jamas una
--     clave foranea fiscal.
--   * NO crea indices nuevos. La optimizacion posterior con EXPLAIN ANALYZE
--     debe basarse en evidencia medida sobre esta carga, no en indices
--     adivinados de antemano.
--   * NO usa DROP DATABASE, DROP SCHEMA ni TRUNCATE. La limpieza es un DELETE
--     acotado exclusivamente a las filas DEMO (ver "CONVENCION DEMO", 3.1).
--
-- REQUISITOS PREVIOS
--   Flyway ya aplico al menos V1..V4 (usuarios, mascotas, citas, consultas,
--   vacunas). El script lo verifica y aborta si falta alguna tabla.
--
-- USO
--   psql -h localhost -p 5432 -U <owner> -d biopet_db_1m_v2 \
--        -f scripts/db/cargar-demo-1m.sql
--
-- Ver scripts/db/README-demo-1m.md para el procedimiento completo reproducible.
-- ============================================================================

\set ON_ERROR_STOP on
\timing on

-- ----------------------------------------------------------------------------
-- PARAMETROS DE VOLUMEN
-- ----------------------------------------------------------------------------
-- Distribucion elegida (razonamiento completo en el README):
--
--     usuarios        2.000   (200 veterinarios + 1.800 duenios)
--     mascotas       10.000   (~5,5 mascotas por duenio)
--     citas         400.000   (40 citas por mascota)
--     consultas     300.000   (30 consultas por mascota)
--     vacunas       300.000   (30 vacunas por mascota)
--     ------------------------------------------------------
--     TOTAL       1.012.000   > 1.000.000  (margen del 1,2 %)
--
-- Deliberadamente NO se genera un millon de usuarios: el volumen se concentra
-- en las tablas transaccionales (citas/consultas/vacunas), que es donde
-- realmente interesa medir paginacion y planes de ejecucion.
--
-- Para cambiar el volumen, editar SOLO estos seis valores.
\set n_usuarios      2000
\set n_veterinarios  200
\set n_mascotas      10000
\set n_citas         400000
\set n_consultas     300000
\set n_vacunas       300000

-- Derivado: los usuarios que no son veterinarios actuan como duenios.
-- Debe cumplirse  n_duenios = n_usuarios - n_veterinarios.
\set n_duenios       1800

-- ============================================================================
-- 1. GUARDA DE SEGURIDAD  (se ejecuta ANTES de cualquier escritura)
-- ============================================================================
-- Doble barrera, deliberadamente redundante:
--   (a) lista blanca: solo se permite el nombre de la base de demostracion;
--   (b) lista negra: nombres conocidos de bases reales, por si alguien amplia
--       la lista blanca sin pensarlo.
-- Se implementa como DO/RAISE del lado del servidor -y no con \if de psql-
-- para que la guarda siga siendo efectiva aunque el fichero se ejecute desde
-- pgAdmin, DBeaver o cualquier cliente que ignore los meta-comandos \.
-- ============================================================================

DO $guarda$
DECLARE
    v_bd         text   := current_database();
    v_permitidas text[] := ARRAY['biopet_db_1m_v2'];
    v_prohibidas text[] := ARRAY['biopet_db', 'BiopetABD', 'biopetabd',
                                 'postgres', 'template1'];
BEGIN
    IF v_bd = ANY (v_prohibidas) THEN
        RAISE EXCEPTION
            E'ABORTADO POR SEGURIDAD.\n'
            '  Base de datos actual : %\n'
            '  Esta base esta en la LISTA NEGRA de cargar-demo-1m.sql.\n'
            '  Este script solo puede ejecutarse sobre una base de DEMOSTRACION\n'
            '  descartable. Cree biopet_db_1m_v2 y ejecutelo alli.\n'
            '  No se ha escrito ni borrado ninguna fila.',
            v_bd;
    END IF;

    IF NOT (v_bd = ANY (v_permitidas)) THEN
        RAISE EXCEPTION
            E'ABORTADO POR SEGURIDAD.\n'
            '  Base de datos actual : %\n'
            '  Bases permitidas     : %\n'
            '  Este script solo puede ejecutarse sobre una base de DEMOSTRACION.\n'
            '  No se ha escrito ni borrado ninguna fila.',
            v_bd, array_to_string(v_permitidas, ', ');
    END IF;

    RAISE NOTICE 'Guarda OK: base de datos de demostracion "%"', v_bd;
END
$guarda$;

-- ============================================================================
-- 2. VERIFICACION DEL ESQUEMA (V1-V4 ya aplicadas por Flyway)
-- ============================================================================

DO $esquema$
DECLARE
    v_tabla  text;
    v_faltan text[] := ARRAY[]::text[];
BEGIN
    FOREACH v_tabla IN ARRAY ARRAY['usuarios','mascotas','citas','consultas','vacunas'] LOOP
        IF to_regclass('public.' || v_tabla) IS NULL THEN
            v_faltan := v_faltan || v_tabla;
        END IF;
    END LOOP;

    IF array_length(v_faltan, 1) IS NOT NULL THEN
        RAISE EXCEPTION
            E'ABORTADO: faltan tablas del esquema BIOPET-V2: %.\n'
            '  Arranque primero el backend contra esta base para que Flyway\n'
            '  aplique V1..V8, o aplique las migraciones manualmente.',
            array_to_string(v_faltan, ', ');
    END IF;

    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE WARNING
            'No existe flyway_schema_history: las tablas existen pero no se '
            'puede confirmar que provengan de las migraciones V1..V8.';
    END IF;
END
$esquema$;

-- ============================================================================
-- 3. CARGA
-- ============================================================================
-- Todo dentro de UNA transaccion: o queda la carga completa y coherente, o no
-- queda nada. Eso es lo que permite re-ejecutar el script sin dejar restos a
-- medias si se interrumpe.
-- ============================================================================

BEGIN;

-- Ajustes de sesion (todos USERSET: no requieren superusuario) validos solo
-- dentro de esta transaccion. synchronous_commit=off evita esperar el fsync de
-- cada commit: aceptable para datos sinteticos y descartables, jamas para datos
-- reales. No se modifica ningun parametro global del servidor.
SET LOCAL synchronous_commit = off;
SET LOCAL work_mem = '64MB';
SET LOCAL maintenance_work_mem = '256MB';

-- Marca de tiempo de inicio, para reportar la duracion total al final.
CREATE TEMP TABLE tmp_reloj (etapa text PRIMARY KEY, t timestamptz NOT NULL)
    ON COMMIT DROP;
INSERT INTO tmp_reloj VALUES ('inicio', clock_timestamp());

-- ----------------------------------------------------------------------------
-- 3.1 CONVENCION DEMO + PURGA IDEMPOTENTE
-- ----------------------------------------------------------------------------
-- Las filas generadas por este script se reconocen SIEMPRE por el dominio de
-- correo de sus usuarios:
--
--     email LIKE '%@demo.biopet.local'
--
-- El TLD ".local" esta reservado para uso interno (mDNS, RFC 6762): ningun
-- correo real puede pertenecer a ese dominio, de modo que la marca no puede
-- colisionar con datos reales. A partir de ahi:
--   - mascota DEMO   = la que cuelga de un usuario DEMO;
--   - cita/consulta/vacuna DEMO = la que cuelga de una mascota DEMO o de un
--     veterinario DEMO.
-- Ninguna fila ajena entra en ese conjunto.
--
-- Por eso la limpieza es un DELETE acotado y NO un TRUNCATE: si alguien deja
-- datos propios en la base de demo, sobreviven intactos.
-- ----------------------------------------------------------------------------

\echo ''
\echo '>>> [1/8] Identificando filas DEMO preexistentes...'

CREATE TEMP TABLE tmp_purge_usuarios ON COMMIT DROP AS
SELECT id FROM usuarios WHERE email LIKE '%@demo.biopet.local';
ALTER TABLE tmp_purge_usuarios ADD PRIMARY KEY (id);

CREATE TEMP TABLE tmp_purge_mascotas ON COMMIT DROP AS
SELECT m.id FROM mascotas m JOIN tmp_purge_usuarios u ON u.id = m.duenio_id;
ALTER TABLE tmp_purge_mascotas ADD PRIMARY KEY (id);

-- GUARDA FISCAL: si alguna fila DEMO quedase referenciada desde facturacion,
-- abortamos en lugar de borrar. Estas tablas SOLO se leen; nunca se escriben.
DO $fiscal$
DECLARE
    v_facturas bigint := 0;
    v_datos    bigint := 0;
BEGIN
    IF to_regclass('public.facturas') IS NOT NULL THEN
        SELECT count(*) INTO v_facturas
        FROM facturas f
        WHERE f.usuario_id IN (SELECT id FROM tmp_purge_usuarios)
           OR f.mascota_id IN (SELECT id FROM tmp_purge_mascotas);
    END IF;

    IF to_regclass('public.datos_facturacion') IS NOT NULL THEN
        SELECT count(*) INTO v_datos
        FROM datos_facturacion d
        WHERE d.usuario_id IN (SELECT id FROM tmp_purge_usuarios);
    END IF;

    IF v_facturas > 0 OR v_datos > 0 THEN
        RAISE EXCEPTION
            E'ABORTADO: hay datos de FACTURACION referenciando filas DEMO.\n'
            '  facturas          : %\n'
            '  datos_facturacion : %\n'
            '  Borrar esas filas DEMO romperia claves foraneas fiscales.\n'
            '  Revise manualmente antes de continuar. No se borro nada.',
            v_facturas, v_datos;
    END IF;
END
$fiscal$;

\echo '>>> [2/8] Purgando carga DEMO anterior (DELETE acotado, sin TRUNCATE)...'

-- Orden inverso al de las claves foraneas: hijos primero.
DELETE FROM citas
 WHERE mascota_id     IN (SELECT id FROM tmp_purge_mascotas)
    OR veterinario_id IN (SELECT id FROM tmp_purge_usuarios);

DELETE FROM consultas
 WHERE mascota_id     IN (SELECT id FROM tmp_purge_mascotas)
    OR veterinario_id IN (SELECT id FROM tmp_purge_usuarios);

DELETE FROM vacunas
 WHERE mascota_id     IN (SELECT id FROM tmp_purge_mascotas)
    OR veterinario_id IN (SELECT id FROM tmp_purge_usuarios);

DELETE FROM mascotas WHERE id IN (SELECT id FROM tmp_purge_mascotas);
DELETE FROM usuarios WHERE id IN (SELECT id FROM tmp_purge_usuarios);

-- ----------------------------------------------------------------------------
-- 3.2 USUARIOS
-- ----------------------------------------------------------------------------
-- Los ids NO se insertan de forma explicita: se dejan a BIGSERIAL. Asi la carga
-- nunca colisiona con filas preexistentes y las secuencias quedan correctas por
-- construccion, sin necesidad de ningun setval de correccion (el script de
-- verificacion lo comprueba de todos modos).
--
-- password_hash lleva un valor deliberadamente INVALIDO como BCrypt: ninguna de
-- estas cuentas sinteticas puede iniciar sesion. Es relleno, no una credencial.
--
-- La correspondencia "fila i -> id asignado" se recupera del propio correo
-- (usuarioNNNNNN@...), sin depender del orden en que el motor devuelva las
-- filas del RETURNING.
-- ----------------------------------------------------------------------------

\echo '>>> [3/8] Generando usuarios...'

CREATE TEMP TABLE tmp_usuarios (rn bigint PRIMARY KEY, id bigint NOT NULL, rol text NOT NULL)
    ON COMMIT DROP;

WITH nuevos AS (
    INSERT INTO usuarios (nombre, email, password_hash, rol, activo, creado_en, actualizado_en)
    SELECT
        'Usuario Demo ' || lpad(i::text, 6, '0'),
        'usuario' || lpad(i::text, 6, '0') || '@demo.biopet.local',
        'DEMO-NO-LOGIN-hash-invalido-no-utilizable',
        CASE WHEN i <= :n_veterinarios THEN 'ROLE_VETERINARIO' ELSE 'ROLE_DUENO' END,
        -- 1 de cada 50 inactivo: da selectividad real a los filtros por activo.
        (i % 50) <> 0,
        TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => i),
        TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => i)
    FROM generate_series(1, :n_usuarios) AS g(i)
    RETURNING id, email, rol
)
INSERT INTO tmp_usuarios (rn, id, rol)
SELECT substring(email from 8 for 6)::bigint, id, rol
FROM nuevos;

-- Sub-poblaciones con numeracion densa 1..N para repartir las claves foraneas.
CREATE TEMP TABLE tmp_vets ON COMMIT DROP AS
SELECT rn, id FROM tmp_usuarios WHERE rol = 'ROLE_VETERINARIO';
ALTER TABLE tmp_vets ADD PRIMARY KEY (rn);

CREATE TEMP TABLE tmp_duenios ON COMMIT DROP AS
SELECT rn - :n_veterinarios AS rn, id FROM tmp_usuarios WHERE rol = 'ROLE_DUENO';
ALTER TABLE tmp_duenios ADD PRIMARY KEY (rn);

-- ----------------------------------------------------------------------------
-- 3.3 MASCOTAS
-- ----------------------------------------------------------------------------
\echo '>>> [4/8] Generando mascotas...'

CREATE TEMP TABLE tmp_mascotas (rn bigint PRIMARY KEY, id bigint NOT NULL)
    ON COMMIT DROP;

WITH nuevas AS (
    INSERT INTO mascotas (duenio_id, nombre, especie, raza, fecha_nacimiento,
                          activo, creado_en, actualizado_en)
    SELECT
        d.id,
        'Mascota Demo ' || lpad(i::text, 6, '0'),
        (ARRAY['CANINO','FELINO','AVE','ROEDOR','REPTIL','CONEJO'])[1 + (i % 6)],
        (ARRAY['Mestizo','Labrador','Pastor Aleman','Siames','Persa','Poodle',
               'Bulldog','Golden Retriever','Criollo','Angora'])[1 + (i % 10)],
        DATE '2015-01-01' + ((i * 7) % 3650),
        (i % 40) <> 0,
        TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => i),
        TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => i)
    FROM generate_series(1, :n_mascotas) AS g(i)
    -- Reparto deterministico y uniforme del duenio. El multiplicador de Knuth
    -- es coprimo con :n_duenios, de modo que i -> rn recorre todo el ciclo:
    -- ningun duenio queda vacio ni sobrecargado.
    JOIN tmp_duenios d ON d.rn = 1 + ((i::bigint * 2654435761) % :n_duenios)
    RETURNING id, nombre
)
INSERT INTO tmp_mascotas (rn, id)
SELECT substring(nombre from 14 for 6)::bigint, id
FROM nuevas;

-- ----------------------------------------------------------------------------
-- 3.4 CITAS  (tabla de mayor volumen)
-- ----------------------------------------------------------------------------
-- Mascota y veterinario se reparten con multiplicadores DISTINTOS, cada uno
-- coprimo con su modulo, para que las dos claves foraneas queden
-- descorrelacionadas: si se usara el mismo i % n en ambas, cada mascota veria
-- siempre al mismo veterinario y los futuros EXPLAIN ANALYZE medirian una
-- distribucion irreal.
-- ----------------------------------------------------------------------------
\echo '>>> [5/8] Generando citas...'

INSERT INTO citas (mascota_id, veterinario_id, fecha_hora, estado, motivo,
                   activo, creado_en, actualizado_en)
SELECT
    m.id,
    v.id,
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600)),
    (ARRAY['PROGRAMADA','COMPLETADA','CANCELADA'])[1 + (i % 3)],
    'Cita demo ' || lpad(i::text, 7, '0') || ' - ' ||
        (ARRAY['Control general','Vacunacion','Desparasitacion',
               'Revision post-operatoria','Control dental',
               'Chequeo dermatologico'])[1 + (i % 6)],
    (i % 25) <> 0,
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600)),
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600))
FROM generate_series(1, :n_citas) AS g(i)
JOIN tmp_mascotas m ON m.rn = 1 + ((i::bigint * 2654435761) % :n_mascotas)
JOIN tmp_vets     v ON v.rn = 1 + ((i::bigint * 2246822519) % :n_veterinarios);

-- ----------------------------------------------------------------------------
-- 3.5 CONSULTAS
-- ----------------------------------------------------------------------------
\echo '>>> [6/8] Generando consultas...'

INSERT INTO consultas (mascota_id, veterinario_id, fecha_consulta, motivo,
                       diagnostico, tratamiento, observaciones,
                       activo, creado_en, actualizado_en)
SELECT
    m.id,
    v.id,
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600)),
    'Consulta demo ' || lpad(i::text, 7, '0') || ' - ' ||
        (ARRAY['Malestar general','Control de peso','Herida superficial',
               'Otitis','Gastroenteritis','Chequeo anual'])[1 + (i % 6)],
    (ARRAY['Sin hallazgos relevantes','Dermatitis leve','Sobrepeso grado I',
           'Infeccion bacteriana','Cuadro viral autolimitado'])[1 + (i % 5)],
    (ARRAY['Reposo e hidratacion','Antibiotico 7 dias','Dieta controlada',
           'Antiinflamatorio 5 dias','Sin tratamiento farmacologico'])[1 + (i % 5)],
    -- 1 de cada 4 sin observaciones: la columna es NULLABLE y conviene que la
    -- carga lo refleje, para que los planes futuros vean NULLs reales.
    CASE WHEN (i % 4) = 0 THEN NULL
         ELSE 'Observacion sintetica de la consulta ' || lpad(i::text, 7, '0')
    END,
    (i % 30) <> 0,
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600)),
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600))
FROM generate_series(1, :n_consultas) AS g(i)
JOIN tmp_mascotas m ON m.rn = 1 + ((i::bigint * 1597334677) % :n_mascotas)
JOIN tmp_vets     v ON v.rn = 1 + ((i::bigint * 2246822519) % :n_veterinarios);

-- ----------------------------------------------------------------------------
-- 3.6 VACUNAS
-- ----------------------------------------------------------------------------
-- vacunas.veterinario_id es la unica clave foranea NULLABLE del dominio (V4).
-- Se deja NULL en 1 de cada 10 filas para que la carga ejercite ese caso.
-- ----------------------------------------------------------------------------
\echo '>>> [7/8] Generando vacunas...'

INSERT INTO vacunas (mascota_id, veterinario_id, tipo, fecha_aplicacion,
                     proxima_fecha, observaciones, activo, creado_en, actualizado_en)
SELECT
    m.id,
    CASE WHEN (i % 10) = 0 THEN NULL ELSE v.id END,
    (ARRAY['Rabia','Triple Felina','Parvovirus','Moquillo','Leptospirosis',
           'Polivalente','Tos de las perreras'])[1 + (i % 7)],
    DATE '2024-01-01' + (i % 730),
    DATE '2024-01-01' + (i % 730) + 365,
    CASE WHEN (i % 5) = 0 THEN NULL
         ELSE 'Lote sintetico ' || lpad(i::text, 7, '0')
    END,
    (i % 35) <> 0,
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600)),
    TIMESTAMPTZ '2024-01-01 08:00:00+00' + make_interval(mins => (i % 525600))
FROM generate_series(1, :n_vacunas) AS g(i)
JOIN tmp_mascotas m ON m.rn = 1 + ((i::bigint * 3221225473) % :n_mascotas)
JOIN tmp_vets     v ON v.rn = 1 + ((i::bigint * 2654435761) % :n_veterinarios);

-- ----------------------------------------------------------------------------
-- 3.7 CIERRE DE LA TRANSACCION
-- ----------------------------------------------------------------------------
INSERT INTO tmp_reloj VALUES ('fin', clock_timestamp());

\echo ''
\echo '--- Tiempo de generacion (dentro de la transaccion) ---'
SELECT
    to_char(f.t - i.t, 'HH24:MI:SS.MS')                AS tiempo_carga,
    round(extract(epoch FROM (f.t - i.t))::numeric, 2) AS segundos
FROM tmp_reloj i, tmp_reloj f
WHERE i.etapa = 'inicio' AND f.etapa = 'fin';

COMMIT;

-- ============================================================================
-- 4. POST-CARGA: ESTADISTICAS
-- ============================================================================
-- ANALYZE (no VACUUM FULL, no REINDEX) unicamente sobre las 5 tablas afectadas.
-- Sin esto el planificador seguiria usando estadisticas de tablas vacias y
-- cualquier EXPLAIN ANALYZE posterior seria ruido.
-- Se ejecuta FUERA de la transaccion para que las estadisticas queden visibles
-- de inmediato para el resto de sesiones.
-- ============================================================================

\echo ''
\echo '>>> [8/8] ANALYZE sobre las tablas afectadas...'

ANALYZE usuarios;
ANALYZE mascotas;
ANALYZE citas;
ANALYZE consultas;
ANALYZE vacunas;

\echo ''
\echo '============================================================'
\echo ' CARGA COMPLETADA. Ejecute ahora:'
\echo '   psql ... -d biopet_db_1m_v2 -f scripts/db/verificar-demo-1m.sql'
\echo '============================================================'
