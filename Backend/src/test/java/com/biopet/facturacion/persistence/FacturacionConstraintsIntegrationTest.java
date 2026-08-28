package com.biopet.facturacion.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que PostgreSQL protege por si mismo las invariantes ESTRUCTURALES
 * del modulo, sin depender de que el codigo Java se acuerde de validarlas.
 *
 * <p>Se ataca la base con SQL crudo por {@link JdbcTemplate}, no a traves de las
 * entidades, a proposito: lo que se quiere demostrar es que la fila invalida se
 * rechaza aunque llegue por un camino que no pasa por Hibernate (una migracion
 * futura, un script de soporte, un bug en un servicio). Cada sentencia va en su
 * propia transaccion implicita, asi que un rechazo no arrastra a los demas
 * casos.
 *
 * <p>Lo que NO se comprueba aqui, por decision de diseno: las reglas de
 * transicion de estados y la igualdad {@code SUM(pagos) == importe_total}. No
 * son invariantes de una fila sino del documento completo en un momento
 * concreto de su ciclo de vida, y pertenecen al futuro servicio de emision. Un
 * trigger por fila las haria imposibles de construir.
 */
@SpringBootTest
class FacturacionConstraintsIntegrationTest extends FacturacionPostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private static boolean fixturesListas;

    static Long usuarioId;
    static Long emisorId;
    static Long puntoId;
    static Long facturaId;

    /**
     * Fixtures completamente ficticios y con valores unicos dentro de esta
     * clase. El RUC 9999999999999 no es un RUC real: solo cumple la forma de 13
     * digitos, que es lo unico que la BD exige.
     *
     * <p>Se crean una sola vez para toda la clase (la bandera estatica) porque
     * la base se comparte y no se limpia entre tests: reinsertarlos en cada
     * metodo chocaria con el indice unico de email de usuarios.
     */
    @BeforeEach
    void prepararFixtures() {
        if (fixturesListas) {
            return;
        }
        jdbc.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) "
                + "VALUES ('Dueno Constraints', 'constraints@biopet.test', 'x', 'ROLE_DUENO', TRUE)");
        usuarioId = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE email = 'constraints@biopet.test'", Long.class);

        jdbc.update("INSERT INTO emisor_fiscal (ruc, razon_social, direccion_matriz, "
                + "obligado_contabilidad, rimpe, activo) "
                + "VALUES ('9999999999999', 'EMISOR FICTICIO CONSTRAINTS', 'Direccion ficticia', "
                + "FALSE, FALSE, TRUE)");
        emisorId = jdbc.queryForObject(
                "SELECT id FROM emisor_fiscal WHERE ruc = '9999999999999'", Long.class);

        jdbc.update("INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                + "VALUES (?, '001', '001', TRUE)", emisorId);
        puntoId = jdbc.queryForObject(
                "SELECT id FROM punto_emision WHERE emisor_fiscal_id = ?", Long.class, emisorId);

        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                + "VALUES (?, DATE '2026-03-01', 'BORRADOR')", usuarioId);
        facturaId = jdbc.queryForObject(
                "SELECT id FROM facturas WHERE usuario_id = ? ORDER BY id LIMIT 1", Long.class, usuarioId);

        fixturesListas = true;
    }

    // ==================================================================
    // secuencial_emision: ambiente y rango
    // ==================================================================

    @Test
    void ambienteFueraDe1o2EsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                        + "VALUES (?, 3, 0)", puntoId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_secuencial_emision_ambiente");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                        + "VALUES (?, 0, 0)", puntoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ultimoSecuencialFueraDeLos9DigitosEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                        + "VALUES (?, 1, 1000000000)", puntoId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_secuencial_emision_rango");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                        + "VALUES (?, 1, -1)", puntoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unPuntoTieneUnContadorPorAmbienteYNoDosDelMismo() {

        // Punto propio para no interferir con otros tests de la clase.
        jdbc.update("INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                + "VALUES (?, '002', '001', TRUE)", emisorId);
        Long punto = jdbc.queryForObject(
                "SELECT id FROM punto_emision WHERE emisor_fiscal_id = ? AND establecimiento = '002'",
                Long.class, emisorId);

        // PRUEBAS y PRODUCCION conviven: son numeraciones independientes.
        jdbc.update("INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                + "VALUES (?, 1, 0)", punto);
        jdbc.update("INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                + "VALUES (?, 2, 0)", punto);

        Integer contadores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM secuencial_emision WHERE punto_emision_id = ?", Integer.class, punto);
        assertThat(contadores).isEqualTo(2);

        // Un segundo contador para el MISMO punto y ambiente, no.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO secuencial_emision (punto_emision_id, ambiente, ultimo_secuencial) "
                        + "VALUES (?, 1, 5)", punto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_secuencial_emision_punto_ambiente");
    }

    // ==================================================================
    // emisor_fiscal / punto_emision
    // ==================================================================

    @Test
    void rucQueNoSonExactamente13DigitosEsRechazado() {

        for (String rucInvalido : new String[]{"123", "12345678901234", "999999999999X"}) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO emisor_fiscal (ruc, razon_social, direccion_matriz, "
                            + "obligado_contabilidad, rimpe, activo) VALUES (?, 'X', 'Y', FALSE, FALSE, TRUE)",
                    rucInvalido))
                    .as("RUC invalido: %s", rucInvalido)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void puntoDeEmisionDuplicadoParaElMismoEmisorEsRechazado() {

        // (emisor, '001', '001') ya existe desde los fixtures.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                        + "VALUES (?, '001', '001', TRUE)", emisorId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_punto_emision_serie");
    }

    @Test
    void establecimientoYPuntoQueNoSonTresDigitosSonRechazados() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                        + "VALUES (?, '1', '001', TRUE)", emisorId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_punto_emision_establecimiento");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                        + "VALUES (?, '003', 'A01', TRUE)", emisorId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_punto_emision_punto");
    }

    // ==================================================================
    // datos_facturacion: predeterminado unico
    // ==================================================================

    @Test
    void unUsuarioNoPuedeTenerDosIdentidadesActivasPredeterminadas() {

        jdbc.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) "
                + "VALUES ('Dueno Predeterminado', 'predeterminado@biopet.test', 'x', 'ROLE_DUENO', TRUE)");
        Long usuario = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE email = 'predeterminado@biopet.test'", Long.class);

        jdbc.update("INSERT INTO datos_facturacion (usuario_id, tipo_identificacion, identificacion, "
                + "razon_social, predeterminado, activo) VALUES (?, '05', '0000000000', 'FICTICIO UNO', TRUE, TRUE)",
                usuario);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO datos_facturacion (usuario_id, tipo_identificacion, identificacion, "
                        + "razon_social, predeterminado, activo) "
                        + "VALUES (?, '04', '9999999999999', 'FICTICIO DOS', TRUE, TRUE)", usuario))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_datos_facturacion_predeterminado_unico");

        // Pero SI puede tener varias identidades no predeterminadas...
        assertThatCode(() -> jdbc.update(
                "INSERT INTO datos_facturacion (usuario_id, tipo_identificacion, identificacion, "
                        + "razon_social, predeterminado, activo) "
                        + "VALUES (?, '04', '9999999999999', 'FICTICIO DOS', FALSE, TRUE)", usuario))
                .doesNotThrowAnyException();

        // ...y una predeterminada dada de baja no bloquea a la nueva: el
        // indice es parcial sobre (predeterminado AND activo).
        assertThatCode(() -> jdbc.update(
                "INSERT INTO datos_facturacion (usuario_id, tipo_identificacion, identificacion, "
                        + "razon_social, predeterminado, activo) "
                        + "VALUES (?, '06', 'PASAPORTE1', 'FICTICIO TRES', TRUE, FALSE)", usuario))
                .doesNotThrowAnyException();
    }

    @Test
    void tipoDeIdentificacionFueraDeLaTabla6EsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO datos_facturacion (usuario_id, tipo_identificacion, identificacion, "
                        + "razon_social, predeterminado, activo) "
                        + "VALUES (?, '99', '0000000000', 'FICTICIO', FALSE, TRUE)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_datos_facturacion_tipo_identificacion");
    }

    // ==================================================================
    // concepto_facturable
    // ==================================================================

    @Test
    void precioNegativoDeUnConceptoEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concepto_facturable (codigo, descripcion, tipo, precio_unitario, "
                        + "codigo_impuesto, codigo_porcentaje, activo) "
                        + "VALUES ('NEG-001', 'Ficticio', 'OTRO', -0.000001, '2', '0', TRUE)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_concepto_facturable_precio");
    }

    @Test
    void elCodigoEsUnicoSoloEntreConceptosActivos() {

        jdbc.update("INSERT INTO concepto_facturable (codigo, descripcion, tipo, precio_unitario, "
                + "codigo_impuesto, codigo_porcentaje, activo) "
                + "VALUES ('DUP-001', 'Ficticio activo', 'OTRO', 1.000000, '2', '0', TRUE)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concepto_facturable (codigo, descripcion, tipo, precio_unitario, "
                        + "codigo_impuesto, codigo_porcentaje, activo) "
                        + "VALUES ('DUP-001', 'Otro ficticio activo', 'OTRO', 2.000000, '2', '0', TRUE)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_concepto_facturable_codigo_activo");

        // Reutilizar el codigo en una fila INACTIVA si vale: el historico se
        // conserva y el codigo queda libre tras dar de baja el concepto.
        assertThatCode(() -> jdbc.update(
                "INSERT INTO concepto_facturable (codigo, descripcion, tipo, precio_unitario, "
                        + "codigo_impuesto, codigo_porcentaje, activo) "
                        + "VALUES ('DUP-001', 'Ficticio dado de baja', 'OTRO', 3.000000, '2', '0', FALSE)"))
                .doesNotThrowAnyException();
    }

    @Test
    void tipoDeConceptoFueraDelCatalogoInternoEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concepto_facturable (codigo, descripcion, tipo, precio_unitario, "
                        + "codigo_impuesto, codigo_porcentaje, activo) "
                        + "VALUES ('TIPO-001', 'Ficticio', 'CIRUGIA_ESPACIAL', 1.0, '2', '0', TRUE)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_concepto_facturable_tipo");
    }

    // ==================================================================
    // facturas: numeracion
    // ==================================================================

    @Test
    void claveDeAccesoConLongitudOFormatoInvalidoEsRechazada() {

        // 48 digitos (le falta el verificador), 50 digitos, y 49 caracteres
        // con una letra.
        String cuarentaYOcho = "1".repeat(48);
        String cincuenta = "1".repeat(50);
        String conLetra = "1".repeat(48) + "X";

        for (String clave : new String[]{cuarentaYOcho, cincuenta, conLetra}) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO facturas (usuario_id, fecha_emision, estado, clave_acceso) "
                            + "VALUES (?, DATE '2026-03-01', 'EMITIDA', ?)", usuarioId, clave))
                    .as("clave invalida de longitud %d", clave.length())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void claveDeAccesoDuplicadaEsRechazada() {

        String clave = "2".repeat(49);
        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado, clave_acceso) "
                + "VALUES (?, DATE '2026-03-01', 'EMITIDA', ?)", usuarioId, clave);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, clave_acceso) "
                        + "VALUES (?, DATE '2026-03-02', 'EMITIDA', ?)", usuarioId, clave))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_facturas_clave_acceso");
    }

    @Test
    void variosBorradoresSinClaveDeAccesoConvivenSinChocar() {

        // El indice unico sobre clave_acceso NO puede impedir tener N
        // borradores: en PostgreSQL los NULL son distintos entre si.
        assertThatCode(() -> {
            for (int i = 0; i < 3; i++) {
                jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                        + "VALUES (?, DATE '2026-03-05', 'BORRADOR')", usuarioId);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void elMismoSecuencialSeRepiteEntreAmbientesPeroNoDentroDelMismo() {

        jdbc.update("INSERT INTO punto_emision (emisor_fiscal_id, establecimiento, punto_emision, activo) "
                + "VALUES (?, '003', '001', TRUE)", emisorId);
        Long punto = jdbc.queryForObject(
                "SELECT id FROM punto_emision WHERE emisor_fiscal_id = ? AND establecimiento = '003'",
                Long.class, emisorId);

        // PRUEBAS 001-001-000000001
        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado, punto_emision_id, "
                + "ambiente, establecimiento, punto_emision, secuencial) "
                + "VALUES (?, DATE '2026-03-01', 'EMITIDA', ?, 1, '003', '001', 1)", usuarioId, punto);

        // PRODUCCION 001-001-000000001 -> PERMITIDO, es otra numeracion legal.
        assertThatCode(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, punto_emision_id, "
                        + "ambiente, establecimiento, punto_emision, secuencial) "
                        + "VALUES (?, DATE '2026-03-01', 'EMITIDA', ?, 2, '003', '001', 1)", usuarioId, punto))
                .doesNotThrowAnyException();

        // Repetir el secuencial DENTRO del mismo punto y ambiente -> RECHAZADO.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, punto_emision_id, "
                        + "ambiente, establecimiento, punto_emision, secuencial) "
                        + "VALUES (?, DATE '2026-03-09', 'EMITIDA', ?, 1, '003', '001', 1)", usuarioId, punto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_facturas_numeracion");
    }

    @Test
    void secuencialFueraDeLos9DigitosYAmbienteInvalidoSonRechazados() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, secuencial) "
                        + "VALUES (?, DATE '2026-03-01', 'EMITIDA', 1000000000)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_facturas_secuencial");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, ambiente) "
                        + "VALUES (?, DATE '2026-03-01', 'EMITIDA', 9)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_facturas_ambiente");
    }

    @Test
    void estadoDeFacturaFueraDeLosCuatroValoresEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                        + "VALUES (?, DATE '2026-03-01', 'ANULADA')", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_facturas_estado");
    }

    @Test
    void totalesNegativosEnLaCabeceraSonRechazados() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, importe_total) "
                        + "VALUES (?, DATE '2026-03-01', 'BORRADOR', -0.01)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_facturas_importe_total");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, total_impuestos) "
                        + "VALUES (?, DATE '2026-03-01', 'BORRADOR', -1)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_facturas_total_impuestos");
    }

    // ==================================================================
    // factura_detalles
    // ==================================================================

    @Test
    void cantidadDescuentoBaseEImpuestoNegativosSonRechazados() {

        assertThatThrownBy(() -> insertarDetalle(901, "-1.000000", "1.000000", "0.00", "0.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_cantidad");

        assertThatThrownBy(() -> insertarDetalle(902, "1.000000", "1.000000", "-0.01", "0.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_descuento");

        assertThatThrownBy(() -> insertarDetalle(903, "1.000000", "-1.000000", "0.00", "0.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_precio_unitario");

        assertThatThrownBy(() -> insertarDetalle(904, "1.000000", "1.000000", "0.00", "-1.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_base");

        assertThatThrownBy(() -> insertarDetalle(905, "1.000000", "1.000000", "0.00", "0.00", "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_impuesto_valor");
    }

    private void insertarDetalle(int linea, String cantidad, String precio,
                                 String descuento, String base, String impuestoValor) {
        jdbc.update("INSERT INTO factura_detalles (factura_id, linea, codigo_principal, descripcion, "
                        + "cantidad, precio_unitario, descuento, precio_total_sin_impuesto, "
                        + "impuesto_codigo, impuesto_codigo_porcentaje, impuesto_tarifa, "
                        + "base_imponible, impuesto_valor) "
                        + "VALUES (?, ?, 'COD', 'Ficticio', ?::numeric, ?::numeric, ?::numeric, 0.00, "
                        + "'2', '0', 0.00, ?::numeric, ?::numeric)",
                facturaId, linea, cantidad, precio, descuento, base, impuestoValor);
    }

    @Test
    void dosLineasConElMismoNumeroEnLaMismaFacturaSonRechazadas() {

        insertarDetalle(1, "1.000000", "10.000000", "0.00", "10.00", "0.00");

        assertThatThrownBy(() -> insertarDetalle(1, "2.000000", "20.000000", "0.00", "40.00", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_factura_detalles_linea");
    }

    @Test
    void origenClinicoFueraDeConsultaVacunaCitaEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_detalles (factura_id, linea, codigo_principal, descripcion, "
                        + "cantidad, precio_unitario, precio_total_sin_impuesto, impuesto_codigo, "
                        + "impuesto_codigo_porcentaje, impuesto_tarifa, base_imponible, impuesto_valor, "
                        + "origen_tipo, origen_id) "
                        + "VALUES (?, 950, 'COD', 'Ficticio', 1, 1, 1, '2', '0', 0, 1, 0, 'HOSPITALIZACION', 1)",
                facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_detalles_origen_tipo");
    }

    @Test
    void unMismoOrigenClinicoPuedeAparecerEnVariasFacturas() {

        // Regla explicita: NO se prohibe por constraint. Refacturar o emitir
        // una futura nota de credito sobre la misma consulta debe ser posible.
        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                + "VALUES (?, DATE '2026-04-01', 'BORRADOR')", usuarioId);
        Long otraFactura = jdbc.queryForObject(
                "SELECT MAX(id) FROM facturas WHERE usuario_id = ?", Long.class, usuarioId);

        assertThatCode(() -> {
            insertarDetalleConOrigen(facturaId, 960, 4242L);
            insertarDetalleConOrigen(otraFactura, 1, 4242L);
        }).doesNotThrowAnyException();

        Integer apariciones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_detalles WHERE origen_tipo = 'CONSULTA' AND origen_id = 4242",
                Integer.class);
        assertThat(apariciones).isEqualTo(2);
    }

    private void insertarDetalleConOrigen(Long factura, int linea, Long origenId) {
        jdbc.update("INSERT INTO factura_detalles (factura_id, linea, codigo_principal, descripcion, "
                        + "cantidad, precio_unitario, precio_total_sin_impuesto, impuesto_codigo, "
                        + "impuesto_codigo_porcentaje, impuesto_tarifa, base_imponible, impuesto_valor, "
                        + "origen_tipo, origen_id) "
                        + "VALUES (?, ?, 'COD', 'Ficticio', 1, 1, 1, '2', '0', 0, 1, 0, 'CONSULTA', ?)",
                factura, linea, origenId);
    }

    // ==================================================================
    // factura_pagos
    // ==================================================================

    @Test
    void formaDePagoFueraDeLaTabla24EsRechazada() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_pagos (factura_id, forma_pago, total) VALUES (?, '99', 10.00)",
                facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_pagos_forma_pago");

        // "02" pasa el patron del XSD pero NO esta en el catalogo: debe caer.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_pagos (factura_id, forma_pago, total) VALUES (?, '02', 10.00)",
                facturaId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Los ocho codigos vigentes si entran.
        for (String codigo : new String[]{"01", "15", "16", "17", "18", "19", "20", "21"}) {
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO factura_pagos (factura_id, forma_pago, total) VALUES (?, ?, 1.00)",
                    facturaId, codigo))
                    .as("forma de pago vigente: %s", codigo)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void totalNegativoDeUnPagoEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_pagos (factura_id, forma_pago, total) VALUES (?, '01', -1.00)",
                facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_pagos_total");
    }

    // ==================================================================
    // factura_documentos / factura_eventos_sri
    // ==================================================================

    @Test
    void sha256QueNoSon64HexadecimalesEsRechazado() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_documentos (factura_id, tipo, contenido, sha256, bytes) "
                        + "VALUES (?, 'XML_GENERADO', '\\x00'::bytea, 'no-es-un-sha', 1)", facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_documentos_sha256");

        // Mayusculas tampoco: la forma canonica que guarda el modulo es en
        // minuscula, y admitir ambas permitiria duplicados que solo difieren
        // en el caso.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_documentos (factura_id, tipo, contenido, sha256, bytes) "
                        + "VALUES (?, 'XML_GENERADO', '\\x00'::bytea, ?, 1)", facturaId, "A".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noPuedeHaberDosDocumentosDelMismoTipoParaUnaFactura() {

        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                + "VALUES (?, DATE '2026-05-01', 'EMITIDA')", usuarioId);
        Long factura = jdbc.queryForObject(
                "SELECT MAX(id) FROM facturas WHERE usuario_id = ?", Long.class, usuarioId);
        String sha = "a".repeat(64);

        jdbc.update("INSERT INTO factura_documentos (factura_id, tipo, contenido, sha256, bytes) "
                + "VALUES (?, 'XML_FIRMADO', '\\x0102'::bytea, ?, 2)", factura, sha);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_documentos (factura_id, tipo, contenido, sha256, bytes) "
                        + "VALUES (?, 'XML_FIRMADO', '\\x0304'::bytea, ?, 2)", factura, sha))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_factura_documentos_tipo");

        // Otro TIPO para la misma factura si entra.
        assertThatCode(() -> jdbc.update(
                "INSERT INTO factura_documentos (factura_id, tipo, contenido, sha256, bytes) "
                        + "VALUES (?, 'RIDE_PDF', '\\x0304'::bytea, ?, 2)", factura, sha))
                .doesNotThrowAnyException();
    }

    @Test
    void operacionResultadoEIntentoInvalidosEnLaBitacoraSonRechazados() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_eventos_sri (factura_id, operacion, resultado, intento) "
                        + "VALUES (?, 'CONSULTA_MAGICA', 'AUT', 1)", facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_eventos_sri_operacion");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_eventos_sri (factura_id, operacion, resultado, intento) "
                        + "VALUES (?, 'RECEPCION', 'QUIZAS', 1)", facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_eventos_sri_resultado");

        // El primer intento es el 1, no el 0.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO factura_eventos_sri (factura_id, operacion, resultado, intento) "
                        + "VALUES (?, 'RECEPCION', 'RECIBIDA', 0)", facturaId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_factura_eventos_sri_intento");
    }

    // ==================================================================
    // Integridad referencial
    // ==================================================================

    @Test
    void borrarUnUsuarioConFacturasFallaEnLugarDeArrastrarlas() {

        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuarios WHERE id = ?", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);

        Integer siguenAhi = jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE usuario_id = ?", Integer.class, usuarioId);
        assertThat(siguenAhi).isPositive();
    }

    @Test
    void borrarUnaFacturaConLineasFallaPorqueNoHayCascada() {

        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado) "
                + "VALUES (?, DATE '2026-06-01', 'EMITIDA')", usuarioId);
        Long factura = jdbc.queryForObject(
                "SELECT MAX(id) FROM facturas WHERE usuario_id = ?", Long.class, usuarioId);
        insertarDetalleConOrigen(factura, 1, 777L);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM facturas WHERE id = ?", factura))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE id = ?", Integer.class, factura)).isEqualTo(1);
    }

    @Test
    void unaFacturaNoPuedeApuntarAUnPuntoDeEmisionInexistente() {

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO facturas (usuario_id, fecha_emision, estado, punto_emision_id) "
                        + "VALUES (?, DATE '2026-03-01', 'BORRADOR', 987654321)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_facturas_punto_emision");
    }
}
