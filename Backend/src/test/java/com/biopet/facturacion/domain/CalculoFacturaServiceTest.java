package com.biopet.facturacion.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculoFacturaServiceTest {

    private CalculoFacturaService calculo;

    @BeforeEach
    void setUp() {
        calculo = new CalculoFacturaService();
    }

    /**
     * Las tarifas son datos del test, nunca constantes del producto: esta clase
     * no decide que tarifa corresponde a un servicio veterinario.
     */
    private LineaFacturable linea(String cantidad, String precio, String descuento,
                                  String codigoPorcentaje, String tarifa) {
        return new LineaFacturable("C-001", "Concepto de prueba",
                new BigDecimal(cantidad), new BigDecimal(precio), new BigDecimal(descuento),
                CodigoImpuestoSri.IVA, codigoPorcentaje, new BigDecimal(tarifa));
    }

    private void assertImporte(String esperado, BigDecimal real) {
        assertEquals(new BigDecimal(esperado), real);
        assertEquals(2, real.scale(), "Todo importe monetario debe tener escala 2: " + real);
    }

    // ---------- Linea simple ----------

    @Test
    void unaUnidadDeDiezConTarifaCero() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "0", "0.00")));

        assertImporte("10.00", totales.totalSinImpuestos());
        assertImporte("0.00", totales.totalImpuestos());
        assertImporte("0.00", totales.totalDescuento());
        assertImporte("10.00", totales.importeTotal());
    }

    @Test
    void unaUnidadDeDiezConTarifaGravada() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "4", "15.00")));

        assertImporte("10.00", totales.totalSinImpuestos());
        assertImporte("1.50", totales.totalImpuestos());
        assertImporte("11.50", totales.importeTotal());

        LineaCalculada unica = totales.lineas().get(0);
        assertImporte("10.00", unica.precioTotalSinImpuesto());
        assertImporte("10.00", unica.baseImponible());
        assertImporte("1.50", unica.valorImpuesto());
        assertImporte("11.50", unica.totalLinea());
    }

    @Test
    void otraTarifaCualquieraSeAplicaTalCualLlega() {
        // Demuestra que la tarifa es un dato, no una constante del producto.
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "200.00", "0.00", "10", "13.00")));
        assertImporte("26.00", totales.totalImpuestos());
        assertImporte("226.00", totales.importeTotal());
    }

    @Test
    void cantidadDecimal() {
        TotalesFactura totales = calculo.calcular(List.of(linea("2.5", "4.00", "0.00", "0", "0.00")));
        assertImporte("10.00", totales.totalSinImpuestos());
    }

    /**
     * Ejemplo numerico real publicado por el SRI en la Ficha v2.34: cantidad
     * 2.542563 y precio unitario 25.542365 dan un producto exacto de
     * 64.943072181495, y la ficha imprime precioTotalSinImpuesto = 64.94.
     */
    @Test
    void ejemploDeSeisDecimalesDeLaFichaOficial() {
        LineaFacturable pescado = new LineaFacturable("003", "FROZEN MOONFISH WR",
                new BigDecimal("2.542563"), new BigDecimal("25.542365"), new BigDecimal("0.00"),
                CodigoImpuestoSri.IVA, "0", new BigDecimal("0.00"));

        LineaCalculada calculada = calculo.calcularLinea(pescado);

        assertEquals(new BigDecimal("64.943072181495"), pescado.importeBruto(),
                "El producto debe calcularse exacto, sin redondeo intermedio");
        assertImporte("64.94", calculada.precioTotalSinImpuesto());
        assertImporte("64.94", calculada.baseImponible());
        assertImporte("0.00", calculada.valorImpuesto());
    }

    @Test
    void precioConSeisDecimales() {
        TotalesFactura totales = calculo.calcular(
                List.of(linea("3", "1.333333", "0.00", "4", "15.00")));
        // 3 x 1.333333 = 3.999999 -> 4.00 ; 4.00 x 15 / 100 = 0.60
        assertImporte("4.00", totales.totalSinImpuestos());
        assertImporte("0.60", totales.totalImpuestos());
        assertImporte("4.60", totales.importeTotal());
    }

    // ---------- Descuentos ----------

    @Test
    void descuentoReduceLaBaseImponible() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "100.00", "20.00", "4", "15.00")));
        assertImporte("80.00", totales.totalSinImpuestos());
        assertImporte("20.00", totales.totalDescuento());
        assertImporte("12.00", totales.totalImpuestos());
        assertImporte("92.00", totales.importeTotal());
    }

    @Test
    void descuentoIgualAlBrutoDejaBaseEnCero() {
        TotalesFactura totales = calculo.calcular(List.of(linea("2", "15.00", "30.00", "4", "15.00")));
        assertImporte("0.00", totales.totalSinImpuestos());
        assertImporte("0.00", totales.totalImpuestos());
        assertImporte("30.00", totales.totalDescuento());
        assertImporte("0.00", totales.importeTotal());
    }

    @Test
    void descuentoMayorAlBrutoEsRechazado() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> linea("1", "10.00", "10.01", "4", "15.00"));
        assertTrue(error.getMessage().contains("descuento"), error.getMessage());
    }

    @Test
    void descuentoNegativoEsRechazado() {
        assertThrows(IllegalArgumentException.class, () -> linea("1", "10.00", "-0.01", "4", "15.00"));
    }

    @Test
    void ningunaLineaProduceBaseImponibleNegativa() {
        // Consecuencia estructural de exigir descuento <= bruto exacto.
        TotalesFactura totales = calculo.calcular(List.of(
                linea("1", "9.995", "9.99", "0", "0.00"),
                linea("3", "0.005", "0.01", "0", "0.00")));
        for (LineaCalculada calculada : totales.lineas()) {
            assertTrue(calculada.baseImponible().signum() >= 0,
                    "Base negativa: " + calculada.baseImponible());
        }
    }

    // ---------- Redondeo delicado ----------

    /**
     * Caso critico frente al error 52 del SRI ("Error en diferencias"). Tres
     * lineas de medio centavo: redondeando por linea el total es 0.03; si se
     * sumara primero y se redondeara despues seria 0.02. La politica
     * implementada redondea una sola vez por linea y despues suma exacto.
     */
    @Test
    void redondearPorLineaYSumarDespuesNoEsLoMismoQueSumarYRedondear() {
        List<LineaFacturable> lineas = List.of(
                linea("1", "0.005", "0.00", "0", "0.00"),
                linea("1", "0.005", "0.00", "0", "0.00"),
                linea("1", "0.005", "0.00", "0", "0.00"));

        TotalesFactura totales = calculo.calcular(lineas);

        assertImporte("0.03", totales.totalSinImpuestos());
        for (LineaCalculada calculada : totales.lineas()) {
            assertImporte("0.01", calculada.precioTotalSinImpuesto());
        }

        BigDecimal sumaSinRedondear = new BigDecimal("0.015");
        assertEquals(new BigDecimal("0.02"), EscalasSri.aMonetario(sumaSinRedondear),
                "Referencia: sumar y redondear despues daria 0.02");
    }

    @Test
    void elImpuestoSeRedondeaUnaSolaVezPorLinea() {
        // 0.04 x 15 / 100 = 0.006 -> 0.01 ; 0.03 x 15 / 100 = 0.0045 -> 0.00
        assertImporte("0.01", calculo.calcularLinea(linea("1", "0.04", "0.00", "4", "15.00")).valorImpuesto());
        assertImporte("0.00", calculo.calcularLinea(linea("1", "0.03", "0.00", "4", "15.00")).valorImpuesto());
    }

    @Test
    void laSumaDeLosGruposCoincideConLaSumaDeLasLineas() {
        List<LineaFacturable> lineas = List.of(
                linea("3", "7.777777", "0.00", "4", "15.00"),
                linea("1.333333", "9.99", "0.55", "4", "15.00"),
                linea("7", "0.015", "0.00", "0", "0.00"),
                linea("2.5", "3.333", "1.11", "10", "13.00"));

        TotalesFactura totales = calculo.calcular(lineas);

        BigDecimal baseLineas = BigDecimal.ZERO.setScale(2);
        BigDecimal impuestoLineas = BigDecimal.ZERO.setScale(2);
        for (LineaCalculada calculada : totales.lineas()) {
            baseLineas = baseLineas.add(calculada.baseImponible());
            impuestoLineas = impuestoLineas.add(calculada.valorImpuesto());
        }

        BigDecimal baseGrupos = BigDecimal.ZERO.setScale(2);
        BigDecimal impuestoGrupos = BigDecimal.ZERO.setScale(2);
        for (ImpuestoAgrupado grupo : totales.resumenImpuestos()) {
            baseGrupos = baseGrupos.add(grupo.baseImponible());
            impuestoGrupos = impuestoGrupos.add(grupo.valorImpuesto());
        }

        assertEquals(baseLineas, baseGrupos);
        assertEquals(impuestoLineas, impuestoGrupos);
        assertEquals(totales.totalSinImpuestos(), baseGrupos);
        assertEquals(totales.totalImpuestos(), impuestoGrupos);
        assertEquals(totales.totalSinImpuestos().add(totales.totalImpuestos()), totales.importeTotal());
    }

    // ---------- Varias lineas y agrupacion ----------

    @Test
    void variasLineasConLaMismaTarifaSeAgrupanEnUnSoloTotalImpuesto() {
        TotalesFactura totales = calculo.calcular(List.of(
                linea("1", "25.00", "0.00", "4", "15.00"),
                linea("2", "5.00", "0.00", "4", "15.00")));

        assertEquals(1, totales.resumenImpuestos().size());
        ImpuestoAgrupado grupo = totales.resumenImpuestos().get(0);
        assertEquals(CodigoImpuestoSri.IVA, grupo.codigoImpuesto());
        assertEquals("4", grupo.codigoPorcentaje());
        assertImporte("35.00", grupo.baseImponible());
        assertImporte("5.25", grupo.valorImpuesto());
    }

    @Test
    void facturaConTarifaGravadaYTarifaCeroProduceDosGrupos() {
        TotalesFactura totales = calculo.calcular(List.of(
                linea("1", "25.00", "0.00", "4", "15.00"),
                linea("1", "12.50", "0.00", "0", "0.00"),
                linea("2", "5.00", "0.00", "4", "15.00")));

        assertEquals(2, totales.resumenImpuestos().size());

        ImpuestoAgrupado gravado = totales.resumenImpuestos().get(0);
        assertEquals("4", gravado.codigoPorcentaje());
        assertImporte("35.00", gravado.baseImponible());
        assertImporte("5.25", gravado.valorImpuesto());

        ImpuestoAgrupado tarifaCero = totales.resumenImpuestos().get(1);
        assertEquals("0", tarifaCero.codigoPorcentaje());
        assertImporte("12.50", tarifaCero.baseImponible());
        assertImporte("0.00", tarifaCero.valorImpuesto());

        assertImporte("47.50", totales.totalSinImpuestos());
        assertImporte("5.25", totales.totalImpuestos());
        assertImporte("52.75", totales.importeTotal());
    }

    @Test
    void soportaTresCodigosDePorcentajeDistintosEnLaMismaFactura() {
        // Gravado, tarifa 0 y "no objeto de impuesto" (codigo 6 de la TABLA 17).
        TotalesFactura totales = calculo.calcular(List.of(
                linea("1", "100.00", "0.00", "4", "15.00"),
                linea("1", "50.00", "0.00", "0", "0.00"),
                linea("1", "20.00", "0.00", "6", "0.00")));

        assertEquals(3, totales.resumenImpuestos().size());
        assertImporte("170.00", totales.totalSinImpuestos());
        assertImporte("15.00", totales.totalImpuestos());
        assertImporte("185.00", totales.importeTotal());
    }

    @Test
    void elOrdenDeLosGruposEsElDePrimeraAparicion() {
        TotalesFactura totales = calculo.calcular(List.of(
                linea("1", "10.00", "0.00", "0", "0.00"),
                linea("1", "10.00", "0.00", "4", "15.00"),
                linea("1", "10.00", "0.00", "0", "0.00")));

        assertEquals("0", totales.resumenImpuestos().get(0).codigoPorcentaje());
        assertEquals("4", totales.resumenImpuestos().get(1).codigoPorcentaje());
        assertImporte("20.00", totales.resumenImpuestos().get(0).baseImponible());
    }

    @Test
    void rechazaDosTarifasDistintasBajoElMismoCodigoDePorcentaje() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> calculo.calcular(List.of(
                        linea("1", "10.00", "0.00", "4", "15.00"),
                        linea("1", "10.00", "0.00", "4", "12.00"))));
        assertTrue(error.getMessage().contains("tarifas distintas"), error.getMessage());
    }

    @Test
    void rechazaFacturaSinLineas() {
        assertThrows(IllegalArgumentException.class, () -> calculo.calcular(List.of()));
        assertThrows(IllegalArgumentException.class, () -> calculo.calcular(null));
    }

    @Test
    void rechazaLineaNula() {
        assertThrows(IllegalArgumentException.class, () -> calculo.calcularLinea(null));
    }

    // ---------- Validaciones de la linea ----------

    @Test
    void rechazaCantidadConMasDeSeisDecimales() {
        assertThrows(IllegalArgumentException.class, () -> linea("1.1234567", "10.00", "0.00", "4", "15.00"));
    }

    @Test
    void rechazaPrecioUnitarioConMasDeSeisDecimales() {
        assertThrows(IllegalArgumentException.class, () -> linea("1", "10.1234567", "0.00", "4", "15.00"));
    }

    @Test
    void rechazaDescuentoConMasDeDosDecimales() {
        assertThrows(IllegalArgumentException.class, () -> linea("1", "10.00", "0.001", "4", "15.00"));
    }

    @Test
    void rechazaTarifaConMasDeDosDecimales() {
        assertThrows(IllegalArgumentException.class, () -> linea("1", "10.00", "0.00", "4", "15.005"));
    }

    @Test
    void rechazaCantidadOPrecioNegativos() {
        assertThrows(IllegalArgumentException.class, () -> linea("-1", "10.00", "0.00", "4", "15.00"));
        assertThrows(IllegalArgumentException.class, () -> linea("1", "-10.00", "0.00", "4", "15.00"));
    }

    @Test
    void rechazaValoresNulos() {
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                null, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, null, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, null, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", null));
    }

    @Test
    void rechazaDescripcionVaciaOExcesiva() {
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "  ",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "x".repeat(301),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
    }

    @Test
    void rechazaCodigoPrincipalExcesivo() {
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("x".repeat(26), "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4", BigDecimal.ZERO));
    }

    @Test
    void aceptaCodigoPrincipalNulo() {
        // codigoPrincipal es minOccurs=0 en el XSD oficial.
        LineaCalculada calculada = calculo.calcularLinea(new LineaFacturable(null, "Servicio",
                BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.ZERO,
                CodigoImpuestoSri.IVA, "0", BigDecimal.ZERO));
        assertImporte("10.00", calculada.precioTotalSinImpuesto());
    }

    @Test
    void rechazaCodigoDeImpuestoOPorcentajeInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, "4", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "12345", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new LineaFacturable("C", "desc",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, CodigoImpuestoSri.IVA, "4A", BigDecimal.ZERO));
    }

    // ---------- Pagos ----------

    @Test
    void pagosQueSumanExactamenteElImporteTotalSonValidos() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "100.00", "0.00", "4", "15.00")));
        assertImporte("115.00", totales.importeTotal());

        calculo.validarPagos(totales, List.of(
                new PagoFacturable(FormaPagoSri.TARJETA_CREDITO, new BigDecimal("100.00")),
                new PagoFacturable(FormaPagoSri.SIN_UTILIZACION_SISTEMA_FINANCIERO, new BigDecimal("15.00"))));
    }

    @Test
    void unSoloPagoQueCubreElTotalEsValido() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "0", "0.00")));
        calculo.validarPagos(totales,
                List.of(new PagoFacturable(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("10.00"))));
    }

    @Test
    void pagosInferioresAlTotalSonRechazados() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "100.00", "0.00", "4", "15.00")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> calculo.validarPagos(totales,
                        List.of(new PagoFacturable(FormaPagoSri.TARJETA_CREDITO, new BigDecimal("114.99")))));
        assertTrue(error.getMessage().contains("115.00"), error.getMessage());
    }

    @Test
    void pagosSuperioresAlTotalSonRechazados() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "100.00", "0.00", "4", "15.00")));
        assertThrows(IllegalArgumentException.class, () -> calculo.validarPagos(totales,
                List.of(new PagoFacturable(FormaPagoSri.TARJETA_CREDITO, new BigDecimal("115.01")))));
    }

    @Test
    void listaDePagosVaciaNoCubreUnTotalMayorQueCero() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "0", "0.00")));
        assertThrows(IllegalArgumentException.class, () -> calculo.validarPagos(totales, List.of()));
    }

    @Test
    void sumarPagosDevuelveEscalaMonetaria() {
        BigDecimal suma = calculo.sumarPagos(List.of(
                new PagoFacturable(FormaPagoSri.DINERO_ELECTRONICO, new BigDecimal("1.10")),
                new PagoFacturable(FormaPagoSri.ENDOSO_TITULOS, new BigDecimal("2.20"))));
        assertImporte("3.30", suma);
        assertImporte("0.00", calculo.sumarPagos(List.of()));
    }

    @Test
    void rechazaPagosNulosOMalFormados() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "0", "0.00")));
        assertThrows(IllegalArgumentException.class, () -> calculo.sumarPagos(null));
        assertThrows(IllegalArgumentException.class, () -> calculo.validarPagos(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PagoFacturable(null, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new PagoFacturable(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new PagoFacturable(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> calculo.validarPagos(totales, null));
    }

    // ---------- Inmutabilidad del resultado ----------

    @Test
    void lasColeccionesDelResultadoSonInmutables() {
        TotalesFactura totales = calculo.calcular(List.of(linea("1", "10.00", "0.00", "0", "0.00")));
        assertThrows(UnsupportedOperationException.class, () -> totales.lineas().clear());
        assertThrows(UnsupportedOperationException.class, () -> totales.resumenImpuestos().clear());
    }
}
