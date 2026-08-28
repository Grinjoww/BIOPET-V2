package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * Flujo completo contra PostgreSQL real: borrador -> emitir -> generar XML ->
 * validar -> persistir -> recargar de BYTEA -> volver a validar.
 *
 * <p>No es {@code @Transactional}: hace falta que cada paso confirme para poder
 * recargar el documento como lo haria una peticion posterior.
 */
@SpringBootTest
class FacturaXmlServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaXsdValidator xsdValidator;
    @Autowired PlatformTransactionManager transactionManager;

    // ==================================================================
    // Flujo principal
    // ==================================================================

    @Test
    void deBorradorAXmlGeneradoPersistidoYValidoAlRecargarlo() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

        FacturaDocumento documento = xmlService.generarXml(emitida.getId());

        // --- Persistencia ---
        assertThat(documento.getId()).isNotNull();
        assertThat(documento.getTipo()).isEqualTo(TipoDocumentoFactura.XML_GENERADO);
        assertThat(documento.getBytes()).isEqualTo(documento.getContenido().length);
        assertThat(documento.getSha256())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(FacturaXmlService.sha256(documento.getContenido()));

        // --- Se recarga desde BYTEA, como haria otra peticion ---
        FacturaDocumento recargado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(emitida.getId(), TipoDocumentoFactura.XML_GENERADO)
                .orElseThrow();
        assertThat(recargado.getContenido()).isEqualTo(documento.getContenido());
        assertThatCode(() -> xsdValidator.validar(recargado.getContenido()))
                .doesNotThrowAnyException();

        String xml = new String(recargado.getContenido(), StandardCharsets.UTF_8);

        // --- Cabecera y atributos ---
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<factura id=\"comprobante\" version=\"2.1.0\">");
        assertThat(xml).doesNotContain("Signature");

        // --- Numeracion ---
        assertThat(xml).contains("<claveAcceso>" + emitida.getClaveAcceso() + "</claveAcceso>");
        assertThat(xml).contains("<estab>" + escenario.punto.getEstablecimiento() + "</estab>");
        assertThat(xml).contains("<ptoEmi>001</ptoEmi>");
        assertThat(xml).contains("<secuencial>000000001</secuencial>");
        assertThat(xml).contains("<ambiente>1</ambiente>");
        assertThat(xml).contains("<codDoc>01</codDoc>");

        // --- Snapshot del emisor ---
        assertThat(xml).contains("<ruc>" + escenario.emisor.getRuc() + "</ruc>");
        assertThat(xml).contains("<razonSocial>" + escenario.emisor.getRazonSocial() + "</razonSocial>");
        assertThat(xml).contains("<dirMatriz>Direccion matriz ficticia</dirMatriz>");
        assertThat(xml).contains("<dirEstablecimiento>Sucursal ficticia</dirEstablecimiento>");
        assertThat(xml).contains("<obligadoContabilidad>SI</obligadoContabilidad>");

        // --- Snapshot del comprador ---
        assertThat(xml).contains("<tipoIdentificacionComprador>05</tipoIdentificacionComprador>");
        assertThat(xml).contains("<identificacionComprador>0000000000</identificacionComprador>");
        assertThat(xml).contains("<razonSocialComprador>PERSONA FICTICIA</razonSocialComprador>");

        // --- Detalles, impuestos y totales (2 x 20.00 + 15% = 46.00) ---
        assertThat(xml).contains("<codigoPrincipal>" + escenario.concepto.getCodigo() + "</codigoPrincipal>");
        assertThat(xml).contains("<cantidad>2.000000</cantidad>");
        assertThat(xml).contains("<precioUnitario>20.000000</precioUnitario>");
        assertThat(xml).contains("<precioTotalSinImpuesto>40.00</precioTotalSinImpuesto>");
        assertThat(xml).contains("<tarifa>15.00</tarifa>");
        assertThat(xml).contains("<totalSinImpuestos>40.00</totalSinImpuestos>");
        assertThat(xml).contains("<importeTotal>46.00</importeTotal>");
        assertThat(xml).contains("<moneda>DOLAR</moneda>");

        // --- Pagos ---
        assertThat(xml).contains("<formaPago>16</formaPago>");
        assertThat(xml).contains("<total>46.00</total>");

        // --- La factura no se toco al generar el XML ---
        Factura releida = facturaRepository.findById(emitida.getId()).orElseThrow();
        assertThat(releida.getSecuencial()).isEqualTo(emitida.getSecuencial());
        assertThat(releida.getClaveAcceso()).isEqualTo(emitida.getClaveAcceso());
        assertThat(releida.getCodigoNumerico()).isEqualTo(emitida.getCodigoNumerico());
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
    }

    @Test
    void unaFacturaEnBorradorNoGeneraXml() {
        Escenario escenario = new Escenario();
        Factura borrador = escenario.borrador();

        assertThatThrownBy(() -> xmlService.generarXml(borrador.getId()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("BORRADOR");

        assertThat(facturaDocumentoRepository.findAllByFactura_Id(borrador.getId())).isEmpty();
    }

    @Test
    void argumentosInvalidosSeRechazan() {
        assertThatThrownBy(() -> xmlService.generarXml(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> xmlService.generarXml(987_654_321L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ==================================================================
    // Snapshot: la prueba critica
    // ==================================================================

    @Test
    void elXmlUsaLosSnapshotsDeLaEmisionAunqueTodoLoVivoHayaCambiado() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

        String rucOriginal = emitida.getEmisorRuc();
        String razonEmisorOriginal = emitida.getEmisorRazonSocial();
        String descripcionOriginal = escenario.concepto.getDescripcion();

        // Cambia TODO lo que alimento la factura, DESPUES de emitir.
        DatosFacturacion datos = datosFacturacionRepository.findById(escenario.datos.getId()).orElseThrow();
        datos.setRazonSocial("COMPRADOR CAMBIADO");
        datos.setDireccion("DIRECCION CAMBIADA");
        datosFacturacionRepository.saveAndFlush(datos);

        Usuario usuario = usuarioRepository.findById(escenario.usuario.getId()).orElseThrow();
        usuario.setNombre("USUARIO RENOMBRADO");
        usuarioRepository.saveAndFlush(usuario);

        EmisorFiscal emisor = emisorFiscalRepository.findById(escenario.emisor.getId()).orElseThrow();
        emisor.setRazonSocial("EMISOR RENOMBRADO");
        emisor.setDireccionMatriz("OTRA DIRECCION MATRIZ");
        emisor.setRuc("0111111111001");
        emisorFiscalRepository.saveAndFlush(emisor);

        ConceptoFacturable concepto =
                conceptoFacturableRepository.findById(escenario.concepto.getId()).orElseThrow();
        concepto.setDescripcion("DESCRIPCION CAMBIADA");
        concepto.setPrecioUnitario(new BigDecimal("999.000000"));
        conceptoFacturableRepository.saveAndFlush(concepto);

        TarifaImpuesto tarifa = tarifaImpuestoRepository.findById(escenario.tarifa.getId()).orElseThrow();
        tarifa.setTarifa(new BigDecimal("25.00"));
        tarifaImpuestoRepository.saveAndFlush(tarifa);

        // Y SOLO ahora se genera el XML.
        String xml = new String(xmlService.generarXml(emitida.getId()).getContenido(),
                StandardCharsets.UTF_8);

        // Manda el snapshot congelado al emitir, no el dato vivo.
        assertThat(xml).contains("<ruc>" + rucOriginal + "</ruc>");
        assertThat(xml).contains("<razonSocial>" + razonEmisorOriginal + "</razonSocial>");
        assertThat(xml).contains("<dirMatriz>Direccion matriz ficticia</dirMatriz>");
        assertThat(xml).contains("<razonSocialComprador>PERSONA FICTICIA</razonSocialComprador>");
        assertThat(xml).contains("<descripcion>" + descripcionOriginal + "</descripcion>");
        assertThat(xml).contains("<precioUnitario>20.000000</precioUnitario>");
        assertThat(xml).contains("<tarifa>15.00</tarifa>");
        assertThat(xml).contains("<importeTotal>46.00</importeTotal>");

        assertThat(xml).doesNotContain("EMISOR RENOMBRADO")
                .doesNotContain("OTRA DIRECCION MATRIZ")
                .doesNotContain("0111111111001")
                .doesNotContain("COMPRADOR CAMBIADO")
                .doesNotContain("DIRECCION CAMBIADA")
                .doesNotContain("DESCRIPCION CAMBIADA")
                .doesNotContain("999.000000")
                .doesNotContain("25.00");
    }

    // ==================================================================
    // Idempotencia y concurrencia
    // ==================================================================

    @Test
    void generarDosVecesDevuelveElMismoDocumentoYUnaSolaFila() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

        FacturaDocumento primero = xmlService.generarXml(emitida.getId());
        FacturaDocumento segundo = xmlService.generarXml(emitida.getId());

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getSha256()).isEqualTo(primero.getSha256());
        assertThat(segundo.getContenido()).isEqualTo(primero.getContenido());
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(emitida.getId())).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ? AND tipo = 'XML_GENERADO'",
                Integer.class, emitida.getId())).isEqualTo(1);
    }

    @Test
    void unXmlGuardadoQueNoCorrespondeASuHashSeDenuncia() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        xmlService.generarXml(emitida.getId());

        // Se corrompe el contenido dejando el hash antiguo.
        jdbc.update("UPDATE factura_documentos SET contenido = ?::bytea "
                        + "WHERE factura_id = ? AND tipo = 'XML_GENERADO'",
                "\\x00010203", emitida.getId());

        assertThatThrownBy(() -> xmlService.generarXml(emitida.getId()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("corrupto");
    }

    @Test
    void dosGeneracionesSimultaneasProducenUnSoloDocumento() throws Exception {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

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
                    // Transaccion propia por worker: concurrencia real.
                    return new TransactionTemplate(transactionManager).execute(status ->
                            xmlService.generarXml(emitida.getId()).getSha256());
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
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ?",
                Integer.class, emitida.getId())).isEqualTo(1);
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

        Factura borrador() {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }

        Factura emitir() {
            Factura borrador = borrador();
            return emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), punto.getId(), AmbienteSri.PRUEBAS));
        }
    }
}
