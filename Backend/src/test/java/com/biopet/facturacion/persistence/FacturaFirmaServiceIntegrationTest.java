package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.firma.CertificadoPruebaFactory;
import com.biopet.facturacion.firma.FirmaXadesVerificador;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cadena local completa contra PostgreSQL real:
 * BORRADOR -> EMITIDA -> XML_GENERADO -> XML_FIRMADO.
 *
 * <p>El certificado es autofirmado y se genera en caliente bajo {@code target/}.
 * Prueba la mecanica de la firma; NO permite afirmar nada sobre la aceptacion
 * del SRI, que exige un certificado emitido por una entidad acreditada.
 */
@SpringBootTest
class FacturaFirmaServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);
    private static final Path P12 = Path.of("target", "tmp", "firma-integracion.p12");

    @DynamicPropertySource
    static void certificadoDePrueba(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
    }

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaFirmaService firmaService;
    @Autowired FirmaXadesVerificador verificador;
    @Autowired FacturaXsdValidator xsdValidator;
    @Autowired PlatformTransactionManager transactionManager;

    // ==================================================================
    // Cadena completa
    // ==================================================================

    @Test
    void deBorradorAXmlFirmadoConservandoTodoLoAnterior() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        FacturaDocumento generado = xmlService.generarXml(emitida.getId());
        byte[] bytesGenerados = generado.getContenido().clone();

        FacturaDocumento firmado = firmaService.firmarFactura(emitida.getId());

        // --- El documento firmado ---
        assertThat(firmado.getTipo()).isEqualTo(TipoDocumentoFactura.XML_FIRMADO);
        assertThat(firmado.getBytes()).isEqualTo(firmado.getContenido().length);
        assertThat(firmado.getSha256())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(FacturaXmlService.sha256(firmado.getContenido()));

        String xml = new String(firmado.getContenido(), StandardCharsets.UTF_8);
        assertThat(xml).contains("Signature");
        assertThat(xml).contains("id=\"comprobante\"").contains("version=\"2.1.0\"");

        // --- Firma valida y XSD valido, releidos desde BYTEA ---
        FacturaDocumento recargado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(emitida.getId(), TipoDocumentoFactura.XML_FIRMADO)
                .orElseThrow();
        assertThat(verificador.esValida(recargado.getContenido())).isTrue();
        assertThatCode(() -> xsdValidator.validar(recargado.getContenido()))
                .doesNotThrowAnyException();

        // --- XML_GENERADO sigue intacto y ambos coexisten ---
        FacturaDocumento generadoReleido = facturaDocumentoRepository
                .findByFactura_IdAndTipo(emitida.getId(), TipoDocumentoFactura.XML_GENERADO)
                .orElseThrow();
        assertThat(generadoReleido.getContenido()).isEqualTo(bytesGenerados);
        assertThat(generadoReleido.getSha256()).isEqualTo(generado.getSha256());
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(emitida.getId())).hasSize(2);

        // --- Firmar no es emitir: la factura no cambia ---
        Factura releida = facturaRepository.findById(emitida.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getSecuencial()).isEqualTo(emitida.getSecuencial());
        assertThat(releida.getClaveAcceso()).isEqualTo(emitida.getClaveAcceso());
        assertThat(releida.getCodigoNumerico()).isEqualTo(emitida.getCodigoNumerico());
        assertThat(releida.getEstadoRecepcion()).isNull();
        assertThat(releida.getEstadoAutorizacion()).isNull();
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
    }

    @Test
    void elXmlFirmadoContieneLosMismosDatosFiscalesQueElGenerado() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        String generado = new String(xmlService.generarXml(emitida.getId()).getContenido(),
                StandardCharsets.UTF_8);
        String firmado = new String(firmaService.firmarFactura(emitida.getId()).getContenido(),
                StandardCharsets.UTF_8);

        for (String fragmento : List.of(
                "<claveAcceso>" + emitida.getClaveAcceso() + "</claveAcceso>",
                "<secuencial>000000001</secuencial>",
                "<ruc>" + escenario.emisor.getRuc() + "</ruc>",
                "<razonSocialComprador>PERSONA FICTICIA</razonSocialComprador>",
                "<importeTotal>46.00</importeTotal>",
                "<totalSinImpuestos>40.00</totalSinImpuestos>")) {
            assertThat(generado).as("en el generado: %s", fragmento).contains(fragmento);
            assertThat(firmado).as("en el firmado: %s", fragmento).contains(fragmento);
        }
    }

    // ==================================================================
    // Snapshot
    // ==================================================================

    @Test
    void laFirmaCubreElXmlPersistidoAunqueLosDatosVivosHayanCambiado() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        FacturaDocumento generado = xmlService.generarXml(emitida.getId());
        String rucOriginal = emitida.getEmisorRuc();

        // Todo lo vivo cambia DESPUES de generar el XML y ANTES de firmar.
        // RUC nuevo derivado del contador: un literal fijo chocaria con el de
        // otra clase de test, porque la base se comparte y el RUC es unico.
        String rucCambiado = String.format("%010d", 800_000_000L + siguiente()) + "001";
        EmisorFiscal emisor = emisorFiscalRepository.findById(escenario.emisor.getId()).orElseThrow();
        emisor.setRazonSocial("EMISOR RENOMBRADO");
        emisor.setRuc(rucCambiado);
        emisorFiscalRepository.saveAndFlush(emisor);

        DatosFacturacion datos = datosFacturacionRepository.findById(escenario.datos.getId()).orElseThrow();
        datos.setRazonSocial("COMPRADOR CAMBIADO");
        datosFacturacionRepository.saveAndFlush(datos);

        ConceptoFacturable concepto =
                conceptoFacturableRepository.findById(escenario.concepto.getId()).orElseThrow();
        concepto.setPrecioUnitario(new BigDecimal("999.000000"));
        conceptoFacturableRepository.saveAndFlush(concepto);

        FacturaDocumento firmado = firmaService.firmarFactura(emitida.getId());
        String xml = new String(firmado.getContenido(), StandardCharsets.UTF_8);

        // Se firmo el XML persistido, no uno reconstruido desde la base.
        assertThat(xml).contains("<ruc>" + rucOriginal + "</ruc>");
        assertThat(xml).contains("<razonSocialComprador>PERSONA FICTICIA</razonSocialComprador>");
        assertThat(xml).contains("<importeTotal>46.00</importeTotal>");
        assertThat(xml).doesNotContain("EMISOR RENOMBRADO")
                .doesNotContain(rucCambiado)
                .doesNotContain("COMPRADOR CAMBIADO")
                .doesNotContain("999.000000");

        // El firmado es exactamente el generado mas la firma.
        String generadoTexto = new String(generado.getContenido(), StandardCharsets.UTF_8);
        String sinFirma = xml.substring(0, xml.indexOf("<ds:Signature"));
        assertThat(generadoTexto).startsWith(sinFirma);
        assertThat(verificador.esValida(firmado.getContenido())).isTrue();
    }

    // ==================================================================
    // Idempotencia y concurrencia
    // ==================================================================

    @Test
    void firmarDosVecesDevuelveElMismoDocumentoYNoVuelveAFirmar() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        xmlService.generarXml(emitida.getId());

        FacturaDocumento primero = firmaService.firmarFactura(emitida.getId());
        FacturaDocumento segundo = firmaService.firmarFactura(emitida.getId());

        // Si se volviese a firmar, el SigningTime cambiaria y con el los bytes y
        // el hash, aunque el comprobante fuese el mismo.
        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getSha256()).isEqualTo(primero.getSha256());
        assertThat(segundo.getContenido()).isEqualTo(primero.getContenido());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ? AND tipo = 'XML_FIRMADO'",
                Integer.class, emitida.getId())).isEqualTo(1);
    }

    @Test
    void dosFirmasSimultaneasProducenUnSoloDocumento() throws Exception {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        xmlService.generarXml(emitida.getId());

        int workers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch listos = new CountDownLatch(workers);
        CountDownLatch arranque = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futuros.add(executor.submit(() -> {
                    listos.countDown();
                    arranque.await(30, TimeUnit.SECONDS);
                    return new TransactionTemplate(transactionManager).execute(status ->
                            firmaService.firmarFactura(emitida.getId()).getSha256());
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            arranque.countDown();

            List<String> hashes = new ArrayList<>();
            List<Throwable> errores = new ArrayList<>();
            for (Future<String> futuro : futuros) {
                try {
                    hashes.add(futuro.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    errores.add(e.getCause());
                }
            }

            assertThat(errores).isEmpty();
            assertThat(hashes).hasSize(2);
            assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        } finally {
            arranque.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ? AND tipo = 'XML_FIRMADO'",
                Integer.class, emitida.getId())).isEqualTo(1);
    }

    // ==================================================================
    // Integridad y precondiciones
    // ==================================================================

    @Test
    void sinXmlGeneradoNoSePuedeFirmar() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

        assertThatThrownBy(() -> firmaService.firmarFactura(emitida.getId()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("no tiene XML generado");

        assertThat(facturaDocumentoRepository.findAllByFactura_Id(emitida.getId())).isEmpty();
    }

    @Test
    void unXmlGeneradoCorruptoNoSeFirma() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        xmlService.generarXml(emitida.getId());

        // Se corrompe el contenido dejando el hash antiguo: firmarlo lo
        // legitimaria.
        jdbc.update("UPDATE factura_documentos SET contenido = ?::bytea "
                        + "WHERE factura_id = ? AND tipo = 'XML_GENERADO'",
                "\\x00010203", emitida.getId());

        assertThatThrownBy(() -> firmaService.firmarFactura(emitida.getId()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("corrupto");

        assertThat(facturaDocumentoRepository
                .findByFactura_IdAndTipo(emitida.getId(), TipoDocumentoFactura.XML_FIRMADO))
                .isEmpty();
    }

    @Test
    void argumentosInvalidosSeRechazan() {
        assertThatThrownBy(() -> firmaService.firmarFactura(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> firmaService.firmarFactura(987_654_321L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private final class Escenario {
        private final EmisorFiscal emisor = nuevoEmisor();
        private final PuntoEmision punto = nuevoPunto(emisor);
        private final ConceptoFacturable concepto;
        private final TarifaImpuesto tarifa;
        private final Usuario usuario = nuevoUsuario();
        private final DatosFacturacion datos;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            String codigoPorcentaje = nuevoCodigoPorcentaje();
            tarifa = nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
            concepto = nuevoConcepto(codigoPorcentaje, "20.000000");
            datos = nuevosDatos(usuario, TipoIdentificacionSri.CEDULA, "0000000000", "PERSONA FICTICIA");
        }

        Factura emitir() {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), punto.getId(), AmbienteSri.PRUEBAS));
        }
    }
}
