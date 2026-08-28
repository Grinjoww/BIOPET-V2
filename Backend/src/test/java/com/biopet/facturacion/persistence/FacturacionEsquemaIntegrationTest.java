package com.biopet.facturacion.persistence;

import com.biopet.facturacion.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el esquema que produce Flyway V1 -> V8 sobre una base vacia es
 * exactamente el que esperan las entidades JPA, y que las estructuras que el
 * modulo de facturacion da por sentadas existen de verdad en PostgreSQL.
 *
 * <p>La prueba mas importante de esta clase es la que no se ve: el contexto de
 * Spring arranca con {@code spring.jpa.hibernate.ddl-auto=validate}. Si
 * cualquier columna, tipo, precision o nullability de V7/V8 no coincidiese con
 * su entidad, Hibernate abortaria el arranque y TODOS los tests de esta clase
 * fallarian antes de ejecutarse.
 */
@SpringBootTest
class FacturacionEsquemaIntegrationTest extends FacturacionPostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    ApplicationContext contexto;

    // ------------------------------------------------------------------
    // Flyway
    // ------------------------------------------------------------------

    @Test
    void flywayAplicoLasOchoMigracionesSinFallos() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");

        Integer fallidas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        assertThat(fallidas).isZero();
    }

    @Test
    void lasOnceTablasDelModuloExistenYLasDeV1aV6SiguenIntactas() {
        Set<String> tablas = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class));

        assertThat(tablas).contains(
                // V7
                "emisor_fiscal", "punto_emision", "secuencial_emision",
                "tarifa_impuesto", "concepto_facturable", "datos_facturacion",
                // V8
                "facturas", "factura_detalles", "factura_pagos",
                "factura_documentos", "factura_eventos_sri",
                // V1-V4, que esta fase no toca
                "usuarios", "mascotas", "citas", "consultas", "vacunas");
    }

    @Test
    void v7yV8noSiembranNingunDatoEnProduccion() throws IOException {
        // Regla explicita de la fase: las tablas nacen vacias. Ni emisor
        // "BIOPET S.A.", ni punto 001-001, ni tarifas, ni precios.
        //
        // Se comprueba sobre el TEXTO de las migraciones y no contando filas de
        // la base: esta base la comparten las tres clases de integracion del
        // modulo (ver FacturacionPostgresTestBase), asi que un COUNT dependeria
        // del orden de ejecucion. El texto de la migracion no depende de nada.
        for (String migracion : List.of(
                "V7__facturacion_catalogos.sql", "V8__facturas.sql")) {
            String sql = Files.readString(
                    Paths.get("src/main/resources/db/migration/" + migracion));

            // Se ignoran los comentarios de linea: explican precisamente por
            // que NO se siembra nada, y mencionan las palabras clave.
            String sinComentarios = sql.lines()
                    .filter(linea -> !linea.stripLeading().startsWith("--"))
                    .collect(Collectors.joining("\n"))
                    .toUpperCase(Locale.ROOT);

            assertThat(sinComentarios)
                    .as("%s no debe insertar datos de negocio", migracion)
                    .doesNotContain("INSERT INTO")
                    .doesNotContain("COPY ");
        }
    }

    // ------------------------------------------------------------------
    // Hibernate validate + descubrimiento de entidades
    // ------------------------------------------------------------------

    @Test
    void springDescubreLasOnceEntidadesDelModulo() {
        Set<String> entidades = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        assertThat(entidades).contains(
                "EmisorFiscal", "PuntoEmision", "SecuencialEmision", "TarifaImpuesto",
                "ConceptoFacturable", "DatosFacturacion", "Factura", "FacturaDetalle",
                "FacturaPago", "FacturaDocumento", "FacturaEventoSri");
    }

    @Test
    void springDescubreLosNueveRepositoriosDelModulo() {
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.EmisorFiscalRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.PuntoEmisionRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.SecuencialEmisionRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.TarifaImpuestoRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.ConceptoFacturableRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.DatosFacturacionRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.FacturaRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.FacturaDocumentoRepository.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(
                com.biopet.facturacion.repository.FacturaEventoSriRepository.class)).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // Tipos fisicos que el modulo da por sentados
    // ------------------------------------------------------------------

    @Test
    void contenidoDelDocumentoEsByteaYNoLargeObject() {
        // Si alguien anadiese @Lob a FacturaDocumento.contenido, Hibernate
        // pasaria a tratar la columna como oid (large object). Este test mira
        // el catalogo real, no el DDL: comprueba lo que la BD tiene de verdad.
        String tipo = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'factura_documentos' AND column_name = 'contenido'",
                String.class);

        assertThat(tipo).isEqualTo("bytea");
    }

    @Test
    void mensajesDelEventoEsJsonbNativo() {
        String tipo = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'factura_eventos_sri' AND column_name = 'mensajes'",
                String.class);

        assertThat(tipo).isEqualTo("jsonb");
    }

    @Test
    void losImportesUsanLaPrecisionDelXsd() {
        // NUMERIC(14,2) para importes, NUMERIC(18,6) para cantidad y precio
        // unitario, NUMERIC(4,2) para la tarifa. Son los facts del XSD oficial
        // que ya documenta EscalasSri.
        assertThat(precisionYEscala("facturas", "importe_total")).isEqualTo("14,2");
        assertThat(precisionYEscala("facturas", "total_sin_impuestos")).isEqualTo("14,2");
        assertThat(precisionYEscala("factura_detalles", "cantidad")).isEqualTo("18,6");
        assertThat(precisionYEscala("factura_detalles", "precio_unitario")).isEqualTo("18,6");
        assertThat(precisionYEscala("factura_detalles", "base_imponible")).isEqualTo("14,2");
        assertThat(precisionYEscala("factura_detalles", "impuesto_tarifa")).isEqualTo("4,2");
        assertThat(precisionYEscala("concepto_facturable", "precio_unitario")).isEqualTo("18,6");
        assertThat(precisionYEscala("tarifa_impuesto", "tarifa")).isEqualTo("4,2");
    }

    private String precisionYEscala(String tabla, String columna) {
        return jdbc.queryForObject(
                "SELECT numeric_precision || ',' || numeric_scale FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = ?",
                String.class, tabla, columna);
    }

    // ------------------------------------------------------------------
    // Triggers e indices
    // ------------------------------------------------------------------

    @Test
    void lasTablasConActualizadoEnTienenSuTriggerReutilizandoLaFuncionDeV1() {
        List<String> tablasConTrigger = jdbc.queryForList(
                "SELECT c.relname FROM pg_trigger t "
                        + "JOIN pg_class c ON c.oid = t.tgrelid "
                        + "JOIN pg_proc p ON p.oid = t.tgfoid "
                        + "WHERE p.proname = 'set_actualizado_en' AND NOT t.tgisinternal",
                String.class);

        assertThat(tablasConTrigger).contains(
                "emisor_fiscal", "punto_emision", "secuencial_emision",
                "tarifa_impuesto", "concepto_facturable", "datos_facturacion", "facturas");

        // Las tablas append-only o sin actualizado_en NO deben tener trigger.
        assertThat(tablasConTrigger).doesNotContain(
                "factura_detalles", "factura_pagos", "factura_documentos", "factura_eventos_sri");

        // set_actualizado_en() sigue siendo la MISMA funcion de V1: existe una
        // sola definicion, V7/V8 no la redefinieron con otra firma.
        Integer definiciones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_proc WHERE proname = 'set_actualizado_en'", Integer.class);
        assertThat(definiciones).isEqualTo(1);
    }

    @Test
    void existenLosIndicesQueSostienenElModulo() {
        Set<String> indices = Set.copyOf(jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class));

        assertThat(indices).contains(
                "idx_datos_facturacion_usuario",
                "idx_datos_facturacion_predeterminado_unico",
                "idx_concepto_facturable_codigo_activo",
                "idx_concepto_facturable_tipo_activo",
                "idx_facturas_clave_acceso",
                "idx_facturas_numeracion",
                "idx_facturas_usuario_estado_fecha",
                "idx_facturas_estado_autorizacion",
                "idx_facturas_mascota",
                "idx_factura_detalles_origen",
                "idx_factura_eventos_sri_factura_creado");
    }

    @Test
    void losIndicesUnicosParcialesSonRealmenteParciales() {
        // Si perdieran el WHERE dejarian de cumplir su proposito: el de
        // datos_facturacion prohibiria mas de una identidad por usuario, y el
        // de facturas impediria tener varios borradores sin numerar.
        assertThat(definicionDe("idx_datos_facturacion_predeterminado_unico"))
                .contains("UNIQUE")
                .contains("WHERE");
        assertThat(definicionDe("idx_facturas_numeracion"))
                .contains("UNIQUE")
                .contains("WHERE");
        assertThat(definicionDe("idx_concepto_facturable_codigo_activo"))
                .contains("UNIQUE")
                .contains("WHERE");
    }

    private String definicionDe(String indice) {
        return jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                String.class, indice);
    }

    // ------------------------------------------------------------------
    // Integridad referencial conservadora
    // ------------------------------------------------------------------

    @Test
    void ningunaClaveAjenaHaciaFacturasNiDesdeUsuarioOMascotaBorraEnCascada() {
        // Regla de la fase: una factura no puede desaparecer por cascada.
        // confdeltype 'a' = NO ACTION (el defecto), 'c' = CASCADE.
        List<String> cascadas = jdbc.queryForList(
                "SELECT c.conname FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE c.contype = 'f' AND c.confdeltype = 'c' "
                        + "AND t.relname IN ('facturas', 'factura_detalles', 'factura_pagos', "
                        + "'factura_documentos', 'factura_eventos_sri', 'datos_facturacion')",
                String.class);

        assertThat(cascadas).isEmpty();
    }

    @Test
    void enumsInternosYCodigosSriDelDominioCoincidenConLosCheckDeLaBd() {
        // Los CHECK de V7/V8 fueron escritos a mano; este test los ata a los
        // enums de Java para que anadir un valor a uno sin actualizar el otro
        // se note aqui y no en produccion.
        assertThat(EstadoFactura.values()).hasSize(4);
        assertThat(EstadoRecepcionSri.values()).hasSize(2);
        assertThat(EstadoAutorizacionSri.values()).hasSize(3);
        assertThat(TipoConceptoFacturable.values()).hasSize(6);
        assertThat(TipoDocumentoFactura.values()).hasSize(4);
        assertThat(OperacionSri.values()).hasSize(2);
        assertThat(ResultadoEventoSri.values()).hasSize(7);
        assertThat(OrigenDetalleFactura.values()).hasSize(3);
        assertThat(TipoIdentificacionSri.values()).hasSize(5);

        for (EstadoFactura estado : EstadoFactura.values()) {
            assertThat(checkDe("chk_facturas_estado")).contains("'" + estado.name() + "'");
        }
        for (TipoDocumentoFactura tipo : TipoDocumentoFactura.values()) {
            assertThat(checkDe("chk_factura_documentos_tipo")).contains("'" + tipo.name() + "'");
        }
        for (ResultadoEventoSri resultado : ResultadoEventoSri.values()) {
            assertThat(checkDe("chk_factura_eventos_sri_resultado")).contains("'" + resultado.name() + "'");
        }
        for (TipoIdentificacionSri tipo : TipoIdentificacionSri.values()) {
            assertThat(checkDe("chk_datos_facturacion_tipo_identificacion")).contains("'" + tipo.codigo() + "'");
        }
    }

    private String checkDe(String constraint) {
        return jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class, constraint);
    }
}
