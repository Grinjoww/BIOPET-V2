package com.biopet.facturacion.xml;

import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas del emisor frente a los facets del XSD oficial.
 */
class EmisorFiscalSriValidatorTest {

    private final EmisorFiscalSriValidator validator = new EmisorFiscalSriValidator();

    private EmisorFiscal emisorValido() {
        EmisorFiscal emisor = EmisorFiscal.builder()
                .ruc("0999999999001")
                .razonSocial("CLINICA VETERINARIA FICTICIA S.A.")
                .nombreComercial("BIOPET")
                .direccionMatriz("Av. Matriz Ficticia 100")
                .obligadoContabilidad(true)
                .contribuyenteEspecial("12345")
                .rimpe(false)
                .agenteRetencionResolucion("12345678")
                .activo(true)
                .build();
        emisor.setId(1L);
        return emisor;
    }

    private PuntoEmision puntoValido() {
        return PuntoEmision.builder()
                .establecimiento("001")
                .puntoEmision("001")
                .direccionEstablecimiento("Sucursal Ficticia")
                .activo(true)
                .build();
    }

    private void validar(EmisorFiscal emisor) {
        validator.validar(emisor, puntoValido());
    }

    // ==================================================================
    // Las constantes deben seguir siendo las del XSD
    // ==================================================================

    @Test
    void lasReglasCoincidenConLosFacetsDeclaradosEnElXsdOficial() {
        // Si alguien sustituye el XSD por otra version y los facets cambian,
        // esta prueba falla y obliga a revisar el validador, en lugar de dejar
        // reglas obsoletas validando contra un esquema que ya no las tiene.
        String xsd = leerXsd();

        assertThat(patron(xsd, "numeroRuc"))
                .isEqualTo(EmisorFiscalSriValidator.PATRON_RUC);

        assertThat(patron(xsd, "agenteRetencion"))
                .isEqualTo(EmisorFiscalSriValidator.PATRON_AGENTE_RETENCION);
        assertThat(faceta(xsd, "agenteRetencion", "maxLength"))
                .isEqualTo(EmisorFiscalSriValidator.MAX_AGENTE_RETENCION);

        assertThat(patron(xsd, "contribuyenteEspecial"))
                .isEqualTo(EmisorFiscalSriValidator.PATRON_CONTRIBUYENTE_ESPECIAL);
        assertThat(faceta(xsd, "contribuyenteEspecial", "minLength"))
                .isEqualTo(EmisorFiscalSriValidator.MIN_CONTRIBUYENTE_ESPECIAL);
        assertThat(faceta(xsd, "contribuyenteEspecial", "maxLength"))
                .isEqualTo(EmisorFiscalSriValidator.MAX_CONTRIBUYENTE_ESPECIAL);

        // Los cuatro campos de texto comparten los mismos facets.
        for (String tipo : new String[]{"razonSocial", "nombreComercial", "dirMatriz", "dirEstablecimiento"}) {
            assertThat(faceta(xsd, tipo, "maxLength"))
                    .as("maxLength de %s", tipo)
                    .isEqualTo(EmisorFiscalSriValidator.MAX_TEXTO);
            assertThat(patron(xsd, tipo))
                    .as("pattern de %s", tipo)
                    .isEqualTo("[^\\n]*");
        }
    }

    // ==================================================================
    // RUC
    // ==================================================================

    @Test
    void unRucConLaFormaDelSriEsValido() {
        assertThatCode(() -> validar(emisorValido())).doesNotThrowAnyException();
    }

    @Test
    void unRucDe13DigitosQueNoTerminaEn001SeRechaza() {
        // Este RUC pasa el CHECK de V7 (13 digitos) y NO pasa el XSD.
        EmisorFiscal emisor = emisorValido();
        emisor.setRuc("0999999999123");

        assertThatThrownBy(() -> validar(emisor))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("RUC")
                .hasMessageContaining("001");
    }

    @Test
    void unRucNuloOConLetrasSeRechaza() {
        EmisorFiscal sinRuc = emisorValido();
        sinRuc.setRuc(null);
        assertThatThrownBy(() -> validar(sinRuc))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);

        EmisorFiscal conLetras = emisorValido();
        conLetras.setRuc("09999X9999001");
        assertThatThrownBy(() -> validar(conLetras))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);
    }

    // ==================================================================
    // Agente de retencion
    // ==================================================================

    @Test
    void unAgenteRetencionNoNumericoOMuyLargoSeRechaza() {
        EmisorFiscal conTexto = emisorValido();
        conTexto.setAgenteRetencionResolucion("RES-2024");
        assertThatThrownBy(() -> validar(conTexto))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("agente de retencion");

        EmisorFiscal demasiadoLargo = emisorValido();
        demasiadoLargo.setAgenteRetencionResolucion("123456789");
        assertThatThrownBy(() -> validar(demasiadoLargo))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);
    }

    @Test
    void sinAgenteRetencionNoSeValidaNada() {
        // Es opcional: el builder lo omite del XML si no hay valor.
        EmisorFiscal emisor = emisorValido();
        emisor.setAgenteRetencionResolucion(null);
        assertThatCode(() -> validar(emisor)).doesNotThrowAnyException();

        emisor.setAgenteRetencionResolucion("  ");
        assertThatCode(() -> validar(emisor)).doesNotThrowAnyException();
    }

    // ==================================================================
    // Contribuyente especial
    // ==================================================================

    @Test
    void unContribuyenteEspecialDemasiadoCortoONoAlfanumericoSeRechaza() {
        // La columna es VARCHAR(13) sin patron: admite "AB" y "12-45".
        EmisorFiscal corto = emisorValido();
        corto.setContribuyenteEspecial("12");
        assertThatThrownBy(() -> validar(corto))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("contribuyente especial");

        EmisorFiscal conGuion = emisorValido();
        conGuion.setContribuyenteEspecial("12-45");
        assertThatThrownBy(() -> validar(conGuion))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);
    }

    @Test
    void sinContribuyenteEspecialNoSeValidaNada() {
        EmisorFiscal emisor = emisorValido();
        emisor.setContribuyenteEspecial(null);
        assertThatCode(() -> validar(emisor)).doesNotThrowAnyException();
    }

    // ==================================================================
    // Campos de texto
    // ==================================================================

    @Test
    void unSaltoDeLineaEnUnCampoDeTextoSeRechaza() {
        // El XSD los prohibe con [^\n]* y la columna si los admite.
        EmisorFiscal emisor = emisorValido();
        emisor.setRazonSocial("CLINICA\nFICTICIA");
        assertThatThrownBy(() -> validar(emisor))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("saltos de linea");

        EmisorFiscal enDireccion = emisorValido();
        enDireccion.setDireccionMatriz("Av. Matriz\r\n100");
        assertThatThrownBy(() -> validar(enDireccion))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);
    }

    @Test
    void razonSocialODireccionMatrizVaciasSeRechazan() {
        // Son obligatorias en el XML; la columna es NOT NULL pero admite "".
        EmisorFiscal sinRazon = emisorValido();
        sinRazon.setRazonSocial("   ");
        assertThatThrownBy(() -> validar(sinRazon))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("razon social");

        EmisorFiscal sinDireccion = emisorValido();
        sinDireccion.setDireccionMatriz("");
        assertThatThrownBy(() -> validar(sinDireccion))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("direccion matriz");
    }

    @Test
    void unaDireccionDeEstablecimientoConSaltoDeLineaSeRechaza() {
        PuntoEmision punto = puntoValido();
        punto.setDireccionEstablecimiento("Sucursal\nNorte");

        assertThatThrownBy(() -> validator.validar(emisorValido(), punto))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("establecimiento");
    }

    @Test
    void unEmisorNuloSeRechaza() {
        assertThatThrownBy(() -> validator.validar(null, puntoValido()))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class);
    }

    // ==================================================================
    // Lectura del XSD
    // ==================================================================

    private String leerXsd() {
        try (InputStream in = EmisorFiscalSriValidator.class
                .getResourceAsStream("/sri/xsd/factura/2.1.0/factura_V2.1.0.xsd")) {
            assertThat(in).as("el XSD oficial debe estar versionado").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String bloqueDe(String xsd, String tipo) {
        int inicio = xsd.indexOf("<xsd:simpleType name=\"" + tipo + "\">");
        assertThat(inicio).as("simpleType %s", tipo).isNotNegative();
        return xsd.substring(inicio, xsd.indexOf("</xsd:simpleType>", inicio));
    }

    private String patron(String xsd, String tipo) {
        Matcher m = Pattern.compile("<xsd:pattern value=\"(.*?)\"/>").matcher(bloqueDe(xsd, tipo));
        assertThat(m.find()).as("pattern de %s", tipo).isTrue();
        return m.group(1);
    }

    private int faceta(String xsd, String tipo, String nombre) {
        Matcher m = Pattern.compile("<xsd:" + nombre + " value=\"(\\d+)\"/>")
                .matcher(bloqueDe(xsd, tipo));
        assertThat(m.find()).as("%s de %s", nombre, tipo).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
