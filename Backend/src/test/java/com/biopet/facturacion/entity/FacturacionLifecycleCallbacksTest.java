package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.AmbienteSri;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura de rama de los callbacks {@code @PrePersist} / {@code @PreUpdate} de
 * las entidades del modulo de facturacion, en el mismo estilo que
 * {@code com.biopet.entity.EntityLifecycleCallbacksTest}: cada callback decide
 * con un {@code if (campo == null)} si autocompleta o respeta lo ya asignado, y
 * aqui se ejercitan las DOS ramas.
 *
 * <p>Se invocan los metodos de paquete directamente (esta clase vive en el mismo
 * paquete que las entidades), sin Spring ni base de datos.
 */
class FacturacionLifecycleCallbacksTest {

    // ------------------------------------------------------------------
    // Factura: la que mas defaults tiene
    // ------------------------------------------------------------------

    @Test
    void facturaPrePersistAutocompletaBorradorTotalesCeroYCeroIntentos() {
        Factura factura = new Factura();

        factura.prePersist();

        assertThat(factura.getCreadoEn()).isNotNull();
        assertThat(factura.getActualizadoEn()).isNotNull();
        // Una factura nace como BORRADOR: nunca como un documento ya emitido.
        assertThat(factura.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(factura.getIntentosAutorizacion()).isZero();
        assertThat(factura.getTotalSinImpuestos()).isEqualByComparingTo("0");
        assertThat(factura.getTotalDescuento()).isEqualByComparingTo("0");
        assertThat(factura.getTotalImpuestos()).isEqualByComparingTo("0");
        assertThat(factura.getImporteTotal()).isEqualByComparingTo("0");
    }

    @Test
    void facturaPrePersistRespetaTodoLoQueYaVieneAsignado() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Factura factura = new Factura();
        factura.setCreadoEn(creado);
        factura.setActualizadoEn(actualizado);
        factura.setEstado(EstadoFactura.AUTORIZADA);
        factura.setIntentosAutorizacion(3);
        factura.setTotalSinImpuestos(new BigDecimal("10.00"));
        factura.setTotalDescuento(new BigDecimal("1.00"));
        factura.setTotalImpuestos(new BigDecimal("1.50"));
        factura.setImporteTotal(new BigDecimal("11.50"));

        factura.prePersist();

        assertThat(factura.getCreadoEn()).isEqualTo(creado);
        assertThat(factura.getActualizadoEn()).isEqualTo(actualizado);
        assertThat(factura.getEstado()).isEqualTo(EstadoFactura.AUTORIZADA);
        assertThat(factura.getIntentosAutorizacion()).isEqualTo(3);
        assertThat(factura.getImporteTotal()).isEqualByComparingTo("11.50");
    }

    @Test
    void facturaPreUpdateRefrescaActualizadoEnYNoTocaCreadoEn() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Factura factura = new Factura();
        factura.setCreadoEn(creado);
        factura.setActualizadoEn(creado);

        factura.preUpdate();

        assertThat(factura.getCreadoEn()).isEqualTo(creado);
        assertThat(factura.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void agregarDetalleYPagoDejanLaRelacionCoherenteEnAmbosExtremos() {
        Factura factura = new Factura();
        FacturaDetalle detalle = new FacturaDetalle();
        FacturaPago pago = new FacturaPago();

        factura.agregarDetalle(detalle);
        factura.agregarPago(pago);

        assertThat(factura.getDetalles()).containsExactly(detalle);
        assertThat(factura.getPagos()).containsExactly(pago);
        // El lado propietario de la FK tambien queda apuntando a la factura:
        // sin esto Hibernate insertaria factura_id nulo.
        assertThat(detalle.getFactura()).isSameAs(factura);
        assertThat(pago.getFactura()).isSameAs(factura);
    }

    @Test
    void unaFacturaConstruidaConBuilderYaTieneSusColeccionesVacias() {
        // @Builder.Default: sin el, las listas llegarian nulas y agregarDetalle
        // reventaria con NullPointerException.
        Factura factura = Factura.builder().build();

        assertThat(factura.getDetalles()).isNotNull().isEmpty();
        assertThat(factura.getPagos()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // Catalogos
    // ------------------------------------------------------------------

    @Test
    void emisorFiscalAutocompletaTimestampsYLosRespetaSiYaExisten() {
        EmisorFiscal nuevo = new EmisorFiscal();
        nuevo.prePersist();
        assertThat(nuevo.getCreadoEn()).isNotNull();
        assertThat(nuevo.getActualizadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        EmisorFiscal existente = new EmisorFiscal();
        existente.setCreadoEn(creado);
        existente.setActualizadoEn(creado);
        existente.prePersist();
        assertThat(existente.getCreadoEn()).isEqualTo(creado);
        assertThat(existente.getActualizadoEn()).isEqualTo(creado);

        existente.preUpdate();
        assertThat(existente.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void puntoEmisionAutocompletaTimestampsYLosRespetaSiYaExisten() {
        PuntoEmision nuevo = new PuntoEmision();
        nuevo.prePersist();
        assertThat(nuevo.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        PuntoEmision existente = new PuntoEmision();
        existente.setCreadoEn(creado);
        existente.setActualizadoEn(creado);
        existente.prePersist();
        assertThat(existente.getCreadoEn()).isEqualTo(creado);

        existente.preUpdate();
        assertThat(existente.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void secuencialEmisionEmpiezaEnCeroYRespetaUnContadorYaAvanzado() {
        SecuencialEmision nuevo = new SecuencialEmision();
        nuevo.prePersist();
        assertThat(nuevo.getUltimoSecuencial()).isZero();
        assertThat(nuevo.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        SecuencialEmision existente = new SecuencialEmision();
        existente.setCreadoEn(creado);
        existente.setActualizadoEn(creado);
        existente.setAmbiente(AmbienteSri.PRODUCCION);
        existente.setUltimoSecuencial(500L);
        existente.prePersist();
        assertThat(existente.getUltimoSecuencial()).isEqualTo(500L);
        assertThat(existente.getCreadoEn()).isEqualTo(creado);

        existente.preUpdate();
        assertThat(existente.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void tarifaImpuestoAutocompletaTimestampsYLosRespetaSiYaExisten() {
        TarifaImpuesto nueva = new TarifaImpuesto();
        nueva.prePersist();
        assertThat(nueva.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        TarifaImpuesto existente = new TarifaImpuesto();
        existente.setCreadoEn(creado);
        existente.setActualizadoEn(creado);
        existente.prePersist();
        assertThat(existente.getCreadoEn()).isEqualTo(creado);

        existente.preUpdate();
        assertThat(existente.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void conceptoFacturableAutocompletaTimestampsYLosRespetaSiYaExisten() {
        ConceptoFacturable nuevo = new ConceptoFacturable();
        nuevo.prePersist();
        assertThat(nuevo.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        ConceptoFacturable existente = new ConceptoFacturable();
        existente.setCreadoEn(creado);
        existente.setActualizadoEn(creado);
        existente.prePersist();
        assertThat(existente.getCreadoEn()).isEqualTo(creado);

        existente.preUpdate();
        assertThat(existente.getActualizadoEn()).isAfter(creado);
    }

    @Test
    void datosFacturacionAutocompletaTimestampsYLosRespetaSiYaExisten() {
        DatosFacturacion nuevos = new DatosFacturacion();
        nuevos.prePersist();
        assertThat(nuevos.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        DatosFacturacion existentes = new DatosFacturacion();
        existentes.setCreadoEn(creado);
        existentes.setActualizadoEn(creado);
        existentes.prePersist();
        assertThat(existentes.getCreadoEn()).isEqualTo(creado);

        existentes.preUpdate();
        assertThat(existentes.getActualizadoEn()).isAfter(creado);
    }

    // ------------------------------------------------------------------
    // Hijas de la factura
    // ------------------------------------------------------------------

    @Test
    void detalleSinDescuentoExplicitoLoTomaComoCeroYRespetaElQueTraiga() {
        FacturaDetalle sinDescuento = new FacturaDetalle();
        sinDescuento.prePersist();
        assertThat(sinDescuento.getDescuento()).isEqualByComparingTo("0");

        FacturaDetalle conDescuento = new FacturaDetalle();
        conDescuento.setDescuento(new BigDecimal("2.50"));
        conDescuento.prePersist();
        assertThat(conDescuento.getDescuento()).isEqualByComparingTo("2.50");
    }

    @Test
    void documentoYEventoFijanCreadoEnSoloSiNoVienePuesto() {
        FacturaDocumento documentoNuevo = new FacturaDocumento();
        documentoNuevo.prePersist();
        assertThat(documentoNuevo.getCreadoEn()).isNotNull();

        Instant creado = Instant.parse("2026-02-01T00:00:00Z");
        FacturaDocumento documentoExistente = new FacturaDocumento();
        documentoExistente.setCreadoEn(creado);
        documentoExistente.prePersist();
        assertThat(documentoExistente.getCreadoEn()).isEqualTo(creado);

        FacturaEventoSri eventoNuevo = new FacturaEventoSri();
        eventoNuevo.prePersist();
        assertThat(eventoNuevo.getCreadoEn()).isNotNull();

        FacturaEventoSri eventoExistente = new FacturaEventoSri();
        eventoExistente.setCreadoEn(creado);
        eventoExistente.prePersist();
        assertThat(eventoExistente.getCreadoEn()).isEqualTo(creado);
    }
}
