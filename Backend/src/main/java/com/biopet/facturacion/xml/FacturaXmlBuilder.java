package com.biopet.facturacion.xml;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.EscalasSri;
import com.biopet.facturacion.domain.ImpuestoAgrupado;
import com.biopet.facturacion.domain.LineaCalculada;
import com.biopet.facturacion.domain.LineaFacturable;
import com.biopet.facturacion.domain.TipoComprobante;
import com.biopet.facturacion.domain.TipoEmisionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compone el XML oficial de Factura 2.1.0 a partir de una factura EMITIDA.
 *
 * <h2>Solo snapshots</h2>
 *
 * <p>Esta clase NO consulta Usuario, DatosFacturacion, EmisorFiscal,
 * PuntoEmision, ConceptoFacturable ni TarifaImpuesto: no tiene un solo
 * repository inyectado, de modo que leer un dato vivo por descuido es
 * imposible, no solo desaconsejado. Todo sale de los campos congelados de la
 * factura y de sus detalles y pagos.
 *
 * <p>Importa porque el XML puede generarse dias despues de emitir, y porque en
 * una fase posterior se regenerara para reintentar ante el SRI: el documento
 * debe salir identico cada vez, aunque entre medias hayan cambiado el precio de
 * un concepto, la tarifa del IVA o la direccion del emisor.
 *
 * <h2>Aritmetica</h2>
 *
 * <p>No se recalcula nada. Los importes ya vienen calculados y congelados desde
 * la emision (Fase 5A). Lo unico que se hace es AGRUPAR los impuestos de las
 * lineas, y para eso se reutiliza {@link CalculoFacturaService#agruparImpuestos}
 * de la Fase 2 en lugar de escribir una segunda agrupacion: asi el XML agrupa
 * exactamente igual que agrupo el calculo que produjo los totales.
 *
 * <p>Antes de emitir el documento se comprueba que los snapshots reconcilien
 * entre si. Si no cuadran, se falla: un comprobante incoherente lo rechazaria el
 * SRI con el error 52, y es mejor no producirlo.
 *
 * <h2>Serializacion</h2>
 *
 * <p>UTF-8, en una sola linea y sin sangrado. No es por ahorrar bytes: la firma
 * XAdES de la fase siguiente se calcula sobre el documento canonicalizado, y el
 * espacio en blanco entre elementos forma parte de lo que se firma. Un XML sin
 * sangrado no tiene espacio en blanco que pueda alterarse despues sin invalidar
 * la firma.
 *
 * <p>El escapado de {@code &}, {@code <}, {@code >} y las comillas lo hace el
 * {@link Transformer}. No hay ni un {@code replace()} manual: escapar XML a mano
 * es como se producen los documentos que fallan con el primer apellido que trae
 * un ampersand.
 */
@Component
public class FacturaXmlBuilder {

    public static final String VERSION = "2.1.0";
    public static final String ID_COMPROBANTE = "comprobante";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Literal exacto que exige el XSD cuando el emisor esta acogido al RIMPE. */
    private static final String LEYENDA_RIMPE = "CONTRIBUYENTE RÉGIMEN RIMPE";

    private final CalculoFacturaService calculoFacturaService;

    public FacturaXmlBuilder(CalculoFacturaService calculoFacturaService) {
        this.calculoFacturaService = calculoFacturaService;
    }

    /**
     * @return el XML del comprobante en UTF-8, sin firma.
     * @throws FacturaXmlInvalidoException si la factura no esta emitida, le falta
     *         un snapshot obligatorio o sus importes no reconcilian.
     */
    public byte[] construir(Factura factura) {
        exigirEmitida(factura);

        List<FacturaDetalle> detalles = factura.getDetalles().stream()
                .sorted(Comparator.comparing(FacturaDetalle::getLinea))
                .toList();
        if (detalles.isEmpty()) {
            throw new FacturaXmlInvalidoException(
                    "La factura " + factura.getId() + " no tiene detalles: el XSD exige al menos uno.");
        }

        List<LineaCalculada> lineas = aLineasCalculadas(detalles);
        List<ImpuestoAgrupado> resumen = calculoFacturaService.agruparImpuestos(lineas);
        reconciliar(factura, lineas, resumen);

        Document documento = nuevoDocumento();
        Element raiz = documento.createElement("factura");
        raiz.setAttribute("id", ID_COMPROBANTE);
        raiz.setAttribute("version", VERSION);
        documento.appendChild(raiz);

        raiz.appendChild(infoTributaria(documento, factura));
        raiz.appendChild(infoFactura(documento, factura, resumen));
        raiz.appendChild(detalles(documento, detalles));

        Element infoAdicional = infoAdicional(documento, factura);
        if (infoAdicional != null) {
            raiz.appendChild(infoAdicional);
        }

        return serializar(documento);
    }

    // ==================================================================
    // Bloques
    // ==================================================================

    private Element infoTributaria(Document doc, Factura factura) {
        Element info = doc.createElement("infoTributaria");

        AmbienteSri ambiente = exigir(factura.getAmbiente(), factura, "ambiente");
        agregar(doc, info, "ambiente", ambiente.codigo());
        agregar(doc, info, "tipoEmision", TipoEmisionSri.NORMAL.codigo());
        agregar(doc, info, "razonSocial", exigirTexto(factura.getEmisorRazonSocial(), factura, "emisorRazonSocial"));
        agregarSiHay(doc, info, "nombreComercial", factura.getEmisorNombreComercial());
        agregar(doc, info, "ruc", exigirTexto(factura.getEmisorRuc(), factura, "emisorRuc"));
        agregar(doc, info, "claveAcceso", exigirTexto(factura.getClaveAcceso(), factura, "claveAcceso"));
        agregar(doc, info, "codDoc", TipoComprobante.FACTURA.codDoc());
        agregar(doc, info, "estab", exigirTexto(factura.getEstablecimiento(), factura, "establecimiento"));
        agregar(doc, info, "ptoEmi", exigirTexto(factura.getPuntoEmisionCodigo(), factura, "puntoEmision"));
        agregar(doc, info, "secuencial", secuencialFormateado(factura));
        agregar(doc, info, "dirMatriz", exigirTexto(factura.getEmisorDireccionMatriz(), factura, "emisorDireccionMatriz"));
        agregarSiHay(doc, info, "agenteRetencion", factura.getEmisorAgenteRetencionResolucion());
        if (Boolean.TRUE.equals(factura.getEmisorRimpe())) {
            // El XSD no admite texto libre: exige exactamente esta leyenda.
            agregar(doc, info, "contribuyenteRimpe", LEYENDA_RIMPE);
        }
        return info;
    }

    private Element infoFactura(Document doc, Factura factura, List<ImpuestoAgrupado> resumen) {
        Element info = doc.createElement("infoFactura");

        agregar(doc, info, "fechaEmision", factura.getFechaEmision().format(FORMATO_FECHA));
        agregarSiHay(doc, info, "dirEstablecimiento", factura.getEmisorDireccionEstablecimiento());
        agregarSiHay(doc, info, "contribuyenteEspecial", factura.getEmisorContribuyenteEspecial());
        if (factura.getEmisorObligadoContabilidad() != null) {
            agregar(doc, info, "obligadoContabilidad",
                    factura.getEmisorObligadoContabilidad() ? "SI" : "NO");
        }
        agregar(doc, info, "tipoIdentificacionComprador",
                exigir(factura.getCompradorTipoIdentificacion(), factura, "compradorTipoIdentificacion").codigo());
        agregar(doc, info, "razonSocialComprador",
                exigirTexto(factura.getCompradorRazonSocial(), factura, "compradorRazonSocial"));
        agregar(doc, info, "identificacionComprador",
                exigirTexto(factura.getCompradorIdentificacion(), factura, "compradorIdentificacion"));
        agregarSiHay(doc, info, "direccionComprador", factura.getCompradorDireccion());

        agregar(doc, info, "totalSinImpuestos", monetario(factura.getTotalSinImpuestos()));
        agregar(doc, info, "totalDescuento", monetario(factura.getTotalDescuento()));

        Element totalConImpuestos = doc.createElement("totalConImpuestos");
        for (ImpuestoAgrupado grupo : resumen) {
            Element total = doc.createElement("totalImpuesto");
            // Orden exigido por el XSD en ESTE bloque: baseImponible antes que
            // tarifa. En el impuesto del detalle el orden es el contrario.
            agregar(doc, total, "codigo", grupo.codigoImpuesto().codigo());
            agregar(doc, total, "codigoPorcentaje", grupo.codigoPorcentaje());
            agregar(doc, total, "baseImponible", monetario(grupo.baseImponible()));
            agregar(doc, total, "tarifa", tarifa(grupo.tarifa()));
            agregar(doc, total, "valor", monetario(grupo.valorImpuesto()));
            totalConImpuestos.appendChild(total);
        }
        info.appendChild(totalConImpuestos);

        agregar(doc, info, "importeTotal", monetario(factura.getImporteTotal()));
        agregarSiHay(doc, info, "moneda", factura.getMoneda());

        Element pagos = pagos(doc, factura);
        if (pagos != null) {
            info.appendChild(pagos);
        }
        return info;
    }

    private Element pagos(Document doc, Factura factura) {
        if (factura.getPagos().isEmpty()) {
            // El bloque es opcional en el XSD, pero un <pagos> vacio no lo es:
            // exige al menos un <pago>. Se omite entero antes que emitirlo vacio.
            return null;
        }
        Element pagos = doc.createElement("pagos");
        for (FacturaPago pago : factura.getPagos()) {
            Element elemento = doc.createElement("pago");
            agregar(doc, elemento, "formaPago", pago.getFormaPago().codigo());
            agregar(doc, elemento, "total", monetario(pago.getTotal()));
            if (pago.getPlazo() != null) {
                agregar(doc, elemento, "plazo", String.valueOf(pago.getPlazo()));
            }
            agregarSiHay(doc, elemento, "unidadTiempo", pago.getUnidadTiempo());
            pagos.appendChild(elemento);
        }
        return pagos;
    }

    private Element detalles(Document doc, List<FacturaDetalle> detalles) {
        Element bloque = doc.createElement("detalles");
        for (FacturaDetalle detalle : detalles) {
            Element elemento = doc.createElement("detalle");
            agregarSiHay(doc, elemento, "codigoPrincipal", detalle.getCodigoPrincipal());
            agregarSiHay(doc, elemento, "codigoAuxiliar", detalle.getCodigoAuxiliar());
            agregar(doc, elemento, "descripcion", detalle.getDescripcion());
            agregar(doc, elemento, "cantidad", cantidad(detalle.getCantidad()));
            agregar(doc, elemento, "precioUnitario", cantidad(detalle.getPrecioUnitario()));
            agregar(doc, elemento, "descuento", monetario(detalle.getDescuento()));
            agregar(doc, elemento, "precioTotalSinImpuesto", monetario(detalle.getPrecioTotalSinImpuesto()));

            Element impuestos = doc.createElement("impuestos");
            Element impuesto = doc.createElement("impuesto");
            agregar(doc, impuesto, "codigo", detalle.getImpuestoCodigo().codigo());
            agregar(doc, impuesto, "codigoPorcentaje", detalle.getImpuestoCodigoPorcentaje());
            agregar(doc, impuesto, "tarifa", tarifa(detalle.getImpuestoTarifa()));
            agregar(doc, impuesto, "baseImponible", monetario(detalle.getBaseImponible()));
            agregar(doc, impuesto, "valor", monetario(detalle.getImpuestoValor()));
            impuestos.appendChild(impuesto);
            elemento.appendChild(impuestos);

            bloque.appendChild(elemento);
        }
        return bloque;
    }

    /**
     * Solo se emiten campos que EXISTEN como snapshot en la factura. No se
     * inventa nada: ni RUC del proveedor de software, ni leyendas regulatorias
     * cuya aplicabilidad no se ha decidido.
     */
    private Element infoAdicional(Document doc, Factura factura) {
        List<String[]> campos = new ArrayList<>();
        if (tieneTexto(factura.getCompradorEmail())) {
            campos.add(new String[]{"email", factura.getCompradorEmail()});
        }
        if (tieneTexto(factura.getCompradorTelefono())) {
            campos.add(new String[]{"telefono", factura.getCompradorTelefono()});
        }
        if (campos.isEmpty()) {
            return null;
        }
        Element bloque = doc.createElement("infoAdicional");
        for (String[] campo : campos) {
            Element elemento = doc.createElement("campoAdicional");
            elemento.setAttribute("nombre", campo[0]);
            elemento.setTextContent(campo[1]);
            bloque.appendChild(elemento);
        }
        return bloque;
    }

    // ==================================================================
    // Agrupacion y reconciliacion
    // ==================================================================

    /**
     * Envuelve cada detalle en los value objects de la Fase 2 SIN recalcular:
     * base e impuesto se toman del snapshot tal cual estan guardados.
     */
    private List<LineaCalculada> aLineasCalculadas(List<FacturaDetalle> detalles) {
        List<LineaCalculada> lineas = new ArrayList<>(detalles.size());
        for (FacturaDetalle detalle : detalles) {
            LineaFacturable origen = new LineaFacturable(
                    detalle.getCodigoPrincipal(),
                    detalle.getDescripcion(),
                    detalle.getCantidad(),
                    detalle.getPrecioUnitario(),
                    detalle.getDescuento(),
                    detalle.getImpuestoCodigo(),
                    detalle.getImpuestoCodigoPorcentaje(),
                    detalle.getImpuestoTarifa());
            lineas.add(new LineaCalculada(
                    origen,
                    detalle.getPrecioTotalSinImpuesto(),
                    detalle.getBaseImponible(),
                    detalle.getImpuestoValor(),
                    detalle.getPrecioTotalSinImpuesto().add(detalle.getImpuestoValor())));
        }
        return lineas;
    }

    /**
     * Comprueba que los snapshots cuadran entre si antes de escribir el XML.
     * Si un detalle se hubiese tocado por SQL, o la cabecera hubiese quedado
     * desincronizada, esto lo detecta aqui y no el SRI despues.
     */
    private void reconciliar(Factura factura, List<LineaCalculada> lineas,
                             List<ImpuestoAgrupado> resumen) {
        BigDecimal sumaLineas = EscalasSri.aMonetario(lineas.stream()
                .map(LineaCalculada::precioTotalSinImpuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal sumaImpuestos = EscalasSri.aMonetario(resumen.stream()
                .map(ImpuestoAgrupado::valorImpuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal sumaBases = EscalasSri.aMonetario(resumen.stream()
                .map(ImpuestoAgrupado::baseImponible)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        exigirIgual(factura, "totalSinImpuestos", factura.getTotalSinImpuestos(), sumaLineas);
        exigirIgual(factura, "suma de bases imponibles", sumaBases, sumaLineas);
        exigirIgual(factura, "totalImpuestos", factura.getTotalImpuestos(), sumaImpuestos);
        exigirIgual(factura, "importeTotal", factura.getImporteTotal(),
                sumaLineas.add(sumaImpuestos));

        if (!factura.getPagos().isEmpty()) {
            BigDecimal sumaPagos = EscalasSri.aMonetario(factura.getPagos().stream()
                    .map(FacturaPago::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            exigirIgual(factura, "suma de pagos", sumaPagos, factura.getImporteTotal());
        }
    }

    private void exigirIgual(Factura factura, String etiqueta, BigDecimal real, BigDecimal esperado) {
        if (real == null || real.compareTo(esperado) != 0) {
            throw new FacturaXmlInvalidoException(
                    "La factura " + factura.getId() + " tiene snapshots incoherentes: " + etiqueta
                            + " vale " + real + " y deberia valer " + esperado
                            + ". No se genera XML de un comprobante que no cuadra.");
        }
    }

    // ==================================================================
    // Formato y utilidades
    // ==================================================================

    /** Importes con exactamente 2 decimales, punto decimal y sin notacion cientifica. */
    private String monetario(BigDecimal valor) {
        return valor.setScale(EscalasSri.ESCALA_MONETARIA, EscalasSri.REDONDEO).toPlainString();
    }

    /** Cantidad y precio unitario: hasta 6 decimales (facet del XSD). */
    private String cantidad(BigDecimal valor) {
        return valor.setScale(EscalasSri.ESCALA_CANTIDAD, EscalasSri.REDONDEO).toPlainString();
    }

    private String tarifa(BigDecimal valor) {
        return valor.setScale(EscalasSri.ESCALA_TARIFA, EscalasSri.REDONDEO).toPlainString();
    }

    /** El XSD exige 9 digitos exactos. */
    private String secuencialFormateado(Factura factura) {
        Long secuencial = exigir(factura.getSecuencial(), factura, "secuencial");
        return String.format("%09d", secuencial);
    }

    private void agregar(Document doc, Element padre, String nombre, String valor) {
        Element elemento = doc.createElement(nombre);
        // setTextContent deja el escapado en manos del Transformer.
        elemento.setTextContent(valor);
        padre.appendChild(elemento);
    }

    private void agregarSiHay(Document doc, Element padre, String nombre, String valor) {
        if (tieneTexto(valor)) {
            agregar(doc, padre, nombre, valor);
        }
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private void exigirEmitida(Factura factura) {
        if (factura == null) {
            throw new FacturaXmlInvalidoException("La factura es obligatoria.");
        }
        if (factura.getEstado() == null || factura.getEstado() == EstadoFactura.BORRADOR) {
            throw new FacturaXmlInvalidoException(
                    "La factura " + factura.getId() + " esta en BORRADOR: solo se genera XML de "
                            + "comprobantes ya emitidos, que son los que tienen numeracion y snapshots.");
        }
    }

    private <T> T exigir(T valor, Factura factura, String campo) {
        if (valor == null) {
            throw new FacturaXmlInvalidoException(
                    "La factura " + factura.getId() + " no tiene " + campo
                            + ", que el XSD exige. El comprobante se emitio incompleto.");
        }
        return valor;
    }

    private String exigirTexto(String valor, Factura factura, String campo) {
        if (!tieneTexto(valor)) {
            throw new FacturaXmlInvalidoException(
                    "La factura " + factura.getId() + " no tiene " + campo
                            + ", que el XSD exige. El comprobante se emitio incompleto.");
        }
        return valor;
    }

    private Document nuevoDocumento() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document documento = factory.newDocumentBuilder().newDocument();
            // Sin esto, el Transformer anade standalone="no" a la declaracion
            // XML. El comprobante de ejemplo del SRI no lo lleva, y cuanto menos
            // ruido tenga la cabecera, menos superficie para que la firma de la
            // fase siguiente vea un documento distinto del esperado.
            documento.setXmlStandalone(true);
            return documento;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo crear el documento XML.", e);
        }
    }

    private byte[] serializar(Document documento) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            // NO se fija OutputKeys.STANDALONE: hacerlo mete un
            // standalone="no" en la declaracion que el XML de ejemplo del SRI
            // no lleva. La declaracion queda como
            // <?xml version="1.0" encoding="UTF-8"?> y nada mas.

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(documento), new StreamResult(salida));
            return salida.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo serializar el XML de la factura.", e);
        }
    }
}
