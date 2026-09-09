-- ============================================================================
-- scripts/db/verificar-demo-1m.sql
-- ----------------------------------------------------------------------------
-- Verificacion de la carga de demostracion generada por
-- scripts/db/cargar-demo-1m.sql.
--
-- Script de SOLO LECTURA: no inserta, no actualiza y no borra ninguna fila.
-- Por eso -a diferencia de cargar-demo-1m.sql- no lleva guarda de nombre de
-- base de datos: puede ejecutarse con seguridad contra cualquier base para
-- inspeccionarla.
--
-- USO
--   psql -h localhost -p 5432 -U <owner> -d biopet_db_1m_v2 \
--        -f scripts/db/verificar-demo-1m.sql
-- ============================================================================

\set ON_ERROR_STOP on
\timing on
\pset null '(null)'

\echo ''
\echo '############################################################'
\echo '# VERIFICACION DE LA CARGA DEMO BIOPET-V2'
\echo '############################################################'

SELECT current_database()                         AS base_de_datos,
       current_user                               AS usuario,
       split_part(version(), ',', 1)              AS servidor,
       pg_size_pretty(pg_database_size(current_database())) AS tamanio;

-- ============================================================================
-- A. CONTEO POR TABLA + TOTAL GENERAL      <-- cifra para la presentacion
-- ============================================================================
\echo ''
\echo '=== A. REGISTROS POR TABLA ==================================='

WITH c(ord, tabla, registros) AS (
    SELECT 1, 'usuarios',  count(*) FROM usuarios
    UNION ALL
    SELECT 2, 'mascotas',  count(*) FROM mascotas
    UNION ALL
    SELECT 3, 'citas',     count(*) FROM citas
    UNION ALL
    SELECT 4, 'consultas', count(*) FROM consultas
    UNION ALL
    SELECT 5, 'vacunas',   count(*) FROM vacunas
)
SELECT tabla, registros, to_char(registros, 'FM999G999G999') AS formateado
FROM (
    SELECT ord, tabla, registros FROM c
    UNION ALL
    SELECT 9, 'TOTAL_GENERAL', sum(registros) FROM c
) x
ORDER BY ord;

\echo ''
\echo '=== A.1 CUMPLIMIENTO DEL OBJETIVO ============================'

WITH c(registros) AS (
    SELECT count(*) FROM usuarios
    UNION ALL SELECT count(*) FROM mascotas
    UNION ALL SELECT count(*) FROM citas
    UNION ALL SELECT count(*) FROM consultas
    UNION ALL SELECT count(*) FROM vacunas
)
SELECT sum(registros)                                     AS total_general,
       1000000                                            AS objetivo,
       sum(registros) - 1000000                           AS margen,
       CASE WHEN sum(registros) > 1000000
            THEN 'CUMPLIDO  (> 1.000.000)'
            ELSE 'NO CUMPLIDO'
       END                                                AS resultado
FROM c;

-- ============================================================================
-- B. DESGLOSE DEMO vs NO-DEMO
-- ============================================================================
-- Confirma que la carga sintetica es identificable y que no se pisaron datos
-- ajenos. Las filas DEMO se reconocen por el dominio '@demo.biopet.local'.
-- ============================================================================
\echo ''
\echo '=== B. DESGLOSE DEMO / NO-DEMO ==============================='

WITH u_demo AS (
    SELECT id FROM usuarios WHERE email LIKE '%@demo.biopet.local'
), m_demo AS (
    SELECT m.id FROM mascotas m JOIN u_demo u ON u.id = m.duenio_id
)
SELECT 'usuarios'  AS tabla,
       (SELECT count(*) FROM u_demo)                                        AS demo,
       (SELECT count(*) FROM usuarios) - (SELECT count(*) FROM u_demo)      AS no_demo
UNION ALL
SELECT 'mascotas',
       (SELECT count(*) FROM m_demo),
       (SELECT count(*) FROM mascotas) - (SELECT count(*) FROM m_demo)
UNION ALL
SELECT 'citas',
       (SELECT count(*) FROM citas WHERE mascota_id IN (SELECT id FROM m_demo)),
       (SELECT count(*) FROM citas WHERE mascota_id NOT IN (SELECT id FROM m_demo))
UNION ALL
SELECT 'consultas',
       (SELECT count(*) FROM consultas WHERE mascota_id IN (SELECT id FROM m_demo)),
       (SELECT count(*) FROM consultas WHERE mascota_id NOT IN (SELECT id FROM m_demo))
UNION ALL
SELECT 'vacunas',
       (SELECT count(*) FROM vacunas WHERE mascota_id IN (SELECT id FROM m_demo)),
       (SELECT count(*) FROM vacunas WHERE mascota_id NOT IN (SELECT id FROM m_demo));

-- ============================================================================
-- C. INTEGRIDAD REFERENCIAL  (todos los contadores deben dar 0)
-- ============================================================================
-- PostgreSQL ya garantiza estas FK, pero se comprueban explicitamente para
-- dejar evidencia de que la carga masiva no introdujo huerfanos.
-- ============================================================================
\echo ''
\echo '=== C. INTEGRIDAD REFERENCIAL (todo debe ser 0) =============='

SELECT 'mascotas.duenio_id -> usuarios'      AS relacion,
       count(*)                              AS huerfanos
  FROM mascotas m LEFT JOIN usuarios u ON u.id = m.duenio_id WHERE u.id IS NULL
UNION ALL
SELECT 'citas.mascota_id -> mascotas',
       count(*) FROM citas c LEFT JOIN mascotas m ON m.id = c.mascota_id WHERE m.id IS NULL
UNION ALL
SELECT 'citas.veterinario_id -> usuarios',
       count(*) FROM citas c LEFT JOIN usuarios u ON u.id = c.veterinario_id WHERE u.id IS NULL
UNION ALL
SELECT 'consultas.mascota_id -> mascotas',
       count(*) FROM consultas c LEFT JOIN mascotas m ON m.id = c.mascota_id WHERE m.id IS NULL
UNION ALL
SELECT 'consultas.veterinario_id -> usuarios',
       count(*) FROM consultas c LEFT JOIN usuarios u ON u.id = c.veterinario_id WHERE u.id IS NULL
UNION ALL
SELECT 'vacunas.mascota_id -> mascotas',
       count(*) FROM vacunas v LEFT JOIN mascotas m ON m.id = v.mascota_id WHERE m.id IS NULL
UNION ALL
-- vacunas.veterinario_id es NULLABLE: un NULL no es un huerfano.
SELECT 'vacunas.veterinario_id -> usuarios',
       count(*) FROM vacunas v LEFT JOIN usuarios u ON u.id = v.veterinario_id
       WHERE v.veterinario_id IS NOT NULL AND u.id IS NULL;

\echo ''
\echo '=== C.1 RESTRICCIONES CHECK / DOMINIOS (todo debe ser 0) ====='

SELECT 'usuarios.rol fuera de chk_usuarios_rol' AS validacion,
       count(*) AS violaciones
  FROM usuarios
 WHERE rol NOT IN ('ROLE_ADMIN','ROLE_VETERINARIO','ROLE_DUENO','ROLE_AUXILIAR')
UNION ALL
SELECT 'citas.estado fuera de chk_citas_estado',
       count(*) FROM citas
 WHERE estado NOT IN ('PROGRAMADA','CANCELADA','COMPLETADA')
UNION ALL
SELECT 'usuarios.email duplicado (idx unico)',
       count(*) FROM (SELECT email FROM usuarios GROUP BY email HAVING count(*) > 1) d
UNION ALL
-- Coherencia del dominio: los veterinarios referenciados desde citas deben
-- tener realmente rol ROLE_VETERINARIO.
SELECT 'citas con veterinario cuyo rol no es ROLE_VETERINARIO',
       count(*) FROM citas c JOIN usuarios u ON u.id = c.veterinario_id
 WHERE u.rol <> 'ROLE_VETERINARIO';

-- ============================================================================
-- D. SECUENCIAS
-- ============================================================================
-- La carga NO inserta ids explicitos: usa los BIGSERIAL. Por tanto last_value
-- debe ser >= max(id) en las cinco tablas y no hace falta ningun setval. Si
-- alguna fila apareciera como 'REVISAR', la correccion seria:
--     SELECT setval('<sec>', (SELECT max(id) FROM <tabla>));
-- ============================================================================
\echo ''
\echo '=== D. SECUENCIAS (deben estar todas OK) ====================='

SELECT tabla, secuencia, last_value, max_id,
       CASE WHEN last_value IS NULL AND max_id IS NULL THEN 'OK (tabla vacia)'
            WHEN last_value >= max_id                  THEN 'OK'
            ELSE 'REVISAR'
       END AS estado
FROM (
    SELECT 'usuarios' AS tabla, 'usuarios_id_seq' AS secuencia,
           (SELECT last_value FROM pg_sequences WHERE schemaname='public' AND sequencename='usuarios_id_seq') AS last_value,
           (SELECT max(id) FROM usuarios) AS max_id
    UNION ALL
    SELECT 'mascotas', 'mascotas_id_seq',
           (SELECT last_value FROM pg_sequences WHERE schemaname='public' AND sequencename='mascotas_id_seq'),
           (SELECT max(id) FROM mascotas)
    UNION ALL
    SELECT 'citas', 'citas_id_seq',
           (SELECT last_value FROM pg_sequences WHERE schemaname='public' AND sequencename='citas_id_seq'),
           (SELECT max(id) FROM citas)
    UNION ALL
    SELECT 'consultas', 'consultas_id_seq',
           (SELECT last_value FROM pg_sequences WHERE schemaname='public' AND sequencename='consultas_id_seq'),
           (SELECT max(id) FROM consultas)
    UNION ALL
    SELECT 'vacunas', 'vacunas_id_seq',
           (SELECT last_value FROM pg_sequences WHERE schemaname='public' AND sequencename='vacunas_id_seq'),
           (SELECT max(id) FROM vacunas)
) s
ORDER BY tabla;

-- ============================================================================
-- E. INDICES EXISTENTES Y TAMANIOS
-- ============================================================================
-- Inventario de lo que YA existe (creado por V1-V4). Este script no crea
-- indices: la optimizacion posterior debe partir de EXPLAIN ANALYZE medido
-- sobre esta carga, no de indices anadidos a ciegas.
-- ============================================================================
\echo ''
\echo '=== E. INDICES EXISTENTES (creados por V1-V4) ================'

SELECT tablename AS tabla, indexname AS indice,
       pg_size_pretty(pg_relation_size((schemaname||'.'||indexname)::regclass)) AS tamanio
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('usuarios','mascotas','citas','consultas','vacunas')
ORDER BY tablename, indexname;

\echo ''
\echo '=== E.1 TAMANIO DE LAS TABLAS ================================'

SELECT t AS tabla,
       pg_size_pretty(pg_table_size(t::regclass))    AS datos,
       pg_size_pretty(pg_indexes_size(t::regclass))  AS indices,
       pg_size_pretty(pg_total_relation_size(t::regclass)) AS total,
       (SELECT last_analyze IS NOT NULL OR last_autoanalyze IS NOT NULL
          FROM pg_stat_user_tables WHERE relname = t)      AS estadisticas_ok
FROM unnest(ARRAY['usuarios','mascotas','citas','consultas','vacunas']) AS t
ORDER BY pg_total_relation_size(t::regclass) DESC;

-- ============================================================================
-- F. FACTURACION / SRI INTACTA
-- ============================================================================
-- Evidencia de que la carga demo no escribio en el subsistema fiscal. Estas
-- tablas se LEEN aqui unicamente para contar filas.
-- ============================================================================
\echo ''
\echo '=== F. TABLAS FISCALES (la carga demo NO escribe en ellas) ==='

SELECT t AS tabla_fiscal,
       CASE WHEN to_regclass('public.'||t) IS NULL
            THEN '(no existe en esta base)'
            ELSE (SELECT n_live_tup::text FROM pg_stat_user_tables WHERE relname = t)
       END AS filas_estimadas
FROM unnest(ARRAY['facturas','factura_detalles','factura_documentos',
                  'factura_eventos_sri','factura_pagos','secuencial_emision',
                  'emisor_fiscal','punto_emision','tarifa_impuesto',
                  'concepto_facturable','datos_facturacion']) AS t
ORDER BY t;

-- ============================================================================
-- G. ESTADO DE FLYWAY
-- ============================================================================
\echo ''
\echo '=== G. FLYWAY SCHEMA HISTORY ================================='

SELECT installed_rank, version, description, type, success,
       installed_on::timestamp(0) AS instalada_en
FROM flyway_schema_history
ORDER BY installed_rank;

\echo ''
\echo '=== G.1 RESUMEN FLYWAY ======================================='

-- El "11" de abajo es el numero de migraciones EN EL MOMENTO EN QUE SE
-- ESCRIBIO este chequeo (V1-V11: dominio + facturacion + respaldos +
-- auditoria + indice de rendimiento sobre citas). Si el proyecto agrega una
-- V12 en adelante, este umbral queda desactualizado por diseno -es un piso
-- minimo, no una cuenta exacta- pero seguira reportando OK correctamente en
-- cuanto se alcance o supere ese piso.
SELECT count(*) FILTER (WHERE success)                       AS migraciones_ok,
       count(*) FILTER (WHERE NOT success)                   AS migraciones_fallidas,
       max(version)                                          AS version_maxima,
       CASE WHEN count(*) FILTER (WHERE NOT success) = 0
             AND count(*) FILTER (WHERE version IS NOT NULL) >= 11
            THEN 'FLYWAY OK (V1..V11 aplicadas)'
            ELSE 'REVISAR'
       END                                                   AS estado
FROM flyway_schema_history;

\echo ''
\echo '############################################################'
\echo '# FIN DE LA VERIFICACION'
\echo '############################################################'
