package com.biopet.facturacion.xml;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del validador: que acepte lo valido, que RECHACE lo invalido y que no
 * se deje engañar por un XML hostil.
 *
 * <p>Un validador que nunca rechaza nada pasa igual de verde que uno que
 * funciona. Por eso la mitad de esta clase son casos negativos: se parte del XML
 * bueno que produce el builder y se rompe una cosa cada vez.
 *
 * <p>Ninguna prueba hace peticiones de red. Los casos de XXE y DTD externo usan
 * URLs ficticias que, si el parser intentara resolverlas, harian fallar la
 * prueba por timeout en lugar de por rechazo, que es justo la señal contraria a
 * la que buscamos.
 */
class FacturaXsdValidatorTest {

    private final FacturaXsdValidator validator = new FacturaXsdValidator();
    private final FacturaXmlBuilder builder = new FacturaXmlBuilder(new CalculoFacturaService());

    private String xmlValido() {
        return new String(builder.construir(FacturaXmlFixture.facturaEmitida()), StandardCharsets.UTF_8);
    }

    private void validar(String xml) {
        validator.validar(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ==================================================================
    // Caso positivo
    // ==================================================================

    @Test
    void elXmlBienFormadoDelBuilderValida() {
        assertThatCode(() -> validar(xmlValido())).doesNotThrowAnyException();
    }

    @Test
    void elEsquemaSeCompilaSinSalirAInternet() {
        // Si la compilacion del XSD dependiese de la red, este constructor
        // fallaria (o tardaria) en un entorno aislado. Se instancia otra vez a
        // proposito: el esquema se compila en el constructor.
        assertThatCode(FacturaXsdValidator::new).doesNotThrowAnyException();
    }

    // ==================================================================
    // Casos negativos del XSD
    // ==================================================================

    @Test
    void unaVersionDistintaDeLaEsperadaSeDetecta() {
        // El XSD declara el atributo version sin restriccion de valor, asi que
        // no lo rechaza por si mismo: quien fija la version es el builder. Se
        // comprueba que el builder emite 2.1.0 y no otra cosa.
        assertThat(xmlValido()).contains("version=\"2.1.0\"");
        assertThat(FacturaXmlBuilder.VERSION).isEqualTo("2.1.0");

        // El id SI esta restringido por enumeracion: "comprobante" y nada mas.
        String idInvalido = xmlValido().replace("id=\"comprobante\"", "id=\"otro\"");
        assertThatThrownBy(() -> validar(idInvalido))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void faltarUnElementoObligatorioSeRechaza() {
        String sinRuc = xmlValido().replace(
                "<ruc>" + FacturaXmlFixture.RUC_EMISOR + "</ruc>", "");
        assertThatThrownBy(() -> validar(sinRuc))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("esquema oficial");

        String sinImporteTotal = xmlValido().replace(
                "<importeTotal>46.00</importeTotal>", "");
        assertThatThrownBy(() -> validar(sinImporteTotal))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unaClaveDeAccesoQueNoSon49DigitosSeRechaza() {
        String clave = FacturaXmlFixture.claveAcceso();

        String corta = xmlValido().replace(clave, clave.substring(0, 48));
        assertThatThrownBy(() -> validar(corta))
                .isInstanceOf(FacturaXmlInvalidoException.class);

        String conLetra = xmlValido().replace(clave, clave.substring(0, 48) + "X");
        assertThatThrownBy(() -> validar(conLetra))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unSecuencialQueNoSon9DigitosSeRechaza() {
        String corto = xmlValido().replace(
                "<secuencial>000000042</secuencial>", "<secuencial>42</secuencial>");
        assertThatThrownBy(() -> validar(corto))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unRucQueNoTerminaEn001SeRechaza() {
        // El XSD es MAS estricto que el CHECK de 13 digitos de la tabla:
        // exige el patron [0-9]{10}001.
        String malFormado = xmlValido().replace(
                "<ruc>" + FacturaXmlFixture.RUC_EMISOR + "</ruc>", "<ruc>0999999999123</ruc>");
        assertThatThrownBy(() -> validar(malFormado))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unaFechaMalFormateadaSeRechaza() {
        // El XSD exige dd/MM/yyyy, no ISO.
        String iso = xmlValido().replace(
                "<fechaEmision>15/09/2026</fechaEmision>",
                "<fechaEmision>2026-09-15</fechaEmision>");
        assertThatThrownBy(() -> validar(iso))
                .isInstanceOf(FacturaXmlInvalidoException.class);

        String mesInvalido = xmlValido().replace(
                "<fechaEmision>15/09/2026</fechaEmision>",
                "<fechaEmision>15/13/2026</fechaEmision>");
        assertThatThrownBy(() -> validar(mesInvalido))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void elOrdenDeLosElementosImporta() {
        // infoFactura antes que infoTributaria: el XSD usa xsd:sequence.
        String xml = xmlValido();
        String infoTributaria = xml.substring(xml.indexOf("<infoTributaria>"),
                xml.indexOf("</infoTributaria>") + "</infoTributaria>".length());
        String infoFactura = xml.substring(xml.indexOf("<infoFactura>"),
                xml.indexOf("</infoFactura>") + "</infoFactura>".length());
        String invertido = xml.replace(infoTributaria + infoFactura, infoFactura + infoTributaria);

        assertThatThrownBy(() -> validar(invertido))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void enElResumenDeImpuestosInvertirBaseYTarifaSeRechaza() {
        // Es la trampa del XSD: en el detalle el orden es tarifa->baseImponible
        // y en totalConImpuestos es baseImponible->tarifa.
        String invertido = xmlValido().replace(
                "<baseImponible>40.00</baseImponible><tarifa>15.00</tarifa><valor>6.00</valor>",
                "<tarifa>15.00</tarifa><baseImponible>40.00</baseImponible><valor>6.00</valor>");

        assertThatThrownBy(() -> validar(invertido))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void masDecimalesDeLosQuePermiteElXsdSeRechazan() {
        // importeTotal admite fractionDigits=2.
        String tresDecimales = xmlValido().replace(
                "<importeTotal>46.00</importeTotal>", "<importeTotal>46.001</importeTotal>");
        assertThatThrownBy(() -> validar(tresDecimales))
                .isInstanceOf(FacturaXmlInvalidoException.class);

        // cantidad admite fractionDigits=6.
        String sieteDecimales = xmlValido().replace(
                "<cantidad>2.000000</cantidad>", "<cantidad>2.0000001</cantidad>");
        assertThatThrownBy(() -> validar(sieteDecimales))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unaFormaDePagoFueraDelPatronSeRechaza() {
        String invalida = xmlValido().replace(
                "<formaPago>16</formaPago>", "<formaPago>99</formaPago>");
        assertThatThrownBy(() -> validar(invalida))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unObligadoContabilidadFueraDeSiNoSeRechaza() {
        String invalido = xmlValido().replace(
                "<obligadoContabilidad>SI</obligadoContabilidad>",
                "<obligadoContabilidad>TALVEZ</obligadoContabilidad>");
        assertThatThrownBy(() -> validar(invalido))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unXmlVacioOMalFormadoSeRechaza() {
        assertThatThrownBy(() -> validator.validar(null))
                .isInstanceOf(FacturaXmlInvalidoException.class);
        assertThatThrownBy(() -> validator.validar(new byte[0]))
                .isInstanceOf(FacturaXmlInvalidoException.class);
        assertThatThrownBy(() -> validar("<factura>sin cerrar"))
                .isInstanceOf(FacturaXmlInvalidoException.class);
        assertThatThrownBy(() -> validar("no es xml en absoluto"))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    // ==================================================================
    // Seguridad XML
    // ==================================================================

    @Test
    void cualquierDoctypeSeRechazaDeRaiz() {
        String conDoctype = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE factura>"
                + xmlValido().substring(xmlValido().indexOf("<factura"));

        assertThatThrownBy(() -> validar(conDoctype))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void xxeConFileNoLeeElDisco() {
        // Si el parser expandiese la entidad, o bien leeria el fichero o bien
        // fallaria por no encontrarlo; en ambos casos el mensaje seria otro.
        // Se rechaza antes, por el DOCTYPE.
        String xxe = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE factura [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<factura id=\"comprobante\" version=\"2.1.0\">&xxe;</factura>";

        assertThatThrownBy(() -> validar(xxe))
                .isInstanceOf(FacturaXmlInvalidoException.class);
    }

    @Test
    void unDtdExternoPorHttpNoSeDescarga() {
        // El host es inexistente a proposito: si el validador intentase
        // resolverlo, la prueba tardaria (timeout de red) en lugar de fallar en
        // el acto. Rechaza sin salir de la JVM.
        String dtdExterno = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE factura SYSTEM \"http://host-que-no-existe.invalid/malicioso.dtd\">"
                + "<factura id=\"comprobante\" version=\"2.1.0\"/>";

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> validar(dtdExterno))
                .isInstanceOf(FacturaXmlInvalidoException.class);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(milisegundos)
                .as("debe rechazarse sin intentar ninguna conexion")
                .isLessThan(3_000);
    }

    @Test
    void unaBombaDeEntidadesNoSeExpande() {
        // "billion laughs" reducido: si se expandiera, consumiria memoria y
        // tiempo. Como necesita DOCTYPE, muere en la puerta.
        String bomba = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE factura ["
                + "<!ENTITY a \"aaaaaaaaaa\">"
                + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">"
                + "<!ENTITY d \"&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;\">"
                + "]>"
                + "<factura id=\"comprobante\" version=\"2.1.0\">&d;</factura>";

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> validar(bomba))
                .isInstanceOf(FacturaXmlInvalidoException.class);
        assertThat((System.nanoTime() - inicio) / 1_000_000)
                .as("debe rechazarse sin expandir nada")
                .isLessThan(3_000);
    }

    @Test
    void unSchemaLocationInyectadoNoCambiaElEsquemaUsado() {
        // Aunque el documento apunte a otro esquema, se valida contra el XSD
        // oficial local: accessExternalSchema esta vacio y ademas el validador
        // ya tiene su Schema compilado.
        String conSchemaLocation = xmlValido().replace(
                "<factura id=\"comprobante\" version=\"2.1.0\">",
                "<factura id=\"comprobante\" version=\"2.1.0\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:noNamespaceSchemaLocation=\"http://host-que-no-existe.invalid/otro.xsd\">");

        long inicio = System.nanoTime();
        assertThatCode(() -> validar(conSchemaLocation)).doesNotThrowAnyException();
        assertThat((System.nanoTime() - inicio) / 1_000_000)
                .as("no debe intentar descargar el esquema referenciado")
                .isLessThan(3_000);
    }
}
