package com.biopet.facturacion.xml;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.ClaveAccesoGenerator;
import com.biopet.facturacion.domain.ClaveAccesoRequest;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.domain.TipoComprobante;
import com.biopet.facturacion.domain.TipoEmisionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.entity.TipoIdentificacionSri;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Factura EMITIDA en memoria, sin base de datos, para las pruebas del
 * constructor de XML y del validador XSD.
 *
 * <p>Todos los datos son ficticios. El RUC cumple el patron que exige el XSD
 * ({@code [0-9]{10}001}), que es mas estricto que el CHECK de 13 digitos de la
 * tabla: por eso no vale cualquier cadena de 13 numeros.
 */
public final class FacturaXmlFixture {

    public static final String RUC_EMISOR = "0999999999001";
    public static final String ESTABLECIMIENTO = "001";
    public static final String PUNTO_EMISION = "002";
    public static final long SECUENCIAL = 42L;
    public static final String CODIGO_NUMERICO = "12345678";
    public static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    private FacturaXmlFixture() {
    }

    /** Una linea de 2 x 20.000000 con IVA 15%: 40.00 + 6.00 = 46.00. */
    public static Factura facturaEmitida() {
        Factura factura = cabecera();
        factura.agregarDetalle(detalle(1, "SRV-001", "Consulta veterinaria ficticia",
                "2.000000", "20.000000", "0.00", "40.00", "4", "15.00", "40.00", "6.00"));
        factura.agregarPago(pago("46.00"));
        return factura;
    }

    public static Factura cabecera() {
        Factura factura = Factura.builder()
                .fechaEmision(FECHA)
                .estado(EstadoFactura.EMITIDA)
                .ambiente(AmbienteSri.PRUEBAS)
                .establecimiento(ESTABLECIMIENTO)
                .puntoEmisionCodigo(PUNTO_EMISION)
                .secuencial(SECUENCIAL)
                .codigoNumerico(CODIGO_NUMERICO)
                .claveAcceso(claveAcceso())
                .compradorTipoIdentificacion(TipoIdentificacionSri.CEDULA)
                .compradorIdentificacion("0102030405")
                .compradorRazonSocial("MARIA LOPEZ")
                .compradorDireccion("Av. Ficticia 123 y Secundaria")
                .compradorEmail("maria@ejemplo.test")
                .compradorTelefono("0999999999")
                .emisorRuc(RUC_EMISOR)
                .emisorRazonSocial("CLINICA VETERINARIA FICTICIA S.A.")
                .emisorNombreComercial("BIOPET")
                .emisorDireccionMatriz("Av. Matriz Ficticia 100")
                .emisorDireccionEstablecimiento("Sucursal Ficticia Norte")
                .emisorObligadoContabilidad(true)
                .emisorContribuyenteEspecial("12345")
                .emisorRimpe(false)
                .totalSinImpuestos(new BigDecimal("40.00"))
                .totalDescuento(new BigDecimal("0.00"))
                .totalImpuestos(new BigDecimal("6.00"))
                .importeTotal(new BigDecimal("46.00"))
                .moneda("DOLAR")
                .build();
        factura.setId(1L);
        return factura;
    }

    public static String claveAcceso() {
        return new ClaveAccesoGenerator().generar(new ClaveAccesoRequest(
                FECHA, TipoComprobante.FACTURA, RUC_EMISOR, AmbienteSri.PRUEBAS,
                ESTABLECIMIENTO, PUNTO_EMISION, SECUENCIAL, CODIGO_NUMERICO,
                TipoEmisionSri.NORMAL));
    }

    public static FacturaDetalle detalle(int linea, String codigo, String descripcion,
                                         String cantidad, String precioUnitario, String descuento,
                                         String precioTotalSinImpuesto, String codigoPorcentaje,
                                         String tarifa, String baseImponible, String valorImpuesto) {
        return FacturaDetalle.builder()
                .linea(linea)
                .codigoPrincipal(codigo)
                .descripcion(descripcion)
                .cantidad(new BigDecimal(cantidad))
                .precioUnitario(new BigDecimal(precioUnitario))
                .descuento(new BigDecimal(descuento))
                .precioTotalSinImpuesto(new BigDecimal(precioTotalSinImpuesto))
                .impuestoCodigo(CodigoImpuestoSri.IVA)
                .impuestoCodigoPorcentaje(codigoPorcentaje)
                .impuestoTarifa(new BigDecimal(tarifa))
                .baseImponible(new BigDecimal(baseImponible))
                .impuestoValor(new BigDecimal(valorImpuesto))
                .build();
    }

    public static FacturaPago pago(String total) {
        return FacturaPago.builder()
                .formaPago(FormaPagoSri.TARJETA_DEBITO)
                .total(new BigDecimal(total))
                .build();
    }
}
