package com.biopet.facturacion.xml;

import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Comprueba que los datos del emisor cumplen los facets del XSD oficial ANTES de
 * emitir.
 *
 * <h2>Por que existe</h2>
 *
 * <p>La tabla {@code emisor_fiscal} (V7) es a proposito mas permisiva que el
 * XSD: el CHECK del RUC solo exige 13 digitos, y varias columnas son texto libre.
 * El XSD de Factura 2.1.0 es mas estricto. Sin esta comprobacion, una
 * configuracion aceptada por la base de datos permitiria:
 *
 * <pre>
 *   reservar secuencial -> EMITIDA -> COMMIT
 *   ... y solo despues, al generar el XML, descubrir que el SRI lo rechaza
 * </pre>
 *
 * <p>Eso dejaria una factura emitida que no puede producir XML valido y, lo
 * peor, habria consumido un numero de una serie que la ley exige contigua. Por
 * eso se valida antes de tocar el contador: un error de configuracion debe
 * costar un mensaje, no un hueco en la numeracion.
 *
 * <h2>De donde salen las reglas</h2>
 *
 * <p>De los {@code simpleType} del XSD local
 * {@code /sri/xsd/factura/2.1.0/factura_V2.1.0.xsd}, copiados literalmente. No
 * son criterios inventados aqui. {@code EmisorFiscalSriValidatorTest} lee ese
 * mismo archivo y comprueba que las constantes de abajo siguen coincidiendo con
 * los facets declarados, de modo que sustituir el XSD por otra version sin
 * revisar esta clase rompe el build en lugar de pasar desapercibido.
 *
 * <p>Solo se valida lo que de verdad viaja al XML. Los campos opcionales se
 * comprueban unicamente cuando tienen valor, porque {@link FacturaXmlBuilder}
 * omite del documento los que estan vacios: exigirlos aqui rechazaria
 * configuraciones que producen un XML perfectamente valido.
 */
@Component
public class EmisorFiscalSriValidator {

    /** simpleType {@code numeroRuc}: {@code <xsd:pattern value="[0-9]{10}001"/>} */
    static final String PATRON_RUC = "[0-9]{10}001";

    /** simpleType {@code agenteRetencion}: {@code [0-9]+} con {@code maxLength 8}. */
    static final String PATRON_AGENTE_RETENCION = "[0-9]+";
    static final int MAX_AGENTE_RETENCION = 8;

    /**
     * simpleType {@code contribuyenteEspecial}: {@code ([A-Za-z0-9])*} con
     * {@code minLength 3} y {@code maxLength 13}.
     */
    static final String PATRON_CONTRIBUYENTE_ESPECIAL = "([A-Za-z0-9])*";
    static final int MIN_CONTRIBUYENTE_ESPECIAL = 3;
    static final int MAX_CONTRIBUYENTE_ESPECIAL = 13;

    /**
     * {@code razonSocial}, {@code nombreComercial}, {@code dirMatriz} y
     * {@code dirEstablecimiento} comparten facets: {@code minLength 1},
     * {@code maxLength 300} y {@code [^\n]*} (ningun salto de linea).
     */
    static final int MAX_TEXTO = 300;

    private static final Pattern RUC = Pattern.compile(PATRON_RUC);
    private static final Pattern AGENTE_RETENCION = Pattern.compile(PATRON_AGENTE_RETENCION);
    private static final Pattern CONTRIBUYENTE_ESPECIAL = Pattern.compile(PATRON_CONTRIBUYENTE_ESPECIAL);

    /**
     * @throws ConfiguracionFiscalInvalidaException si algun dato del emisor o del
     *         punto de emision produciria un XML que el XSD rechaza.
     */
    public void validar(EmisorFiscal emisor, PuntoEmision puntoEmision) {
        if (emisor == null) {
            throw new ConfiguracionFiscalInvalidaException(
                    "No hay emisor fiscal con el que emitir.");
        }

        // --- RUC: el caso mas importante. La BD acepta 13 digitos cualesquiera;
        //     el SRI solo acepta los que terminan en 001.
        if (emisor.getRuc() == null || !RUC.matcher(emisor.getRuc()).matches()) {
            throw new ConfiguracionFiscalInvalidaException(
                    "El RUC del emisor " + emisor.getId() + " (\"" + emisor.getRuc()
                            + "\") no cumple el formato que exige el SRI: 10 digitos seguidos de "
                            + "001. Corrija la configuracion fiscal antes de emitir.");
        }

        exigirTextoXml(emisor.getRazonSocial(), "razon social", emisor, true);
        exigirTextoXml(emisor.getDireccionMatriz(), "direccion matriz", emisor, true);
        exigirTextoXml(emisor.getNombreComercial(), "nombre comercial", emisor, false);

        if (tieneTexto(emisor.getContribuyenteEspecial())) {
            String valor = emisor.getContribuyenteEspecial();
            if (valor.length() < MIN_CONTRIBUYENTE_ESPECIAL
                    || valor.length() > MAX_CONTRIBUYENTE_ESPECIAL
                    || !CONTRIBUYENTE_ESPECIAL.matcher(valor).matches()) {
                throw new ConfiguracionFiscalInvalidaException(
                        "El numero de contribuyente especial del emisor " + emisor.getId()
                                + " (\"" + valor + "\") debe tener entre " + MIN_CONTRIBUYENTE_ESPECIAL
                                + " y " + MAX_CONTRIBUYENTE_ESPECIAL
                                + " caracteres alfanumericos para el SRI.");
            }
        }

        if (tieneTexto(emisor.getAgenteRetencionResolucion())) {
            String valor = emisor.getAgenteRetencionResolucion();
            if (valor.length() > MAX_AGENTE_RETENCION
                    || !AGENTE_RETENCION.matcher(valor).matches()) {
                throw new ConfiguracionFiscalInvalidaException(
                        "La resolucion de agente de retencion del emisor " + emisor.getId()
                                + " (\"" + valor + "\") debe ser solo digitos, con un maximo de "
                                + MAX_AGENTE_RETENCION + ", para el SRI.");
            }
        }

        if (puntoEmision != null) {
            exigirTextoXml(puntoEmision.getDireccionEstablecimiento(),
                    "direccion del establecimiento", emisor, false);
        }
    }

    /**
     * Los cuatro campos de texto del emisor comparten facets. Un salto de linea
     * los invalida ({@code [^\n]*}), y la BD si lo admite.
     *
     * @param obligatorio si el campo es obligatorio en el XML. Los opcionales,
     *                    cuando estan vacios, el builder los omite y no hay nada
     *                    que validar.
     */
    private void exigirTextoXml(String valor, String etiqueta, EmisorFiscal emisor, boolean obligatorio) {
        if (!tieneTexto(valor)) {
            if (obligatorio) {
                throw new ConfiguracionFiscalInvalidaException(
                        "El emisor " + emisor.getId() + " no tiene " + etiqueta
                                + ", que el SRI exige en el comprobante.");
            }
            return;
        }
        if (valor.length() > MAX_TEXTO) {
            throw new ConfiguracionFiscalInvalidaException(
                    "La " + etiqueta + " del emisor " + emisor.getId() + " supera los "
                            + MAX_TEXTO + " caracteres que admite el SRI.");
        }
        if (valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0) {
            throw new ConfiguracionFiscalInvalidaException(
                    "La " + etiqueta + " del emisor " + emisor.getId()
                            + " no puede contener saltos de linea: el SRI no los admite.");
        }
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
