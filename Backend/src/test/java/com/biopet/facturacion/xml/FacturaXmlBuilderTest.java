package com.biopet.facturacion.xml;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Construccion del XML, sin Spring y sin base de datos: el builder solo depende
 * de la factura que recibe.
 *
 * <p>Que estas pruebas puedan escribirse asi ya es una comprobacion en si misma:
 * si el builder necesitase repositories para completar algun dato, no habria
 * forma de instanciarlo con un {@code new}.
 */
class FacturaXmlBuilderTest {

    private final FacturaXmlBuilder builder = new FacturaXmlBuilder(new CalculoFacturaService());
    private final FacturaXsdValidator validator = new FacturaXsdValidator();

    private String xml(Factura factura) {
        return new String(builder.construir(factura), StandardCharsets.UTF_8);
    }

    // ==================================================================
    // Estructura
    // ==================================================================

    @Test
    void elDocumentoTieneLaCabeceraYLosAtributosQueExigeElSri() {
        String xml = xml(FacturaXmlFixture.facturaEmitida());

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<factura id=\"comprobante\" version=\"2.1.0\">");
        // Sin firma todavia: este documento es el input exacto de la fase XAdES.
        assertThat(xml).doesNotContain("Signature").doesNotContain("ds:");
        // Bloques en el orden del XSD.
        assertThat(xml.indexOf("<infoTributaria>"))
                .isLessThan(xml.indexOf("<infoFactura>"));
        assertThat(xml.indexOf("<infoFactura>"))
                .isLessThan(xml.indexOf("<detalles>"));
        assertThat(xml.indexOf("<detalles>"))
                .isLessThan(xml.indexOf("<infoAdicional>"));
    }

    @Test
    void infoTributariaLlevaLosSnapshotsDelEmisorYLaNumeracion() {
        String xml = xml(FacturaXmlFixture.facturaEmitida());

        assertThat(xml).contains("<ambiente>1</ambiente>");
        assertThat(xml).contains("<tipoEmision>1</tipoEmision>");
        assertThat(xml).contains("<razonSocial>CLINICA VETERINARIA FICTICIA S.A.</razonSocial>");
        assertThat(xml).contains("<nombreComercial>BIOPET</nombreComercial>");
        assertThat(xml).contains("<ruc>" + FacturaXmlFixture.RUC_EMISOR + "</ruc>");
        assertThat(xml).contains("<claveAcceso>" + FacturaXmlFixture.claveAcceso() + "</claveAcceso>");
        assertThat(xml).contains("<codDoc>01</codDoc>");
        assertThat(xml).contains("<estab>001</estab>");
        assertThat(xml).contains("<ptoEmi>002</ptoEmi>");
        // 9 digitos exactos, con ceros a la izquierda.
        assertThat(xml).contains("<secuencial>000000042</secuencial>");
        assertThat(xml).contains("<dirMatriz>Av. Matriz Ficticia 100</dirMatriz>");
    }

    @Test
    void infoFacturaLlevaFechaFormateadaCompradorYTotales() {
        String xml = xml(FacturaXmlFixture.facturaEmitida());

        assertThat(xml).contains("<fechaEmision>15/09/2026</fechaEmision>");
        assertThat(xml).contains("<dirEstablecimiento>Sucursal Ficticia Norte</dirEstablecimiento>");
        assertThat(xml).contains("<contribuyenteEspecial>12345</contribuyenteEspecial>");
        assertThat(xml).contains("<obligadoContabilidad>SI</obligadoContabilidad>");
        assertThat(xml).contains("<tipoIdentificacionComprador>05</tipoIdentificacionComprador>");
        assertThat(xml).contains("<razonSocialComprador>MARIA LOPEZ</razonSocialComprador>");
        assertThat(xml).contains("<identificacionComprador>0102030405</identificacionComprador>");
        assertThat(xml).contains("<totalSinImpuestos>40.00</totalSinImpuestos>");
        assertThat(xml).contains("<totalDescuento>0.00</totalDescuento>");
        assertThat(xml).contains("<importeTotal>46.00</importeTotal>");
        assertThat(xml).contains("<moneda>DOLAR</moneda>");
        assertThat(xml).contains("<formaPago>16</formaPago>");
        assertThat(xml).contains("<total>46.00</total>");
    }

    @Test
    void elDetalleConservaLasEscalasDelXsd() {
        String xml = xml(FacturaXmlFixture.facturaEmitida());

        assertThat(xml).contains("<codigoPrincipal>SRV-001</codigoPrincipal>");
        assertThat(xml).contains("<descripcion>Consulta veterinaria ficticia</descripcion>");
        // cantidad y precio con 6 decimales; importes con 2; tarifa con 2.
        assertThat(xml).contains("<cantidad>2.000000</cantidad>");
        assertThat(xml).contains("<precioUnitario>20.000000</precioUnitario>");
        assertThat(xml).contains("<descuento>0.00</descuento>");
        assertThat(xml).contains("<precioTotalSinImpuesto>40.00</precioTotalSinImpuesto>");
        assertThat(xml).contains("<tarifa>15.00</tarifa>");
        // Orden dentro del impuesto del DETALLE: tarifa antes que baseImponible.
        String impuestoDetalle = xml.substring(xml.indexOf("<detalles>"));
        assertThat(impuestoDetalle.indexOf("<tarifa>"))
                .isLessThan(impuestoDetalle.indexOf("<baseImponible>"));
    }

    @Test
    void enTotalConImpuestosLaBaseVaAntesDeLaTarifa() {
        // El XSD invierte el orden entre el impuesto del detalle y el del
        // resumen. Es una trampa facil de pasar por alto y el validador la
        // castiga, asi que se fija aqui explicitamente.
        String xml = xml(FacturaXmlFixture.facturaEmitida());
        String resumen = xml.substring(xml.indexOf("<totalConImpuestos>"),
                xml.indexOf("</totalConImpuestos>"));

        assertThat(resumen.indexOf("<baseImponible>"))
                .isLessThan(resumen.indexOf("<tarifa>"));
        assertThat(resumen).contains("<codigo>2</codigo>");
        assertThat(resumen).contains("<codigoPorcentaje>4</codigoPorcentaje>");
        assertThat(resumen).contains("<valor>6.00</valor>");
    }

    @Test
    void variasLineasConElMismoImpuestoSeAgrupanEnUnSoloTotalImpuesto() {
        Factura factura = FacturaXmlFixture.cabecera();
        factura.agregarDetalle(FacturaXmlFixture.detalle(1, "A", "Linea A",
                "1.000000", "10.000000", "0.00", "10.00", "4", "15.00", "10.00", "1.50"));
        factura.agregarDetalle(FacturaXmlFixture.detalle(2, "B", "Linea B",
                "1.000000", "30.000000", "0.00", "30.00", "4", "15.00", "30.00", "4.50"));
        factura.agregarPago(FacturaXmlFixture.pago("46.00"));

        String xml = xml(factura);
        String resumen = xml.substring(xml.indexOf("<totalConImpuestos>"),
                xml.indexOf("</totalConImpuestos>"));

        assertThat(resumen.split("<totalImpuesto>", -1)).hasSize(2); // un solo grupo
        assertThat(resumen).contains("<baseImponible>40.00</baseImponible>");
        assertThat(resumen).contains("<valor>6.00</valor>");
        assertThatCode(() -> validator.validar(builder.construir(factura)))
                .doesNotThrowAnyException();
    }

    @Test
    void dosTarifasDistintasProducenDosGruposYSiguenValidando() {
        Factura factura = FacturaXmlFixture.cabecera();
        factura.agregarDetalle(FacturaXmlFixture.detalle(1, "GRAV", "Servicio gravado",
                "1.000000", "40.000000", "0.00", "40.00", "4", "15.00", "40.00", "6.00"));
        factura.agregarDetalle(FacturaXmlFixture.detalle(2, "EXEN", "Producto tarifa cero",
                "1.000000", "10.000000", "0.00", "10.00", "0", "0.00", "10.00", "0.00"));
        factura.setTotalSinImpuestos(new BigDecimal("50.00"));
        factura.setTotalImpuestos(new BigDecimal("6.00"));
        factura.setImporteTotal(new BigDecimal("56.00"));
        factura.agregarPago(FacturaXmlFixture.pago("56.00"));

        String xml = xml(factura);
        String resumen = xml.substring(xml.indexOf("<totalConImpuestos>"),
                xml.indexOf("</totalConImpuestos>"));

        assertThat(resumen.split("<totalImpuesto>", -1)).hasSize(3); // dos grupos
        assertThatCode(() -> validator.validar(builder.construir(factura)))
                .doesNotThrowAnyException();
    }

    @Test
    void unEmisorRimpeEmiteLaLeyendaExactaDelCatalogo() {
        Factura factura = FacturaXmlFixture.facturaEmitida();
        factura.setEmisorRimpe(true);

        String xml = xml(factura);

        // El XSD no admite texto libre aqui: es un patron con esa cadena exacta.
        assertThat(xml).contains("<contribuyenteRimpe>CONTRIBUYENTE RÉGIMEN RIMPE</contribuyenteRimpe>");
        assertThatCode(() -> validator.validar(builder.construir(factura)))
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // UTF-8 y escapado
    // ==================================================================

    @Test
    void tildesEnesYCaracteresReservadosViajanBienSinEscaparAMano() {
        Factura factura = FacturaXmlFixture.cabecera();
        factura.setCompradorRazonSocial("Muñoz & Hijos <Cía. Ltda.> \"El Ñandú\"");
        factura.setEmisorRazonSocial("Clínica Veterinaria Añañá S.A.");
        factura.agregarDetalle(FacturaXmlFixture.detalle(1, "A&B", "Vacunación múltiple <triple> \"felina\"",
                "2.000000", "20.000000", "0.00", "40.00", "4", "15.00", "40.00", "6.00"));
        factura.agregarPago(FacturaXmlFixture.pago("46.00"));

        byte[] bytes = builder.construir(factura);
        String xml = new String(bytes, StandardCharsets.UTF_8);

        // Los caracteres reservados van escapados...
        assertThat(xml).contains("Muñoz &amp; Hijos &lt;Cía. Ltda.&gt;");
        assertThat(xml).contains("A&amp;B");
        assertThat(xml).contains("Vacunación múltiple &lt;triple&gt;");
        // ...y los acentos viajan como UTF-8 real, no como entidades numericas.
        assertThat(xml).contains("Añañá");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Ñandú");
        assertThat(xml).doesNotContain("&#");

        // Y el resultado sigue siendo valido para el SRI.
        assertThatCode(() -> validator.validar(bytes)).doesNotThrowAnyException();
    }

    // ==================================================================
    // Coherencia de snapshots
    // ==================================================================

    @Test
    void unaFacturaEnBorradorNoProduceXml() {
        Factura factura = FacturaXmlFixture.facturaEmitida();
        factura.setEstado(EstadoFactura.BORRADOR);

        assertThatThrownBy(() -> builder.construir(factura))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("BORRADOR");
    }

    @Test
    void unaFacturaSinDetallesNoProduceXml() {
        assertThatThrownBy(() -> builder.construir(FacturaXmlFixture.cabecera()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("no tiene detalles");
    }

    @Test
    void siLosTotalesNoCuadranConLasLineasNoSeGeneraXml() {
        Factura factura = FacturaXmlFixture.facturaEmitida();
        // Alguien tocó la cabecera por SQL y dejó un importe que no corresponde.
        factura.setImporteTotal(new BigDecimal("99.99"));

        assertThatThrownBy(() -> builder.construir(factura))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("importeTotal")
                .hasMessageContaining("no cuadra");
    }

    @Test
    void siLosPagosNoCubrenElTotalNoSeGeneraXml() {
        Factura factura = FacturaXmlFixture.cabecera();
        factura.agregarDetalle(FacturaXmlFixture.detalle(1, "A", "Linea",
                "2.000000", "20.000000", "0.00", "40.00", "4", "15.00", "40.00", "6.00"));
        factura.agregarPago(FacturaXmlFixture.pago("10.00"));

        assertThatThrownBy(() -> builder.construir(factura))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("pagos");
    }

    @Test
    void faltarUnSnapshotObligatorioDelXsdSeDenunciaConElNombreDelCampo() {
        Factura sinClave = FacturaXmlFixture.facturaEmitida();
        sinClave.setClaveAcceso(null);
        assertThatThrownBy(() -> builder.construir(sinClave))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("claveAcceso");

        Factura sinComprador = FacturaXmlFixture.facturaEmitida();
        sinComprador.setCompradorRazonSocial("  ");
        assertThatThrownBy(() -> builder.construir(sinComprador))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("compradorRazonSocial");

        Factura sinRuc = FacturaXmlFixture.facturaEmitida();
        sinRuc.setEmisorRuc(null);
        assertThatThrownBy(() -> builder.construir(sinRuc))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("emisorRuc");
    }

    // ==================================================================
    // Opcionales
    // ==================================================================

    @Test
    void losBloquesOpcionalesSeOmitenEnLugarDeIrVacios() {
        Factura factura = FacturaXmlFixture.facturaEmitida();
        factura.setEmisorNombreComercial(null);
        factura.setEmisorDireccionEstablecimiento(null);
        factura.setEmisorContribuyenteEspecial(null);
        factura.setCompradorDireccion(null);
        factura.setCompradorEmail(null);
        factura.setCompradorTelefono(null);

        byte[] bytes = builder.construir(factura);
        String xml = new String(bytes, StandardCharsets.UTF_8);

        assertThat(xml).doesNotContain("<nombreComercial>")
                .doesNotContain("<dirEstablecimiento>")
                .doesNotContain("<contribuyenteEspecial>")
                .doesNotContain("<direccionComprador>")
                // Sin email ni telefono no hay nada que poner: el bloque entero
                // desaparece en lugar de quedar como <infoAdicional/>, que el
                // XSD rechazaria por exigir al menos un campoAdicional.
                .doesNotContain("infoAdicional");

        assertThatCode(() -> validator.validar(bytes)).doesNotThrowAnyException();
    }

    @Test
    void elEmailYElTelefonoDelCompradorViajanComoCampoAdicional() {
        String xml = xml(FacturaXmlFixture.facturaEmitida());

        assertThat(xml).contains("<campoAdicional nombre=\"email\">maria@ejemplo.test</campoAdicional>");
        assertThat(xml).contains("<campoAdicional nombre=\"telefono\">0999999999</campoAdicional>");
    }

    @Test
    void elXmlGeneradoValidaContraElXsdOficial() {
        assertThatCode(() -> validator.validar(builder.construir(FacturaXmlFixture.facturaEmitida())))
                .doesNotThrowAnyException();
    }
}
