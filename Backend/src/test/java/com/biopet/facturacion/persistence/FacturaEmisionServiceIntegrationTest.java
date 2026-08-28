package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.ClaveAccesoGenerator;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.ConceptoFacturableNoDisponibleException;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import com.biopet.facturacion.exception.DatosFacturacionInvalidosException;
import com.biopet.facturacion.exception.PagosFacturaInvalidosException;
import com.biopet.facturacion.exception.SecuencialAgotadoException;
import com.biopet.facturacion.exception.SecuencialNoConfiguradoException;
import com.biopet.facturacion.exception.TarifaImpuestoAmbiguaException;
import com.biopet.facturacion.exception.TarifaImpuestoNoConfiguradaException;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Emision local BORRADOR -> EMITIDA contra PostgreSQL real.
 *
 * <p>No es {@code @Transactional} a proposito: la emision se apoya en una
 * transaccion corta propia y hay que poder distinguir lo confirmado de lo
 * revertido.
 *
 * <p>Importa {@link CodigoNumericoDeterministaConfig} para poder afirmar la
 * clave de acceso EXACTA, no solo su longitud.
 */
@SpringBootTest
@Import(CodigoNumericoDeterministaConfig.class)
class FacturaEmisionServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    @Autowired FacturaEmisionService emisionService;
    @Autowired ClaveAccesoGenerator claveAccesoGenerator;

    // ==================================================================
    // Emision simple
    // ==================================================================

    @Test
    void emitirUnBorradorLoNumeraLoCongelaYLoDejaEmitido() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(emitida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(emitida.getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
        assertThat(emitida.getSecuencial()).isEqualTo(1L);
        assertThat(emitida.getEstablecimiento()).isEqualTo(escenario.punto.getEstablecimiento());
        assertThat(emitida.getPuntoEmisionCodigo()).isEqualTo("001");
        assertThat(emitida.getCodigoNumerico())
                .isEqualTo(CodigoNumericoDeterministaConfig.CODIGO)
                .hasSize(8);
        assertThat(emitida.getMoneda()).isEqualTo("DOLAR");

        // Clave de acceso: 49 digitos, valida segun el modulo 11 de la Fase 2,
        // y exactamente la que corresponde a estos datos.
        String clave = emitida.getClaveAcceso();
        assertThat(clave).hasSize(49).containsOnlyDigits();
        assertThat(claveAccesoGenerator.esValida(clave)).isTrue();
        assertThat(clave).isEqualTo("15092026" + "01" + escenario.emisor.getRuc() + "1"
                + escenario.punto.getEstablecimiento() + "001" + "000000001"
                + CodigoNumericoDeterministaConfig.CODIGO + "1" + clave.charAt(48));

        // El contador avanzo exactamente uno.
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);

        // Totales: 2 x 20.00 = 40.00 + 15% = 46.00
        assertThat(emitida.getTotalSinImpuestos()).isEqualByComparingTo("40.00");
        assertThat(emitida.getTotalImpuestos()).isEqualByComparingTo("6.00");
        assertThat(emitida.getImporteTotal()).isEqualByComparingTo("46.00");
        assertThat(emitida.getTotalDescuento()).isEqualByComparingTo("0.00");

        // Snapshot del emisor congelado.
        assertThat(emitida.getEmisorRuc()).isEqualTo(escenario.emisor.getRuc());
        assertThat(emitida.getEmisorRazonSocial()).isEqualTo(escenario.emisor.getRazonSocial());
        assertThat(emitida.getEmisorDireccionMatriz()).isEqualTo(escenario.emisor.getDireccionMatriz());
        assertThat(emitida.getEmisorDireccionEstablecimiento())
                .isEqualTo(escenario.punto.getDireccionEstablecimiento());
        assertThat(emitida.getEmisorObligadoContabilidad()).isTrue();
        assertThat(emitida.getEmisorRimpe()).isFalse();

        // Y sigue SIN estado SRI: EMITIDA no es ni RECIBIDA ni AUTORIZADA.
        assertThat(emitida.getEstadoRecepcion()).isNull();
        assertThat(emitida.getEstadoAutorizacion()).isNull();
        assertThat(emitida.getNumeroAutorizacion()).isNull();
    }

    @Test
    void variasLineasConImpuestosDistintosSeAgrupanCorrectamente() {
        Escenario escenario = escenario();
        // Segundo par tributario: tarifa 0% (por ejemplo un producto exento).
        String porcentajeCero = nuevoCodigoPorcentaje();
        nuevaTarifa(porcentajeCero, "0.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable exento = nuevoConcepto(
                porcentajeCero, "5.000000", TipoConceptoFacturable.PRODUCTO, true);

        Factura borrador = escenario.nuevoBorrador();
        borradorService.reemplazarDetalles(borrador.getId(), List.of(
                DetalleBorradorCommand.de(escenario.concepto.getId(), new BigDecimal("2")),
                DetalleBorradorCommand.de(exento.getId(), new BigDecimal("3"))));
        // 40.00 gravado (+6.00 IVA) + 15.00 exento = 61.00
        borradorService.reemplazarPagos(borrador.getId(),
                List.of(PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("61.00"))));

        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(emitida.getTotalSinImpuestos()).isEqualByComparingTo("55.00");
        assertThat(emitida.getTotalImpuestos()).isEqualByComparingTo("6.00");
        assertThat(emitida.getImporteTotal()).isEqualByComparingTo("61.00");

        List<FacturaDetalle> detalles =
                facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(emitida.getId());
        assertThat(detalles).hasSize(2);
        assertThat(detalles.get(0).getImpuestoTarifa()).isEqualByComparingTo("15.00");
        assertThat(detalles.get(0).getImpuestoValor()).isEqualByComparingTo("6.00");
        assertThat(detalles.get(1).getImpuestoTarifa()).isEqualByComparingTo("0.00");
        assertThat(detalles.get(1).getImpuestoValor()).isEqualByComparingTo("0.00");
        assertThat(detalles).allSatisfy(detalle ->
                assertThat(detalle.getImpuestoCodigo()).isEqualTo(CodigoImpuestoSri.IVA));
    }

    @Test
    void variasFormasDePagoQueSumanElImporteTotalSonValidas() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", null);
        borradorService.reemplazarPagos(borrador.getId(), List.of(
                PagoBorradorCommand.de(FormaPagoSri.SIN_UTILIZACION_SISTEMA_FINANCIERO,
                        new BigDecimal("16.00")),
                new PagoBorradorCommand(FormaPagoSri.TARJETA_CREDITO,
                        new BigDecimal("30.00"), 30, "dias")));

        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(emitida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(facturaPagoRepository.findAllByFactura_Id(emitida.getId())).hasSize(2);
    }

    @Test
    void pagosDeMenosODeMasImpidenEmitirYNoConsumenNumeracion() {
        for (String importePago : new String[]{"45.99", "46.01"}) {
            Escenario escenario = escenario();
            Factura borrador = escenario.borradorCon("2", "20.000000", importePago);

            assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                    .as("pago de %s frente a un total de 46.00", importePago)
                    .isInstanceOf(PagosFacturaInvalidosException.class);

            // Falla ANTES de reservar: el contador ni se entera.
            assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
            assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getEstado())
                    .isEqualTo(EstadoFactura.BORRADOR);
        }
    }

    // ==================================================================
    // Validaciones previas al secuencial
    // ==================================================================

    @Test
    void sinCompradorNoSeEmiteYElContadorNoSeMueve() {
        Escenario escenario = escenario();
        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
        borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(escenario.concepto.getId(), BigDecimal.ONE)));

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(DatosFacturacionInvalidosException.class)
                .hasMessageContaining("tipo de identificacion del comprador");

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unBorradorSinLineasNoSePuedeEmitir() {
        Escenario escenario = escenario();
        Usuario usuario = nuevoUsuario();
        DatosFacturacion datos = nuevosDatos(usuario);
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
        borradorService.seleccionarComprador(borrador.getId(), datos.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene lineas");

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unPuntoDeEmisionInactivoNoEmite() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
        jdbc.update("UPDATE punto_emision SET activo = FALSE WHERE id = ?", escenario.punto.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("inactivo");

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unEmisorInactivoNoEmite() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
        jdbc.update("UPDATE emisor_fiscal SET activo = FALSE WHERE id = ?", escenario.emisor.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("emisor");

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void sinContadorConfiguradoParaElAmbienteSePropagaLaExcepcionDeLaFase4B() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        // El contador de PRODUCCION no existe: solo se creo el de PRUEBAS.
        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRODUCCION)))
                .isInstanceOf(SecuencialNoConfiguradoException.class);

        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoFactura.BORRADOR);
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void conLaSerieAgotadaLaFacturaSigueSiendoBorrador() {
        Escenario escenario = escenario(999_999_999L);
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(SecuencialAgotadoException.class);

        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoFactura.BORRADOR);
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(999_999_999L);
    }

    @Test
    void sinTarifaOConTarifasSolapadasNoSeLlegaAReservar() {
        // Tarifa sin cobertura para la fecha de emision.
        Escenario sinTarifa = escenario();
        String porcentajeCaduco = nuevoCodigoPorcentaje();
        nuevaTarifa(porcentajeCaduco, "12.00", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31));
        ConceptoFacturable conceptoCaduco = nuevoConcepto(porcentajeCaduco, "10.000000");
        Factura borradorSinTarifa = sinTarifa.borradorCon("2", "20.000000", "46.00");
        // Se cambia la linea por SQL para saltarse la validacion del borrador y
        // llegar con el problema hasta la emision, que es donde importa.
        jdbc.update("UPDATE factura_detalles SET concepto_facturable_id = ? WHERE factura_id = ?",
                conceptoCaduco.getId(), borradorSinTarifa.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borradorSinTarifa.getId(), sinTarifa.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(TarifaImpuestoNoConfiguradaException.class);
        assertThat(ultimoSecuencial(sinTarifa.punto, AmbienteSri.PRUEBAS)).isZero();

        // Dos tarifas que solapan la misma fecha.
        Escenario ambigua = escenario();
        Factura borradorAmbiguo = ambigua.borradorCon("2", "20.000000", "46.00");
        nuevaTarifa(ambigua.codigoPorcentaje, "20.00", LocalDate.of(2024, 1, 1), null);

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borradorAmbiguo.getId(), ambigua.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(TarifaImpuestoAmbiguaException.class);
        assertThat(ultimoSecuencial(ambigua.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unConceptoDadoDeBajaEntreElBorradorYLaEmisionImpideEmitir() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        // El administrador retira el concepto del catalogo despues de guardar
        // el borrador. Emitir con el precio viejo seria congelar en un
        // comprobante fiscal algo que la clinica ya no vende.
        jdbc.update("UPDATE concepto_facturable SET activo = FALSE WHERE id = ?",
                escenario.concepto.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConceptoFacturableNoDisponibleException.class);

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    // ==================================================================
    // Snapshots
    // ==================================================================

    @Test
    void elCompradorEmitidoEsElSnapshotDelBorradorNoElDatoExternoDelUltimoMinuto() {
        // Decision de diseno: al emitir NO se relee DatosFacturacion. Lo que
        // vale es lo que el usuario dejo guardado en ESTE borrador; si edita su
        // libreta de datos, debe volver a seleccionarlos explicitamente.
        Escenario escenario = escenario();
        Usuario usuario = nuevoUsuario();
        DatosFacturacion datos = nuevosDatos(usuario, TipoIdentificacionSri.CEDULA,
                "0000000000", "NOMBRE ORIGINAL");
        Factura borrador = escenario.borradorDe(usuario, datos, "46.00");

        // Alguien edita la identidad tributaria DESPUES de armar el borrador.
        datos.setRazonSocial("NOMBRE CAMBIADO");
        datos.setDireccion("DIRECCION CAMBIADA");
        datosFacturacionRepository.saveAndFlush(datos);

        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(emitida.getCompradorRazonSocial()).isEqualTo("NOMBRE ORIGINAL");
        assertThat(emitida.getCompradorDireccion()).isNotEqualTo("DIRECCION CAMBIADA");

        // Y para trasladar el cambio hay que seleccionar de nuevo en un borrador
        // nuevo: entonces si se copia el valor actualizado.
        Factura otroBorrador = escenario.borradorDe(usuario, datos, "46.00");
        assertThat(facturaRepository.findById(otroBorrador.getId()).orElseThrow()
                .getCompradorRazonSocial()).isEqualTo("NOMBRE CAMBIADO");
    }

    @Test
    void cambiarEmisorConceptoOTarifaDespuesNoAlteraLaFacturaEmitida() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));
        String rucOriginal = emitida.getEmisorRuc();
        String razonOriginal = emitida.getEmisorRazonSocial();

        // Cambia TODO lo que alimento la factura.
        EmisorFiscal emisor = emisorFiscalRepository.findById(escenario.emisor.getId()).orElseThrow();
        emisor.setRazonSocial("EMISOR RENOMBRADO");
        emisor.setDireccionMatriz("OTRA DIRECCION");
        emisorFiscalRepository.saveAndFlush(emisor);

        ConceptoFacturable concepto =
                conceptoFacturableRepository.findById(escenario.concepto.getId()).orElseThrow();
        concepto.setDescripcion("DESCRIPCION CAMBIADA");
        concepto.setPrecioUnitario(new BigDecimal("999.000000"));
        conceptoFacturableRepository.saveAndFlush(concepto);

        TarifaImpuesto tarifa = tarifaImpuestoRepository.findById(escenario.tarifa.getId()).orElseThrow();
        tarifa.setTarifa(new BigDecimal("25.00"));
        tarifaImpuestoRepository.saveAndFlush(tarifa);

        Factura releida = facturaRepository.findById(emitida.getId()).orElseThrow();
        assertThat(releida.getEmisorRuc()).isEqualTo(rucOriginal);
        assertThat(releida.getEmisorRazonSocial()).isEqualTo(razonOriginal);
        assertThat(releida.getEmisorRazonSocial()).isNotEqualTo("EMISOR RENOMBRADO");
        assertThat(releida.getImporteTotal()).isEqualByComparingTo("46.00");

        FacturaDetalle detalle =
                facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(emitida.getId()).get(0);
        assertThat(detalle.getDescripcion()).isEqualTo(escenario.concepto.getDescripcion());
        assertThat(detalle.getPrecioUnitario()).isEqualByComparingTo("20.000000");
        assertThat(detalle.getImpuestoTarifa()).isEqualByComparingTo("15.00");
    }

    @Test
    void cadaFacturaCongelaLaTarifaVigenteEnSuPropiaFecha() {
        Escenario escenario = escenario();
        String porcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(porcentaje, "12.00", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 12, 31));
        nuevaTarifa(porcentaje, "15.00", LocalDate.of(2024, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(porcentaje, "100.000000");

        // Factura antigua: 100 + 12% = 112.00
        Factura antigua = escenario.borradorEnFecha(
                LocalDate.of(2022, 6, 1), concepto, "112.00");
        Factura emitidaAntigua = emisionService.emitir(new EmitirFacturaCommand(
                antigua.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        // Factura reciente: 100 + 15% = 115.00
        Factura reciente = escenario.borradorEnFecha(
                LocalDate.of(2026, 6, 1), concepto, "115.00");
        Factura emitidaReciente = emisionService.emitir(new EmitirFacturaCommand(
                reciente.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(emitidaAntigua.getImporteTotal()).isEqualByComparingTo("112.00");
        assertThat(emitidaReciente.getImporteTotal()).isEqualByComparingTo("115.00");
        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(emitidaAntigua.getId())
                .get(0).getImpuestoTarifa()).isEqualByComparingTo("12.00");
        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(emitidaReciente.getId())
                .get(0).getImpuestoTarifa()).isEqualByComparingTo("15.00");
    }

    // ==================================================================
    // Idempotencia
    // ==================================================================

    @Test
    void emitirDosVecesDevuelveLaMismaFacturaYNoConsumeOtroNumero() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
        EmitirFacturaCommand orden = new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS);

        Factura primera = emisionService.emitir(orden);
        Factura segunda = emisionService.emitir(orden);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(segunda.getSecuencial()).isEqualTo(primera.getSecuencial());
        assertThat(segunda.getClaveAcceso()).isEqualTo(primera.getClaveAcceso());
        assertThat(segunda.getCodigoNumerico()).isEqualTo(primera.getCodigoNumerico());
        // Lo esencial: un solo numero consumido.
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE punto_emision_id = ?",
                Integer.class, escenario.punto.getId())).isEqualTo(1);
    }

    @Test
    void unaFacturaYaAutorizadaTampocoSeVuelveANumerar() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));
        // El pipeline futuro la habria dejado AUTORIZADA.
        jdbc.update("UPDATE facturas SET estado = 'AUTORIZADA' WHERE id = ?", emitida.getId());

        Factura respuesta = emisionService.emitir(new EmitirFacturaCommand(
                emitida.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(respuesta.getEstado()).isEqualTo(EstadoFactura.AUTORIZADA);
        assertThat(respuesta.getClaveAcceso()).isEqualTo(emitida.getClaveAcceso());
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
    }

    // ==================================================================
    // Validacion de entrada
    // ==================================================================

    @Test
    void losArgumentosMalFormadosSeRechazanAntesDeCargarNada() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
        Long punto = escenario.punto.getId();

        assertThatThrownBy(() -> emisionService.emitir(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> emisionService.emitir(
                new EmitirFacturaCommand(null, punto, AmbienteSri.PRUEBAS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> emisionService.emitir(
                new EmitirFacturaCommand(borrador.getId(), null, AmbienteSri.PRUEBAS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punto de emision");
        assertThatThrownBy(() -> emisionService.emitir(
                new EmitirFacturaCommand(borrador.getId(), punto, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiente");

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unaFacturaOUnPuntoInexistentesSonRecursoNoEncontrado() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                987_654_321L, escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), 987_654_321L, AmbienteSri.PRUEBAS)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unCompradorAMediasImpideEmitir() {
        // Coherencia estructural: tipo + identificacion + razon social.
        for (String columna : new String[]{"comprador_identificacion", "comprador_razon_social"}) {
            Escenario escenario = escenario();
            Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");
            jdbc.update("UPDATE facturas SET " + columna + " = '   ' WHERE id = ?", borrador.getId());

            assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                    .as("comprador sin %s", columna)
                    .isInstanceOf(DatosFacturacionInvalidosException.class);

            assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
        }
    }

    // ==================================================================
    // Configuracion del emisor que la BD acepta pero el XSD del SRI no
    // ==================================================================

    @Test
    void unRucQueLaBdAceptaPeroElSriNoImpideEmitirYNoConsumeNumeracion() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        // 13 digitos: pasa el CHECK de V7. No termina en 001: el XSD lo rechaza.
        // Se cambia por SQL para dejar constancia de que la BD lo admite.
        jdbc.update("UPDATE emisor_fiscal SET ruc = '0999999999123' WHERE id = ?",
                escenario.emisor.getId());
        assertThat(jdbc.queryForObject("SELECT ruc FROM emisor_fiscal WHERE id = ?",
                String.class, escenario.emisor.getId())).isEqualTo("0999999999123");

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("RUC");

        exigirBorradorIntacto(escenario, borrador);
    }

    @Test
    void unAgenteRetencionNoNumericoImpideEmitirYNoConsumeNumeracion() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        // La columna es VARCHAR(20) libre; el XSD exige digitos y maximo 8.
        jdbc.update("UPDATE emisor_fiscal SET agente_retencion_resolucion = 'RES-2024' WHERE id = ?",
                escenario.emisor.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("agente de retencion");

        exigirBorradorIntacto(escenario, borrador);
    }

    @Test
    void unContribuyenteEspecialInvalidoImpideEmitirYNoConsumeNumeracion() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        // "12" cabe en VARCHAR(13) pero el XSD exige minimo 3 alfanumericos.
        jdbc.update("UPDATE emisor_fiscal SET contribuyente_especial = '12' WHERE id = ?",
                escenario.emisor.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("contribuyente especial");

        exigirBorradorIntacto(escenario, borrador);
    }

    @Test
    void unSaltoDeLineaEnLaRazonSocialImpideEmitirYNoConsumeNumeracion() {
        Escenario escenario = escenario();
        Factura borrador = escenario.borradorCon("2", "20.000000", "46.00");

        jdbc.update("UPDATE emisor_fiscal SET razon_social = E'CLINICA\\nFICTICIA' WHERE id = ?",
                escenario.emisor.getId());

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(ConfiguracionFiscalInvalidaException.class)
                .hasMessageContaining("saltos de linea");

        exigirBorradorIntacto(escenario, borrador);
    }

    /**
     * Tras un rechazo por configuracion: ni un numero consumido, ni un rastro de
     * numeracion en la factura, que sigue siendo un borrador editable.
     */
    private void exigirBorradorIntacto(Escenario escenario, Factura borrador) {
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();

        Factura releida = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(releida.getSecuencial()).isNull();
        assertThat(releida.getClaveAcceso()).isNull();
        assertThat(releida.getCodigoNumerico()).isNull();
        assertThat(releida.getAmbiente()).isNull();
    }

    // ==================================================================
    // Escenario reutilizable
    // ==================================================================

    private Escenario escenario() {
        return escenario(0L);
    }

    private Escenario escenario(long ultimoSecuencial) {
        Escenario escenario = new Escenario();
        escenario.emisor = nuevoEmisor();
        escenario.punto = nuevoPunto(escenario.emisor);
        nuevoContador(escenario.punto, AmbienteSri.PRUEBAS, ultimoSecuencial);
        escenario.codigoPorcentaje = nuevoCodigoPorcentaje();
        escenario.tarifa = nuevaTarifa(escenario.codigoPorcentaje, "15.00",
                LocalDate.of(2020, 1, 1), null);
        escenario.concepto = nuevoConcepto(escenario.codigoPorcentaje, "20.000000");
        return escenario;
    }

    /** Configuracion fiscal completa y ficticia para un test. */
    private final class Escenario {
        private EmisorFiscal emisor;
        private PuntoEmision punto;
        private String codigoPorcentaje;
        private TarifaImpuesto tarifa;
        private ConceptoFacturable concepto;

        Factura nuevoBorrador() {
            Usuario usuario = nuevoUsuario();
            DatosFacturacion datos = nuevosDatos(usuario);
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            return borrador;
        }

        /** Borrador con una linea y, si se indica, un pago que cubre el total. */
        Factura borradorCon(String cantidad, String precioIgnorado, String importePago) {
            Factura borrador = nuevoBorrador();
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal(cantidad))));
            if (importePago != null) {
                borradorService.reemplazarPagos(borrador.getId(), List.of(
                        PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal(importePago))));
            }
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }

        Factura borradorDe(Usuario usuario, DatosFacturacion datos, String importePago) {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal(importePago))));
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }

        Factura borradorEnFecha(LocalDate fecha, ConceptoFacturable conceptoLinea, String importePago) {
            Usuario usuario = nuevoUsuario();
            DatosFacturacion datos = nuevosDatos(usuario);
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, fecha));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(conceptoLinea.getId(), BigDecimal.ONE)));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal(importePago))));
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }
    }
}
