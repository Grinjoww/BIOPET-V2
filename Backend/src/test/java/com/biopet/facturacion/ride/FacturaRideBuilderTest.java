package com.biopet.facturacion.ride;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba SIN Spring ni PostgreSQL, igual que {@code TarifaImpuestoResolverTest}:
 * construye una {@link Factura} en memoria (nunca se persiste nada) y verifica
 * el PDF que produce {@link FacturaRideBuilder} contra la propia biblioteca
 * que lo genero (releerlo con {@link PdfReader}/{@link PdfTextExtractor} es la
 * unica forma honesta de comprobar "el PDF es valido y dice lo que debe decir"
 * sin acoplarse a coordenadas de layout).
 */
class FacturaRideBuilderTest {

    private static final String CLAVE_ACCESO_49_DIGITOS = "1234567890123456789012345678901234567890123456789";

    private final FacturaRideBuilder builder = new FacturaRideBuilder(new CalculoFacturaService());

    @Test
    void elPdfEmpiezaConLaCabeceraRealDeUnPdf() {
        byte[] pdf = builder.construir(facturaAutorizada(1));

        assertThat(pdf.length).isGreaterThan(4);
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void elPdfSePuedeReabrirConLaMismaBibliotecaYContieneLosDatosFiscalesMinimos() throws Exception {
        Factura factura = facturaAutorizada(1);
        byte[] pdf = builder.construir(factura);

        String texto = textoCompleto(pdf);

        assertThat(texto).as("numero completo").contains("001-001-000000042");
        assertThat(texto).as("RUC").contains(factura.getEmisorRuc());
        assertThat(texto).as("numero de autorizacion").contains(factura.getNumeroAutorizacion());
        assertThat(texto).as("clave de acceso en texto legible").contains(CLAVE_ACCESO_49_DIGITOS);
        assertThat(texto).as("comprador").contains("PERSONA FICTICIA").contains("0000000000");
        assertThat(texto).as("detalle").contains("Consulta general ficticia");
        assertThat(texto).as("total").contains("46.00");
        assertThat(texto).as("ambiente").contains("PRUEBAS");
    }

    @Test
    void elCodigoDeBarrasCodificaExactamenteLaClaveDeAccesoPersistida() {
        var codigo = builder.construirCodigoBarras(CLAVE_ACCESO_49_DIGITOS);
        assertThat(codigo.getCode()).isEqualTo(CLAVE_ACCESO_49_DIGITOS);
        assertThat(codigo.getCode()).hasSize(49);
    }

    @Test
    void noAgregaColumnaDeDescuentoCuandoNingunaLineaLoTiene() throws Exception {
        byte[] pdf = builder.construir(facturaAutorizada(1));
        String texto = textoCompleto(pdf);
        assertThat(texto).doesNotContain("Descuento");
    }

    @Test
    void agregaColumnaDeDescuentoCuandoAlMenosUnaLineaLoTiene() throws Exception {
        Factura factura = facturaAutorizada(1);
        factura.getDetalles().get(0).setDescuento(new BigDecimal("5.00"));
        byte[] pdf = builder.construir(factura);
        String texto = textoCompleto(pdf);
        assertThat(texto).contains("Descuento");
    }

    @Test
    void unaFacturaConMuchosDetallesProduceUnPdfValidoDeVariasPaginas() throws Exception {
        Factura factura = facturaAutorizada(120);
        byte[] pdf = builder.construir(factura);

        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages())
                    .as("120 lineas deben desbordar la primera pagina A4")
                    .isGreaterThan(1);

            // Cada linea debe aparecer completa en el texto extraido (ninguna
            // fila se corto a la mitad entre paginas: setSplitRows(false)).
            String texto = textoCompleto(pdf);
            assertThat(texto).contains("Concepto 1 ").contains("Concepto 120 ");
        } finally {
            lector.close();
        }
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private Factura facturaAutorizada(int cantidadLineas) {
        Usuario usuario = Usuario.builder().id(1L).nombre("Fixture").email("f@biopet.test").rol(null).activo(true).build();

        Factura factura = Factura.builder()
                .id(99L)
                .usuario(usuario)
                .fechaEmision(LocalDate.of(2026, 9, 1))
                .estado(EstadoFactura.AUTORIZADA)
                .ambiente(AmbienteSri.PRUEBAS)
                .establecimiento("001")
                .puntoEmisionCodigo("001")
                .secuencial(42L)
                .codigoNumerico("12345678")
                .claveAcceso(CLAVE_ACCESO_49_DIGITOS)
                .estadoAutorizacion(com.biopet.facturacion.entity.EstadoAutorizacionSri.AUT)
                .numeroAutorizacion(CLAVE_ACCESO_49_DIGITOS)
                .fechaAutorizacion(Instant.parse("2026-09-01T15:30:00Z"))
                .compradorTipoIdentificacion(TipoIdentificacionSri.CEDULA)
                .compradorIdentificacion("0000000000")
                .compradorRazonSocial("PERSONA FICTICIA")
                .compradorDireccion("Direccion ficticia")
                .compradorEmail("comprador@biopet.test")
                .compradorTelefono("0999999999")
                .emisorRuc("0900000000001")
                .emisorRazonSocial("BIOPET CIA LTDA")
                .emisorNombreComercial("BIOPET")
                .emisorDireccionMatriz("Direccion matriz ficticia")
                .emisorDireccionEstablecimiento("Sucursal ficticia")
                .emisorObligadoContabilidad(true)
                .totalSinImpuestos(new BigDecimal("40.00"))
                .totalDescuento(BigDecimal.ZERO)
                .totalImpuestos(new BigDecimal("6.00"))
                .importeTotal(new BigDecimal("46.00"))
                .moneda("DOLAR")
                .creadoEn(Instant.now())
                .actualizadoEn(Instant.now())
                .build();

        List<FacturaDetalle> detalles = new ArrayList<>();
        for (int i = 1; i <= cantidadLineas; i++) {
            FacturaDetalle detalle = FacturaDetalle.builder()
                    .factura(factura)
                    .linea(i)
                    .codigoPrincipal("CPT-" + i)
                    .descripcion(cantidadLineas == 1 ? "Consulta general ficticia" : "Concepto " + i + " ficticio")
                    .cantidad(new BigDecimal("2.000000"))
                    .precioUnitario(new BigDecimal("20.000000"))
                    .descuento(BigDecimal.ZERO)
                    .precioTotalSinImpuesto(new BigDecimal("40.00"))
                    .impuestoCodigo(CodigoImpuestoSri.IVA)
                    .impuestoCodigoPorcentaje("4")
                    .impuestoTarifa(new BigDecimal("15.00"))
                    .baseImponible(new BigDecimal("40.00"))
                    .impuestoValor(new BigDecimal("6.00"))
                    .build();
            detalles.add(detalle);
        }
        factura.setDetalles(detalles);

        FacturaPago pago = FacturaPago.builder()
                .factura(factura)
                .formaPago(FormaPagoSri.TARJETA_DEBITO)
                .total(new BigDecimal("46.00"))
                .build();
        factura.setPagos(List.of(pago));

        return factura;
    }

    private static String textoCompleto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder acumulado = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                acumulado.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return acumulado.toString();
        } finally {
            lector.close();
        }
    }
}
