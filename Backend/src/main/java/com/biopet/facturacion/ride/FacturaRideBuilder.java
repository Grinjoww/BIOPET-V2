package com.biopet.facturacion.ride;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.EscalasSri;
import com.biopet.facturacion.domain.ImpuestoAgrupado;
import com.biopet.facturacion.domain.LineaCalculada;
import com.biopet.facturacion.domain.LineaFacturable;
import com.biopet.facturacion.domain.TipoEmisionSri;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye el RIDE (representacion impresa del comprobante electronico) de
 * una factura ya AUTORIZADA.
 *
 * <h2>Solo snapshots, igual que {@code FacturaXmlBuilder}</h2>
 *
 * <p>Esta clase NO consulta Usuario, DatosFacturacion, EmisorFiscal,
 * PuntoEmision, ConceptoFacturable ni TarifaImpuesto: no tiene inyectado un
 * solo repository. Todo sale de los campos ya congelados de {@link Factura} y
 * de sus {@code detalles}/{@code pagos}. El RIDE es la representacion IMPRESA
 * de lo ya autorizado, nunca un recalculo: si algun dia cambia el precio de un
 * concepto o la direccion del emisor, este documento debe imprimir exactamente
 * lo mismo que imprimio el dia de la autorizacion.
 *
 * <h2>Aritmetica: cero recalculo</h2>
 *
 * <p>Todos los importes (por linea y totales) se leen tal cual de la
 * factura/detalles ya persistidos. La unica operacion "de calculo" que hace
 * esta clase es exactamente la misma AGRUPACION por (codigo de impuesto,
 * codigo de porcentaje) que ya usa {@code FacturaXmlBuilder} -reutilizando
 * {@link CalculoFacturaService#agruparImpuestos}-, alimentada con los valores
 * YA CONGELADOS de cada linea (nunca con una tarifa recalculada desde el
 * porcentaje): ver {@link #aLineasCalculadasSinRecalcular}.
 *
 * <h2>Multipagina</h2>
 *
 * <p>La tabla de detalles usa {@code setHeaderRows(1)} (la cabecera de
 * columnas se repite en cada pagina) y {@code setSplitRows(false)} (una fila
 * nunca se corta entre dos paginas: si no cabe entera, pasa completa a la
 * siguiente). El resto del documento fluye con el layout normal de OpenPDF.
 */
@Component
public class FacturaRideBuilder {

    private static final ZoneId ZONA_ECUADOR = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZONA_ECUADOR);

    private static final Color COLOR_PRIMARIO = new Color(0x15, 0x30, 0x4D);
    private static final Color COLOR_BORDE = new Color(0xDE, 0xDC, 0xD5);
    private static final Color COLOR_FONDO_SUAVE = new Color(0xF3, 0xF2, 0xEE);

    private static final Font FUENTE_MARCA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_PRIMARIO);
    private static final Font FUENTE_EYEBROW = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Font FUENTE_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.DARK_GRAY);
    private static final Font FUENTE_VALOR = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_VALOR_DATO = FontFactory.getFont(FontFactory.COURIER, 9, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARIO);
    private static final Font FUENTE_TABLA_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FUENTE_TABLA_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_TOTAL_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font FUENTE_TOTAL_VALOR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font FUENTE_CLAVE = FontFactory.getFont(FontFactory.COURIER_BOLD, 10, Font.NORMAL, Color.BLACK);

    private final CalculoFacturaService calculoFacturaService;

    public FacturaRideBuilder(CalculoFacturaService calculoFacturaService) {
        this.calculoFacturaService = calculoFacturaService;
    }

    /**
     * @param factura una factura AUTORIZADA con numero de autorizacion, fecha
     *                de autorizacion y clave de acceso ya presentes. La
     *                comprobacion de que la factura este en condiciones de
     *                tener RIDE es responsabilidad de quien llama
     *                ({@code FacturaRideService}), no de esta clase: aqui solo
     *                se construye el documento a partir de lo que ya se sabe
     *                valido.
     */
    public byte[] construir(Factura factura) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Document documento = new Document(PageSize.A4, 36, 36, 48, 36);
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarCabecera(documento, factura);
            agregarClaveAcceso(documento, writer, factura);
            agregarEmisor(documento, factura);
            agregarComprador(documento, factura);
            agregarDetalles(documento, factura);
            agregarTotales(documento, factura);
            agregarPagos(documento, factura);
            agregarInformacionAdicional(documento, factura);

            documento.close();
        } catch (DocumentException e) {
            // No hay nada que el llamador pueda corregir reintentando con los
            // mismos datos: si el layout falla es un problema de esta clase o
            // de la biblioteca, nunca de la factura. Se traduce a un error de
            // servidor generico (500), igual que FacturaXmlService hace con un
            // SHA-256 no disponible.
            throw new IllegalStateException(
                    "No se pudo generar el RIDE de la factura " + factura.getId() + ".", e);
        }
        return salida.toByteArray();
    }

    // ==================================================================
    // Cabecera: marca + caja de datos del comprobante
    // ==================================================================

    private void agregarCabecera(Document documento, Factura factura) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{55f, 45f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(10f);

        PdfPCell marca = new PdfPCell();
        marca.setBorder(Element.RECTANGLE);
        marca.setBorderColor(COLOR_BORDE);
        marca.setPadding(8f);
        marca.addElement(new Paragraph("BIOPET", FUENTE_MARCA));
        marca.addElement(new Paragraph("Clínica veterinaria", FUENTE_EYEBROW));
        marca.addElement(espacio(6f));
        marca.addElement(new Paragraph("FACTURA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        marca.addElement(new Paragraph(
                "Representación impresa del comprobante electrónico (RIDE)", FUENTE_EYEBROW));
        tabla.addCell(marca);

        PdfPCell caja = new PdfPCell();
        caja.setBorder(Element.RECTANGLE);
        caja.setBorderColor(COLOR_BORDE);
        caja.setPadding(8f);
        caja.addElement(filaEtiquetaValor("RUC", vacioSiNulo(factura.getEmisorRuc())));
        caja.addElement(filaEtiquetaValor("N.º FACTURA", numeroCompleto(factura)));
        caja.addElement(filaEtiquetaValor("AMBIENTE", etiquetaAmbiente(factura.getAmbiente())));
        // TipoEmisionSri.NORMAL es el UNICO valor que existe para el metodo
        // offline (ver el javadoc del enum): no hay nada que leer de la
        // factura porque no hay ninguna alternativa que modelar.
        caja.addElement(filaEtiquetaValor("EMISIÓN", TipoEmisionSri.NORMAL.name()));
        caja.addElement(filaEtiquetaValor("N.º AUTORIZACIÓN", vacioSiNulo(factura.getNumeroAutorizacion())));
        caja.addElement(filaEtiquetaValor("FECHA Y HORA AUTORIZACIÓN", formatoFechaHora(factura.getFechaAutorizacion())));
        tabla.addCell(caja);

        documento.add(tabla);
    }

    /** "001-001-000000042", el mismo formato que usa la clave de acceso y el resto del sistema. */
    private String numeroCompleto(Factura factura) {
        if (factura.getEstablecimiento() == null || factura.getPuntoEmisionCodigo() == null
                || factura.getSecuencial() == null) {
            return "";
        }
        return factura.getEstablecimiento() + "-" + factura.getPuntoEmisionCodigo() + "-"
                + String.format("%09d", factura.getSecuencial());
    }

    private String etiquetaAmbiente(AmbienteSri ambiente) {
        return ambiente == null ? "" : ambiente.name();
    }

    private String formatoFechaHora(java.time.Instant instante) {
        return instante == null ? "" : FORMATO_FECHA_HORA.format(instante);
    }

    // ==================================================================
    // Clave de acceso: codigo de barras + texto legible
    // ==================================================================

    /**
     * El contenido que se codifica es EXACTAMENTE {@code factura.getClaveAcceso()}
     * -los 49 digitos persistidos, la misma cadena que viaja en el XML y que el
     * SRI autorizo-, nunca una version formateada, truncada o recompuesta.
     */
    private void agregarClaveAcceso(Document documento, PdfWriter writer, Factura factura) throws DocumentException {
        String clave = factura.getClaveAcceso();
        if (clave == null || clave.isBlank()) {
            return;
        }

        Barcode128 codigoBarras = construirCodigoBarras(clave);
        Image imagenBarras = codigoBarras.createImageWithBarcode(writer.getDirectContent(), null, null);
        imagenBarras.setAlignment(Element.ALIGN_CENTER);
        documento.add(imagenBarras);

        Paragraph texto = new Paragraph(clave, FUENTE_CLAVE);
        texto.setAlignment(Element.ALIGN_CENTER);
        texto.setSpacingAfter(10f);
        documento.add(texto);
    }

    /**
     * Extraido a su propio metodo, con visibilidad de paquete, exclusivamente
     * para que un test pueda comprobar -sin parsear el PDF final- que la
     * cadena que se codifica en el codigo de barras es EXACTAMENTE la clave
     * persistida, ni un caracter distinto (ver
     * {@code FacturaRideBuilderTest#elCodigoDeBarrasCodificaExactamenteLaClaveDeAcceso}).
     */
    Barcode128 construirCodigoBarras(String claveAcceso) {
        Barcode128 codigoBarras = new Barcode128();
        codigoBarras.setCode(claveAcceso);
        codigoBarras.setFont(null); // el texto legible se agrega aparte, con tipografia propia.
        codigoBarras.setBarHeight(28f);
        return codigoBarras;
    }

    // ==================================================================
    // Emisor
    // ==================================================================

    private void agregarEmisor(Document documento, Factura factura) throws DocumentException {
        documento.add(tituloSeccion("Emisor"));

        PdfPTable tabla = cajaSeccion();
        tabla.addCell(celdaEtiquetaValor("Razón social", vacioSiNulo(factura.getEmisorRazonSocial())));
        if (tieneTexto(factura.getEmisorNombreComercial())) {
            tabla.addCell(celdaEtiquetaValor("Nombre comercial", factura.getEmisorNombreComercial()));
        }
        tabla.addCell(celdaEtiquetaValor("RUC", vacioSiNulo(factura.getEmisorRuc())));
        tabla.addCell(celdaEtiquetaValor("Dirección matriz", vacioSiNulo(factura.getEmisorDireccionMatriz())));
        if (tieneTexto(factura.getEmisorDireccionEstablecimiento())) {
            tabla.addCell(celdaEtiquetaValor("Dirección establecimiento", factura.getEmisorDireccionEstablecimiento()));
        }
        if (tieneTexto(factura.getEmisorContribuyenteEspecial())) {
            tabla.addCell(celdaEtiquetaValor("Contribuyente especial", "Resolución N.º " + factura.getEmisorContribuyenteEspecial()));
        }
        if (factura.getEmisorObligadoContabilidad() != null) {
            tabla.addCell(celdaEtiquetaValor("Obligado a llevar contabilidad",
                    factura.getEmisorObligadoContabilidad() ? "SÍ" : "NO"));
        }
        documento.add(tabla);
    }

    // ==================================================================
    // Comprador
    // ==================================================================

    private void agregarComprador(Document documento, Factura factura) throws DocumentException {
        documento.add(tituloSeccion("Comprador"));

        PdfPTable tabla = cajaSeccion();
        tabla.addCell(celdaEtiquetaValor(
                etiquetaTipoIdentificacion(factura.getCompradorTipoIdentificacion()),
                vacioSiNulo(factura.getCompradorIdentificacion())));
        tabla.addCell(celdaEtiquetaValor("Razón social / Nombres", vacioSiNulo(factura.getCompradorRazonSocial())));
        if (tieneTexto(factura.getCompradorDireccion())) {
            tabla.addCell(celdaEtiquetaValor("Dirección", factura.getCompradorDireccion()));
        }
        documento.add(tabla);
    }

    private String etiquetaTipoIdentificacion(TipoIdentificacionSri tipo) {
        if (tipo == null) {
            return "Identificación";
        }
        return switch (tipo) {
            case RUC -> "RUC";
            case CEDULA -> "Cédula";
            case PASAPORTE -> "Pasaporte";
            case CONSUMIDOR_FINAL -> "Consumidor final";
            case IDENTIFICACION_EXTERIOR -> "Identificación del exterior";
        };
    }

    // ==================================================================
    // Detalles (tabla multipagina)
    // ==================================================================

    private void agregarDetalles(Document documento, Factura factura) throws DocumentException {
        documento.add(tituloSeccion("Detalles"));

        List<FacturaDetalle> detalles = factura.getDetalles();
        boolean hayDescuentos = detalles.stream().anyMatch(d -> d.getDescuento() != null && d.getDescuento().signum() > 0);

        int columnas = hayDescuentos ? 6 : 5;
        float[] anchos = hayDescuentos
                ? new float[]{14f, 34f, 10f, 14f, 12f, 16f}
                : new float[]{16f, 40f, 12f, 16f, 16f};
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(4f);
        tabla.setSpacingAfter(8f);
        tabla.setHeaderRows(1);
        // Una fila nunca se parte entre dos paginas: si no cabe entera, se
        // mueve completa a la siguiente (requisito de la Fase 10).
        tabla.setSplitRows(false);

        agregarEncabezado(tabla, "Código");
        agregarEncabezado(tabla, "Descripción");
        agregarEncabezado(tabla, "Cant.");
        agregarEncabezado(tabla, "P. Unitario");
        if (hayDescuentos) {
            agregarEncabezado(tabla, "Descuento");
        }
        agregarEncabezado(tabla, "Subtotal");

        for (FacturaDetalle detalle : detalles) {
            tabla.addCell(celdaTabla(vacioSiNulo(detalle.getCodigoPrincipal()), Element.ALIGN_LEFT));
            tabla.addCell(celdaTabla(vacioSiNulo(detalle.getDescripcion()), Element.ALIGN_LEFT));
            tabla.addCell(celdaTabla(formatoCantidad(detalle.getCantidad()), Element.ALIGN_RIGHT));
            tabla.addCell(celdaTabla(formatoMonetario(detalle.getPrecioUnitario()), Element.ALIGN_RIGHT));
            if (hayDescuentos) {
                tabla.addCell(celdaTabla(formatoMonetario(detalle.getDescuento()), Element.ALIGN_RIGHT));
            }
            tabla.addCell(celdaTabla(formatoMonetario(detalle.getPrecioTotalSinImpuesto()), Element.ALIGN_RIGHT));
        }

        documento.add(tabla);
    }

    // ==================================================================
    // Totales
    // ==================================================================

    private void agregarTotales(Document documento, Factura factura) throws DocumentException {
        List<ImpuestoAgrupado> resumenImpuestos = agruparImpuestosSinRecalcular(factura.getDetalles());

        PdfPTable tabla = new PdfPTable(new float[]{60f, 40f});
        tabla.setWidthPercentage(60);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingBefore(4f);
        tabla.setSpacingAfter(8f);

        tabla.addCell(celdaTotal("Subtotal sin impuestos", formatoMonetario(factura.getTotalSinImpuestos()), FUENTE_TOTAL_ETIQUETA));
        if (factura.getTotalDescuento() != null && factura.getTotalDescuento().signum() > 0) {
            tabla.addCell(celdaTotal("Descuento total", formatoMonetario(factura.getTotalDescuento()), FUENTE_TOTAL_ETIQUETA));
        }
        for (ImpuestoAgrupado grupo : resumenImpuestos) {
            String etiqueta = grupo.codigoImpuesto().name() + " " + grupo.tarifa().stripTrailingZeros().toPlainString() + "%"
                    + " (base " + formatoMonetario(grupo.baseImponible()) + ")";
            tabla.addCell(celdaTotal(etiqueta, formatoMonetario(grupo.valorImpuesto()), FUENTE_TOTAL_ETIQUETA));
        }
        tabla.addCell(celdaTotal("VALOR TOTAL", formatoMonetario(factura.getImporteTotal()) + " " + vacioSiNulo(factura.getMoneda()),
                FUENTE_TOTAL_VALOR));

        documento.add(tabla);
    }

    /**
     * Misma agrupacion que {@code FacturaXmlBuilder} (por
     * codigo de impuesto + codigo de porcentaje), pero alimentada con los
     * valores YA CONGELADOS de cada {@link FacturaDetalle} -nunca recalculados
     * desde cantidad x precio x tarifa-. Construir un {@link LineaFacturable}
     * desde un detalle ya persistido solo REVALIDA rangos (los mismos que ya
     * se cumplieron al emitir); no ejecuta ninguna operacion aritmetica cuyo
     * resultado se use.
     */
    private List<ImpuestoAgrupado> agruparImpuestosSinRecalcular(List<FacturaDetalle> detalles) {
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
            BigDecimal totalLinea = detalle.getPrecioTotalSinImpuesto().add(detalle.getImpuestoValor());
            lineas.add(new LineaCalculada(
                    origen, detalle.getPrecioTotalSinImpuesto(), detalle.getBaseImponible(),
                    detalle.getImpuestoValor(), totalLinea));
        }
        return calculoFacturaService.agruparImpuestos(lineas);
    }

    // ==================================================================
    // Pagos
    // ==================================================================

    private void agregarPagos(Document documento, Factura factura) throws DocumentException {
        List<FacturaPago> pagos = factura.getPagos();
        if (pagos.isEmpty()) {
            return;
        }
        documento.add(tituloSeccion("Forma de pago"));

        boolean hayPlazo = pagos.stream().anyMatch(p -> p.getPlazo() != null);
        PdfPTable tabla = new PdfPTable(hayPlazo ? new float[]{50f, 25f, 25f} : new float[]{70f, 30f});
        tabla.setWidthPercentage(70);
        tabla.setSpacingBefore(4f);
        tabla.setSpacingAfter(8f);
        tabla.setHeaderRows(1);
        tabla.setSplitRows(false);

        agregarEncabezado(tabla, "Forma de pago");
        agregarEncabezado(tabla, "Valor");
        if (hayPlazo) {
            agregarEncabezado(tabla, "Plazo");
        }

        for (FacturaPago pago : pagos) {
            tabla.addCell(celdaTabla(pago.getFormaPago().descripcion(), Element.ALIGN_LEFT));
            tabla.addCell(celdaTabla(formatoMonetario(pago.getTotal()), Element.ALIGN_RIGHT));
            if (hayPlazo) {
                String plazoTexto = pago.getPlazo() == null ? "" : pago.getPlazo() + " " + vacioSiNulo(pago.getUnidadTiempo());
                tabla.addCell(celdaTabla(plazoTexto, Element.ALIGN_RIGHT));
            }
        }

        documento.add(tabla);
    }

    // ==================================================================
    // Informacion adicional (solo campos reales, mismo criterio que
    // FacturaXmlBuilder#infoAdicional)
    // ==================================================================

    private void agregarInformacionAdicional(Document documento, Factura factura) throws DocumentException {
        List<String[]> campos = new ArrayList<>();
        if (tieneTexto(factura.getCompradorEmail())) {
            campos.add(new String[]{"Email", factura.getCompradorEmail()});
        }
        if (tieneTexto(factura.getCompradorTelefono())) {
            campos.add(new String[]{"Teléfono", factura.getCompradorTelefono()});
        }
        if (campos.isEmpty()) {
            return;
        }

        documento.add(tituloSeccion("Información adicional"));
        PdfPTable tabla = cajaSeccion();
        for (String[] campo : campos) {
            tabla.addCell(celdaEtiquetaValor(campo[0], campo[1]));
        }
        documento.add(tabla);
    }

    // ==================================================================
    // Helpers de formato y layout
    // ==================================================================

    private Paragraph tituloSeccion(String texto) {
        Paragraph parrafo = new Paragraph(texto.toUpperCase(java.util.Locale.forLanguageTag("es-EC")), FUENTE_SECCION);
        parrafo.setSpacingBefore(6f);
        parrafo.setSpacingAfter(4f);
        return parrafo;
    }

    private PdfPTable cajaSeccion() {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        return tabla;
    }

    private PdfPCell celdaEtiquetaValor(String etiqueta, String valor) {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Element.RECTANGLE);
        celda.setBorderColor(COLOR_BORDE);
        celda.setPadding(4f);
        celda.addElement(filaEtiquetaValor(etiqueta.toUpperCase(java.util.Locale.forLanguageTag("es-EC")), valor));
        return celda;
    }

    private Paragraph filaEtiquetaValor(String etiqueta, String valor) {
        Paragraph parrafo = new Paragraph();
        parrafo.add(new Chunk(etiqueta + ":  ", FUENTE_ETIQUETA));
        parrafo.add(new Chunk(valor == null ? "" : valor, FUENTE_VALOR_DATO));
        parrafo.setSpacingAfter(2f);
        return parrafo;
    }

    private void agregarEncabezado(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new com.lowagie.text.Phrase(texto, FUENTE_TABLA_CABECERA));
        celda.setBackgroundColor(COLOR_PRIMARIO);
        celda.setPadding(4f);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(celda);
    }

    private PdfPCell celdaTabla(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new com.lowagie.text.Phrase(texto == null ? "" : texto, FUENTE_TABLA_CELDA));
        celda.setPadding(4f);
        celda.setHorizontalAlignment(alineacion);
        celda.setBorderColor(COLOR_BORDE);
        return celda;
    }

    private PdfPCell celdaTotal(String etiqueta, String valor, Font fuenteEtiqueta) {
        // Una sola celda con ambos textos alineados a la derecha: mas simple
        // que dos columnas separadas y evita desalinear la caja de totales
        // cuando la etiqueta del impuesto es larga (incluye la base imponible).
        PdfPCell celda = new PdfPCell();
        celda.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celda.setPadding(3f);
        Paragraph parrafo = new Paragraph();
        parrafo.add(new Chunk(etiqueta + "   ", fuenteEtiqueta));
        parrafo.add(new Chunk(valor, FUENTE_TOTAL_VALOR));
        parrafo.setAlignment(Element.ALIGN_RIGHT);
        celda.addElement(parrafo);
        celda.setColspan(2);
        return celda;
    }

    private Paragraph espacio(float alto) {
        Paragraph parrafo = new Paragraph(" ");
        parrafo.setSpacingAfter(alto);
        return parrafo;
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "" : valor;
    }

    /** 2 decimales fijos para toda cifra monetaria, sin recalcular nada: solo formato de presentacion. */
    private static String formatoMonetario(BigDecimal valor) {
        return valor == null ? "" : EscalasSri.aMonetario(valor).toPlainString();
    }

    /** Cantidad sin ceros de relleno (no es dinero): "2" en vez de "2.000000". */
    private static String formatoCantidad(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        BigDecimal limpio = valor.stripTrailingZeros();
        return limpio.scale() < 0 ? limpio.setScale(0).toPlainString() : limpio.toPlainString();
    }
}
