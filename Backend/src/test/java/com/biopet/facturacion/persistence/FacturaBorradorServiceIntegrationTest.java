package com.biopet.facturacion.persistence;

import com.biopet.entity.Cita;
import com.biopet.entity.Consulta;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.entity.Vacuna;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.ConceptoFacturableNoDisponibleException;
import com.biopet.facturacion.exception.FacturaNoEditableException;
import com.biopet.facturacion.exception.OrigenClinicoInvalidoException;
import com.biopet.facturacion.exception.TarifaImpuestoAmbiguaException;
import com.biopet.facturacion.exception.TarifaImpuestoNoConfiguradaException;
import com.biopet.facturacion.exception.TitularFacturaInvalidoException;
import com.biopet.facturacion.service.command.ActualizarFacturaBorradorCommand;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo de vida de un BORRADOR contra PostgreSQL real.
 *
 * <p>La clase no es {@code @Transactional}: cada llamada al servicio abre y
 * confirma su propia transaccion, que es como se usara de verdad. Envolverlo
 * todo en una transaccion de test ocultaria justo los problemas que importan
 * (colecciones desincronizadas, DELETE que no se emiten antes de los INSERT).
 */
@SpringBootTest
class FacturaBorradorServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    // ==================================================================
    // Crear
    // ==================================================================

    @Test
    void seCreaUnBorradorMinimoSinCompradorSinLineasYSinPagos() {
        Usuario usuario = nuevoUsuario();

        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));

        assertThat(borrador.getId()).isNotNull();
        assertThat(borrador.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(borrador.getFechaEmision()).isEqualTo(FECHA);
        // Un borrador NO consume numeracion fiscal.
        assertThat(borrador.getSecuencial()).isNull();
        assertThat(borrador.getClaveAcceso()).isNull();
        assertThat(borrador.getCodigoNumerico()).isNull();
        assertThat(borrador.getAmbiente()).isNull();
        assertThat(borrador.getEstadoRecepcion()).isNull();
        assertThat(borrador.getEstadoAutorizacion()).isNull();
        assertThat(borrador.getCompradorIdentificacion()).isNull();
        assertThat(borrador.getImporteTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void unBorradorPuedeLlevarMascotaDelPropioUsuario() {
        Usuario usuario = nuevoUsuario();
        Mascota mascota = nuevaMascota(usuario);

        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));

        assertThat(columnaFactura(borrador.getId(), "mascota_id"))
                .isEqualTo(String.valueOf(mascota.getId()));
    }

    @Test
    void noSePuedeFacturarLaMascotaDeOtroUsuario() {
        Usuario titular = nuevoUsuario();
        Usuario ajeno = nuevoUsuario();
        Mascota mascotaAjena = nuevaMascota(ajeno);

        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(titular.getId(), mascotaAjena.getId(), FECHA)))
                .isInstanceOf(TitularFacturaInvalidoException.class)
                .hasMessageContaining("no pertenece al usuario");
    }

    @Test
    void usuarioInexistenteOFechaAusenteSonRechazados() {
        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(987_654_321L, null, FECHA)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        Usuario usuario = nuevoUsuario();
        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha de emision");
    }

    // ==================================================================
    // Comprador
    // ==================================================================

    @Test
    void seleccionarCompradorCopiaLosDatosAlSnapshotDelBorrador() {
        Usuario usuario = nuevoUsuario();
        DatosFacturacion datos = nuevosDatos(usuario, TipoIdentificacionSri.RUC,
                "9999999999001", "COMERCIAL FICTICIA S.A.");
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));

        Factura conComprador = borradorService.seleccionarComprador(borrador.getId(), datos.getId());

        assertThat(conComprador.getCompradorTipoIdentificacion()).isEqualTo(TipoIdentificacionSri.RUC);
        assertThat(conComprador.getCompradorIdentificacion()).isEqualTo("9999999999001");
        assertThat(conComprador.getCompradorRazonSocial()).isEqualTo("COMERCIAL FICTICIA S.A.");
        assertThat(conComprador.getCompradorDireccion()).isEqualTo(datos.getDireccion());
        assertThat(conComprador.getCompradorEmail()).isEqualTo(datos.getEmailFacturacion());
        assertThat(conComprador.getCompradorTelefono()).isEqualTo(datos.getTelefono());
    }

    @Test
    void elCompradorPuedeSerUnTerceroDistintoDelTitularDeLaFactura() {
        // Maria es la duena de la mascota; la factura va a nombre de su empresa.
        Usuario maria = nuevoUsuario();
        Mascota luna = nuevaMascota(maria);
        DatosFacturacion empresa = nuevosDatos(maria, TipoIdentificacionSri.RUC,
                "1790000000001", "COMERCIAL LOPEZ S.A.");

        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(maria.getId(), luna.getId(), FECHA));
        Factura conComprador = borradorService.seleccionarComprador(borrador.getId(), empresa.getId());

        // El titular funcional sigue siendo Maria...
        assertThat(columnaFactura(conComprador.getId(), "usuario_id"))
                .isEqualTo(String.valueOf(maria.getId()));
        // ...pero el receptor tributario es la empresa.
        assertThat(conComprador.getCompradorRazonSocial()).isEqualTo("COMERCIAL LOPEZ S.A.");
    }

    @Test
    void noSePuedenUsarLosDatosDeFacturacionDeOtroUsuario() {
        Usuario titular = nuevoUsuario();
        Usuario ajeno = nuevoUsuario();
        DatosFacturacion datosAjenos = nuevosDatos(ajeno);
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(titular.getId(), null, FECHA));

        assertThatThrownBy(() ->
                borradorService.seleccionarComprador(borrador.getId(), datosAjenos.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ==================================================================
    // Lineas
    // ==================================================================

    @Test
    void elBackendPoneElPrecioLaDescripcionYElImpuestoDeCadaLinea() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "20.000000");
        Factura borrador = borradorConLineas(List.of(
                DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));

        List<FacturaDetalle> detalles =
                facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId());

        assertThat(detalles).hasSize(1);
        FacturaDetalle detalle = detalles.get(0);
        assertThat(detalle.getLinea()).isEqualTo(1);
        // Nada de esto lo envio quien llamo: sale todo del catalogo.
        assertThat(detalle.getCodigoPrincipal()).isEqualTo(concepto.getCodigo());
        assertThat(detalle.getDescripcion()).isEqualTo(concepto.getDescripcion());
        assertThat(detalle.getPrecioUnitario()).isEqualByComparingTo("20.000000");
        assertThat(detalle.getImpuestoCodigoPorcentaje()).isEqualTo(codigoPorcentaje);
        assertThat(detalle.getImpuestoTarifa()).isEqualByComparingTo("15.00");
        // 2 x 20 = 40; IVA 15% = 6; total 46.
        assertThat(detalle.getPrecioTotalSinImpuesto()).isEqualByComparingTo("40.00");
        assertThat(detalle.getImpuestoValor()).isEqualByComparingTo("6.00");

        Factura releida = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(releida.getTotalSinImpuestos()).isEqualByComparingTo("40.00");
        assertThat(releida.getTotalImpuestos()).isEqualByComparingTo("6.00");
        assertThat(releida.getImporteTotal()).isEqualByComparingTo("46.00");
    }

    @Test
    void reemplazarLasLineasBorraLasAnterioresYRenumeraDesdeUno() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "10.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable uno = nuevoConcepto(codigoPorcentaje, "10.000000");
        ConceptoFacturable dos = nuevoConcepto(codigoPorcentaje, "5.000000");

        Factura borrador = borradorConLineas(List.of(
                DetalleBorradorCommand.de(uno.getId(), BigDecimal.ONE),
                DetalleBorradorCommand.de(dos.getId(), BigDecimal.ONE),
                DetalleBorradorCommand.de(uno.getId(), BigDecimal.ONE)));
        assertThat(facturaDetalleRepository.countByFactura_Id(borrador.getId())).isEqualTo(3);

        borradorService.reemplazarDetalles(borrador.getId(), List.of(
                DetalleBorradorCommand.de(dos.getId(), new BigDecimal("4"))));

        List<FacturaDetalle> detalles =
                facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId());
        assertThat(detalles).hasSize(1);
        assertThat(detalles.get(0).getLinea()).isEqualTo(1);
        assertThat(detalles.get(0).getPrecioTotalSinImpuesto()).isEqualByComparingTo("20.00");
        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getImporteTotal())
                .isEqualByComparingTo("22.00");
    }

    @Test
    void unConceptoDadoDeBajaNoSePuedeAnadir() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable inactivo =
                nuevoConcepto(codigoPorcentaje, "10.000000", TipoConceptoFacturable.PRODUCTO, false);
        Factura borrador = nuevoBorrador();

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(inactivo.getId(), BigDecimal.ONE))))
                .isInstanceOf(ConceptoFacturableNoDisponibleException.class);
    }

    @Test
    void unDescuentoSeAplicaAntesDelImpuesto() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "100.000000");

        Factura borrador = borradorConLineas(List.of(new DetalleBorradorCommand(
                concepto.getId(), BigDecimal.ONE, new BigDecimal("20.00"), null, null)));

        FacturaDetalle detalle =
                facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId()).get(0);
        assertThat(detalle.getDescuento()).isEqualByComparingTo("20.00");
        assertThat(detalle.getPrecioTotalSinImpuesto()).isEqualByComparingTo("80.00");
        assertThat(detalle.getImpuestoValor()).isEqualByComparingTo("12.00");
        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getTotalDescuento())
                .isEqualByComparingTo("20.00");
    }

    // ==================================================================
    // Tarifas
    // ==================================================================

    @Test
    void sinTarifaVigenteParaLaFechaNoSePuedeCalcularLaLinea() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        // Tarifa que caduco antes de la fecha de la factura.
        nuevaTarifa(codigoPorcentaje, "12.00", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31));
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "10.000000");
        Factura borrador = nuevoBorrador();

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(concepto.getId(), BigDecimal.ONE))))
                .isInstanceOf(TarifaImpuestoNoConfiguradaException.class)
                .hasMessageContaining("No hay tarifa vigente");
    }

    @Test
    void dosTarifasQueSolapanLaMismaFechaSeDenuncianEnLugarDeElegirUna() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "12.00", LocalDate.of(2020, 1, 1), null);
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2024, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "10.000000");
        Factura borrador = nuevoBorrador();

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(concepto.getId(), BigDecimal.ONE))))
                .isInstanceOf(TarifaImpuestoAmbiguaException.class)
                .hasMessageContaining("disjuntos");
    }

    @Test
    void cambiarLaFechaDelBorradorCambiaLaTarifaAplicada() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "12.00", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 12, 31));
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2024, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "100.000000");

        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(new CrearFacturaBorradorCommand(
                usuario.getId(), null, LocalDate.of(2022, 6, 1)));
        borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(concepto.getId(), BigDecimal.ONE)));

        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId())
                .get(0).getImpuestoTarifa()).isEqualByComparingTo("12.00");

        borradorService.actualizar(borrador.getId(),
                new ActualizarFacturaBorradorCommand(null, LocalDate.of(2026, 6, 1)));

        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId())
                .get(0).getImpuestoTarifa()).isEqualByComparingTo("15.00");
        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getImporteTotal())
                .isEqualByComparingTo("115.00");
    }

    // ==================================================================
    // Origen clinico
    // ==================================================================

    @Test
    void seAceptaElOrigenClinicoDeLaPropiaMascota() {
        Usuario usuario = nuevoUsuario();
        Usuario veterinario = nuevoVeterinario();
        Mascota mascota = nuevaMascota(usuario);
        Consulta consulta = nuevaConsulta(mascota, veterinario);
        Vacuna vacuna = nuevaVacuna(mascota, veterinario);
        Cita cita = nuevaCita(mascota, veterinario, EstadoCita.COMPLETADA);

        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
        ConceptoFacturable concepto = nuevoConcepto(codigoPorcentaje, "10.000000");

        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));
        borradorService.reemplazarDetalles(borrador.getId(), List.of(
                new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CONSULTA, consulta.getId()),
                new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.VACUNA, vacuna.getId()),
                new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CITA, cita.getId())));

        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(borrador.getId()))
                .extracting(FacturaDetalle::getOrigenTipo)
                .containsExactly(OrigenDetalleFactura.CONSULTA,
                        OrigenDetalleFactura.VACUNA, OrigenDetalleFactura.CITA);
    }

    @Test
    void seRechazaElOrigenClinicoDeOtraMascota() {
        Usuario usuario = nuevoUsuario();
        Usuario veterinario = nuevoVeterinario();
        Mascota mascota = nuevaMascota(usuario);
        Mascota otraMascota = nuevaMascota(usuario);
        Consulta consultaAjena = nuevaConsulta(otraMascota, veterinario);
        Vacuna vacunaAjena = nuevaVacuna(otraMascota, veterinario);

        ConceptoFacturable concepto = conceptoConTarifa();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CONSULTA, consultaAjena.getId()))))
                .isInstanceOf(OrigenClinicoInvalidoException.class)
                .hasMessageContaining("pertenece a la mascota");

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.VACUNA, vacunaAjena.getId()))))
                .isInstanceOf(OrigenClinicoInvalidoException.class);
    }

    @Test
    void soloSePuedeFacturarUnaCitaCompletada() {
        Usuario usuario = nuevoUsuario();
        Usuario veterinario = nuevoVeterinario();
        Mascota mascota = nuevaMascota(usuario);
        Cita programada = nuevaCita(mascota, veterinario, EstadoCita.PROGRAMADA);
        Cita cancelada = nuevaCita(mascota, veterinario, EstadoCita.CANCELADA);

        ConceptoFacturable concepto = conceptoConTarifa();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));

        for (Cita cita : List.of(programada, cancelada)) {
            assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                            OrigenDetalleFactura.CITA, cita.getId()))))
                    .as("cita %s", cita.getEstado())
                    .isInstanceOf(OrigenClinicoInvalidoException.class)
                    .hasMessageContaining("COMPLETADA");
        }
    }

    @Test
    void unaFacturaSinMascotaNoAdmiteOrigenClinico() {
        Usuario usuario = nuevoUsuario();
        Usuario veterinario = nuevoVeterinario();
        Mascota mascota = nuevaMascota(usuario);
        Consulta consulta = nuevaConsulta(mascota, veterinario);

        ConceptoFacturable concepto = conceptoConTarifa();
        // Borrador de productos, sin contexto clinico: es un caso valido.
        Factura sinMascota = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(sinMascota.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CONSULTA, consulta.getId()))))
                .isInstanceOf(OrigenClinicoInvalidoException.class)
                .hasMessageContaining("no tiene mascota");

        // Pero sin origen si acepta lineas.
        borradorService.reemplazarDetalles(sinMascota.getId(),
                List.of(DetalleBorradorCommand.de(concepto.getId(), BigDecimal.ONE)));
        assertThat(facturaDetalleRepository.countByFactura_Id(sinMascota.getId())).isEqualTo(1);
    }

    @Test
    void quitarLaMascotaDeUnBorradorConOrigenClinicoFalla() {
        Usuario usuario = nuevoUsuario();
        Usuario veterinario = nuevoVeterinario();
        Mascota mascota = nuevaMascota(usuario);
        Consulta consulta = nuevaConsulta(mascota, veterinario);
        ConceptoFacturable concepto = conceptoConTarifa();

        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));
        borradorService.reemplazarDetalles(borrador.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CONSULTA, consulta.getId())));

        assertThatThrownBy(() -> borradorService.actualizar(borrador.getId(),
                new ActualizarFacturaBorradorCommand(null, FECHA)))
                .isInstanceOf(OrigenClinicoInvalidoException.class);
    }

    @Test
    void unOrigenAMediasTipoSinIdOAlRevesEsRechazado() {
        Usuario usuario = nuevoUsuario();
        Mascota mascota = nuevaMascota(usuario);
        ConceptoFacturable concepto = conceptoConTarifa();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA));

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), BigDecimal.ONE, null,
                        OrigenDetalleFactura.CONSULTA, null))))
                .isInstanceOf(OrigenClinicoInvalidoException.class)
                .hasMessageContaining("tipo e id a la vez");
    }

    // ==================================================================
    // Pagos
    // ==================================================================

    @Test
    void unBorradorAdmiteVariasFormasDePagoYSePuedenReemplazar() {
        Factura borrador = nuevoBorrador();

        borradorService.reemplazarPagos(borrador.getId(), List.of(
                PagoBorradorCommand.de(FormaPagoSri.SIN_UTILIZACION_SISTEMA_FINANCIERO,
                        new BigDecimal("10.00")),
                new PagoBorradorCommand(FormaPagoSri.TARJETA_CREDITO,
                        new BigDecimal("36.00"), 30, "dias")));

        assertThat(facturaPagoRepository.findAllByFactura_Id(borrador.getId())).hasSize(2);

        borradorService.reemplazarPagos(borrador.getId(), List.of(
                PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));

        assertThat(facturaPagoRepository.findAllByFactura_Id(borrador.getId()))
                .singleElement()
                .satisfies(pago -> {
                    assertThat(pago.getFormaPago()).isEqualTo(FormaPagoSri.TARJETA_DEBITO);
                    assertThat(pago.getTotal()).isEqualByComparingTo("46.00");
                });
    }

    @Test
    void enBorradorLosPagosNoTienenQueCuadrarTodavia() {
        // Un borrador a medio armar debe poder guardarse. La igualdad
        // SUM(pagos) == importeTotal se exige al emitir, no antes.
        Factura borrador = nuevoBorrador();

        borradorService.reemplazarPagos(borrador.getId(), List.of(
                PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("1.00"))));

        assertThat(facturaPagoRepository.findAllByFactura_Id(borrador.getId())).hasSize(1);
    }

    // ==================================================================
    // Inmutabilidad tras emitir
    // ==================================================================

    @Test
    void unaFacturaQueYaNoEsBorradorNoAdmiteNingunaEdicion() {
        Factura borrador = nuevoBorrador();
        // Se fuerza el estado por SQL para no depender aqui del servicio de
        // emision: lo que se prueba es la guarda del servicio de borrador.
        jdbc.update("UPDATE facturas SET estado = 'EMITIDA' WHERE id = ?", borrador.getId());
        Long id = borrador.getId();

        assertThatThrownBy(() -> borradorService.actualizar(id,
                new ActualizarFacturaBorradorCommand(null, FECHA)))
                .isInstanceOf(FacturaNoEditableException.class)
                .hasMessageContaining("EMITIDA");

        assertThatThrownBy(() -> borradorService.seleccionarComprador(id, 1L))
                .isInstanceOf(FacturaNoEditableException.class);

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(id, List.of()))
                .isInstanceOf(FacturaNoEditableException.class);

        assertThatThrownBy(() -> borradorService.reemplazarPagos(id, List.of()))
                .isInstanceOf(FacturaNoEditableException.class);

        // Y nada se borro por el camino.
        assertThat(facturaDetalleRepository.countByFactura_Id(id)).isEqualTo(1);
    }

    // ==================================================================
    // Validacion de entrada
    // ==================================================================

    @Test
    void losArgumentosMalFormadosSeRechazanAntesDeTocarNada() {
        assertThatThrownBy(() -> borradorService.crear(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(null, null, FECHA)))
                .isInstanceOf(IllegalArgumentException.class);

        Factura borrador = nuevoBorrador();
        Long id = borrador.getId();

        assertThatThrownBy(() -> borradorService.actualizar(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borradorService.actualizar(id, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borradorService.actualizar(id,
                new ActualizarFacturaBorradorCommand(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borradorService.seleccionarComprador(id, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borradorService.reemplazarDetalles(id,
                List.of(new DetalleBorradorCommand(null, BigDecimal.ONE, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("concepto facturable");
        assertThatThrownBy(() -> borradorService.reemplazarPagos(id,
                List.of(new PagoBorradorCommand(null, BigDecimal.ONE, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forma de pago");
        assertThatThrownBy(() -> borradorService.reemplazarDetalles(987_654_321L, List.of()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void laCantidadEsObligatoriaEnCadaLinea() {
        ConceptoFacturable concepto = conceptoConTarifa();
        Factura borrador = nuevoBorrador();

        assertThatThrownBy(() -> borradorService.reemplazarDetalles(borrador.getId(),
                List.of(new DetalleBorradorCommand(concepto.getId(), null, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cantidad");
    }

    @Test
    void unaListaNulaDeLineasOPagosSeTrataComoVacia() {
        Factura borrador = nuevoBorrador();
        assertThat(facturaDetalleRepository.countByFactura_Id(borrador.getId())).isEqualTo(1);

        borradorService.reemplazarPagos(borrador.getId(), null);
        borradorService.reemplazarDetalles(borrador.getId(), null);

        assertThat(facturaDetalleRepository.countByFactura_Id(borrador.getId())).isZero();
        assertThat(facturaPagoRepository.findAllByFactura_Id(borrador.getId())).isEmpty();
        // Un borrador sin lineas tiene totales a cero, no totales desconocidos.
        assertThat(facturaRepository.findById(borrador.getId()).orElseThrow().getImporteTotal())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void unUsuarioOUnaMascotaInactivosNoPuedenFacturar() {
        Usuario inactivo = nuevoUsuario();
        jdbc.update("UPDATE usuarios SET activo = FALSE WHERE id = ?", inactivo.getId());

        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(inactivo.getId(), null, FECHA)))
                .isInstanceOf(TitularFacturaInvalidoException.class)
                .hasMessageContaining("inactivo");

        Usuario usuario = nuevoUsuario();
        Mascota mascota = nuevaMascota(usuario);
        jdbc.update("UPDATE mascotas SET activo = FALSE WHERE id = ?", mascota.getId());

        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), mascota.getId(), FECHA)))
                .isInstanceOf(TitularFacturaInvalidoException.class)
                .hasMessageContaining("inactiva");

        assertThatThrownBy(() -> borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), 987_654_321L, FECHA)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ==================================================================
    // Eliminar (borrado fisico exclusivo de BORRADOR)
    // ==================================================================

    @Test
    void eliminarUnBorradorConDetallesYPagosLoBorraTodoYConservaLasEntidadesCompartidas() {
        ConceptoFacturable concepto = conceptoConTarifa();
        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
        borradorService.reemplazarDetalles(borrador.getId(),
                List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
        borradorService.reemplazarPagos(borrador.getId(), List.of(
                PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
        Long facturaId = borrador.getId();

        borradorService.eliminar(facturaId);

        assertThat(facturaRepository.findById(facturaId)).isEmpty();
        assertThat(facturaDetalleRepository.findAllByFactura_IdOrderByLineaAsc(facturaId)).isEmpty();
        assertThat(facturaPagoRepository.findAllByFactura_Id(facturaId)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_detalles WHERE factura_id = ?", Integer.class, facturaId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_pagos WHERE factura_id = ?", Integer.class, facturaId))
                .isZero();

        // Entidades compartidas: intactas.
        assertThat(usuarioRepository.findById(usuario.getId())).isPresent();
        assertThat(conceptoFacturableRepository.findById(concepto.getId())).isPresent();
    }

    @Test
    void unBorradorVacioSinDetallesNiPagosTambienSeElimina() {
        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));

        borradorService.eliminar(borrador.getId());

        assertThat(facturaRepository.findById(borrador.getId())).isEmpty();
    }

    @Test
    void unaFacturaQueYaNoEsBorradorNuncaSeElimina() {
        Usuario usuario = nuevoUsuario();
        for (EstadoFactura estadoNoBorrador : List.of(
                EstadoFactura.EMITIDA, EstadoFactura.AUTORIZADA, EstadoFactura.RECHAZADA)) {
            Factura factura = facturaRepository.save(Factura.builder()
                    .usuario(usuario)
                    .fechaEmision(FECHA)
                    .estado(estadoNoBorrador)
                    .build());

            assertThatThrownBy(() -> borradorService.eliminar(factura.getId()))
                    .as("estado " + estadoNoBorrador)
                    .isInstanceOf(FacturaNoEditableException.class);

            // Sigue existiendo, intacta.
            assertThat(facturaRepository.findById(factura.getId()))
                    .as("estado " + estadoNoBorrador)
                    .isPresent();
        }
    }

    @Test
    void unBorradorConRastroFiscalInconsistenteNoSeEliminaAunqueSuEstadoDigaBorrador() {
        // Escenario que la maquina de estados actual no deberia permitir
        // nunca: se fuerza por SQL directo para probar la segunda barrera
        // defensiva de eliminar() (ver su javadoc), no la del estado.
        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
        jdbc.update("UPDATE facturas SET clave_acceso = ? WHERE id = ?",
                "0".repeat(49), borrador.getId());

        assertThatThrownBy(() -> borradorService.eliminar(borrador.getId()))
                .isInstanceOf(FacturaNoEditableException.class);

        assertThat(facturaRepository.findById(borrador.getId())).isPresent();
    }

    @Test
    void eliminarUnaFacturaInexistenteEs404() {
        assertThatThrownBy(() -> borradorService.eliminar(987_654_321L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarSinIdEsArgumentoInvalido() {
        assertThatThrownBy(() -> borradorService.eliminar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================================================================
    // Apoyo
    // ==================================================================

    private ConceptoFacturable conceptoConTarifa() {
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
        return nuevoConcepto(codigoPorcentaje, "20.000000");
    }

    private Factura nuevoBorrador() {
        return borradorConLineas(null);
    }

    /** Borrador con una linea de 2 x 20.00 + 15% = 46.00, salvo que se indique otra cosa. */
    private Factura borradorConLineas(List<DetalleBorradorCommand> lineas) {
        Usuario usuario = nuevoUsuario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));

        List<DetalleBorradorCommand> comandos = lineas;
        if (comandos == null) {
            ConceptoFacturable concepto = conceptoConTarifa();
            comandos = List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2")));
        }
        borradorService.reemplazarDetalles(borrador.getId(), comandos);
        return facturaRepository.findById(borrador.getId()).orElseThrow();
    }
}
