package com.biopet.facturacion.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fija los catalogos del SRI frente a ediciones accidentales. Los valores
 * proceden de la Ficha Tecnica de Comprobantes Electronicos Offline v2.34
 * (TABLA 2, TABLA 3, TABLA 4, TABLA 16 y TABLA 24).
 */
class CatalogosSriTest {

    @Test
    void ambienteUsaLosCodigosDeLaTablaCuatro() {
        assertEquals("1", AmbienteSri.PRUEBAS.codigo());
        assertEquals("2", AmbienteSri.PRODUCCION.codigo());
        assertEquals(2, AmbienteSri.values().length);
        assertEquals(AmbienteSri.PRUEBAS, AmbienteSri.desdeCodigo("1"));
        assertEquals(AmbienteSri.PRODUCCION, AmbienteSri.desdeCodigo("2"));
        assertThrows(IllegalArgumentException.class, () -> AmbienteSri.desdeCodigo("3"));
    }

    @Test
    void tipoDeEmisionSoloContemplaLaNormalDelEsquemaOffline() {
        assertEquals("1", TipoEmisionSri.NORMAL.codigo());
        assertEquals(1, TipoEmisionSri.values().length);
    }

    @Test
    void tipoDeComprobanteFacturaEsCeroUno() {
        assertEquals("01", TipoComprobante.FACTURA.codDoc());
        assertEquals(1, TipoComprobante.values().length);
    }

    @Test
    void codigosDeImpuestoSonLosDeLaTablaDieciseis() {
        assertEquals("2", CodigoImpuestoSri.IVA.codigo());
        assertEquals("3", CodigoImpuestoSri.ICE.codigo());
        assertEquals("5", CodigoImpuestoSri.IRBPNR.codigo());
        assertEquals(3, CodigoImpuestoSri.values().length);
        assertEquals(CodigoImpuestoSri.IVA, CodigoImpuestoSri.desdeCodigo("2"));
        assertThrows(IllegalArgumentException.class, () -> CodigoImpuestoSri.desdeCodigo("4"));
    }

    /**
     * Los ocho codigos de la TABLA 24, verificados sobre el PDF oficial. Todos
     * figuran con FECHA FIN vacia, es decir siguen vigentes. La tabla no define
     * los codigos 02-14 pese a que el patron del XSD los admitiria.
     */
    @Test
    void formasDePagoSonExactamenteLasOchoDeLaTablaVeinticuatro() {
        Map<String, FormaPagoSri> esperadas = new LinkedHashMap<>();
        esperadas.put("01", FormaPagoSri.SIN_UTILIZACION_SISTEMA_FINANCIERO);
        esperadas.put("15", FormaPagoSri.COMPENSACION_DEUDAS);
        esperadas.put("16", FormaPagoSri.TARJETA_DEBITO);
        esperadas.put("17", FormaPagoSri.DINERO_ELECTRONICO);
        esperadas.put("18", FormaPagoSri.TARJETA_PREPAGO);
        esperadas.put("19", FormaPagoSri.TARJETA_CREDITO);
        esperadas.put("20", FormaPagoSri.OTROS_CON_SISTEMA_FINANCIERO);
        esperadas.put("21", FormaPagoSri.ENDOSO_TITULOS);

        assertEquals(esperadas.size(), FormaPagoSri.values().length);
        esperadas.forEach((codigo, forma) -> {
            assertEquals(codigo, forma.codigo());
            assertEquals(forma, FormaPagoSri.desdeCodigo(codigo));
        });
    }

    @Test
    void cadaFormaDePagoTieneDescripcion() {
        for (FormaPagoSri forma : FormaPagoSri.values()) {
            assertEquals(2, forma.codigo().length());
            org.junit.jupiter.api.Assertions.assertFalse(forma.descripcion().isBlank());
        }
    }

    @Test
    void formaDePagoDesconocidaEsRechazada() {
        assertThrows(IllegalArgumentException.class, () -> FormaPagoSri.desdeCodigo("02"));
        assertThrows(IllegalArgumentException.class, () -> FormaPagoSri.desdeCodigo("99"));
    }
}
