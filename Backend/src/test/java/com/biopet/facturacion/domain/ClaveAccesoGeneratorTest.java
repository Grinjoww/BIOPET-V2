package com.biopet.facturacion.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaveAccesoGeneratorTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 27);
    private static final String RUC = "1790012345001";
    private static final String ESTAB = "001";
    private static final String PTO_EMI = "001";
    private static final String CODIGO_NUMERICO = "12345678";

    private ClaveAccesoGenerator generador;

    @BeforeEach
    void setUp() {
        generador = new ClaveAccesoGenerator();
    }

    private ClaveAccesoRequest request(AmbienteSri ambiente, long secuencial) {
        return new ClaveAccesoRequest(FECHA, TipoComprobante.FACTURA, RUC, ambiente,
                ESTAB, PTO_EMI, secuencial, CODIGO_NUMERICO, TipoEmisionSri.NORMAL);
    }

    // ---------- Forma de la clave ----------

    @Test
    void laClaveTieneCuarentaYNueveDigitos() {
        String clave = generador.generar(request(AmbienteSri.PRUEBAS, 42L));
        assertEquals(49, clave.length());
        assertTrue(clave.chars().allMatch(Character::isDigit));
    }

    @Test
    void losCamposOcupanLasPosicionesDeLaTablaUno() {
        String clave = generador.generar(request(AmbienteSri.PRUEBAS, 42L));

        assertEquals("27082026", clave.substring(0, 8), "fecha ddMMyyyy");
        assertEquals("01", clave.substring(8, 10), "codDoc factura");
        assertEquals(RUC, clave.substring(10, 23), "RUC");
        assertEquals("1", clave.substring(23, 24), "ambiente");
        assertEquals(ESTAB, clave.substring(24, 27), "establecimiento");
        assertEquals(PTO_EMI, clave.substring(27, 30), "punto de emision");
        assertEquals("000000042", clave.substring(30, 39), "secuencial");
        assertEquals(CODIGO_NUMERICO, clave.substring(39, 47), "codigo numerico");
        assertEquals("1", clave.substring(47, 48), "tipo de emision");
    }

    @Test
    void laFechaSeFormateaComoDdMMyyyyConCerosALaIzquierda() {
        ClaveAccesoRequest peticion = new ClaveAccesoRequest(LocalDate.of(2026, 1, 5),
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI,
                1L, CODIGO_NUMERICO, TipoEmisionSri.NORMAL);
        assertEquals("05012026", generador.generar(peticion).substring(0, 8));
    }

    @Test
    void elCodDocDeFacturaEsCeroUno() {
        assertEquals("01", TipoComprobante.FACTURA.codDoc());
        assertEquals("01", generador.generar(request(AmbienteSri.PRUEBAS, 1L)).substring(8, 10));
    }

    @Test
    void ambientePruebasUsaUnoYProduccionUsaDos() {
        assertEquals("1", generador.generar(request(AmbienteSri.PRUEBAS, 1L)).substring(23, 24));
        assertEquals("2", generador.generar(request(AmbienteSri.PRODUCCION, 1L)).substring(23, 24));
    }

    @Test
    void elAmbienteCambiaLaClaveCompleta() {
        String pruebas = generador.generar(request(AmbienteSri.PRUEBAS, 7L));
        String produccion = generador.generar(request(AmbienteSri.PRODUCCION, 7L));
        assertNotEquals(pruebas, produccion);
        assertTrue(generador.esValida(pruebas));
        assertTrue(generador.esValida(produccion));
    }

    // ---------- Secuencial ----------

    @Test
    void elSecuencialSeNormalizaANueveDigitos() {
        assertEquals("000000001", request(AmbienteSri.PRUEBAS, 1L).secuencialFormateado());
        assertEquals("000000042", request(AmbienteSri.PRUEBAS, 42L).secuencialFormateado());
        assertEquals("999999999", request(AmbienteSri.PRUEBAS, 999_999_999L).secuencialFormateado());
    }

    @Test
    void aceptaElSecuencialMaximo() {
        String clave = generador.generar(request(AmbienteSri.PRUEBAS, 999_999_999L));
        assertEquals("999999999", clave.substring(30, 39));
        assertTrue(generador.esValida(clave));
    }

    @Test
    void rechazaSecuencialCeroYNegativo() {
        assertThrows(IllegalArgumentException.class, () -> request(AmbienteSri.PRUEBAS, 0L));
        assertThrows(IllegalArgumentException.class, () -> request(AmbienteSri.PRUEBAS, -1L));
    }

    @Test
    void rechazaSecuencialPorEncimaDelMaximo() {
        assertThrows(IllegalArgumentException.class, () -> request(AmbienteSri.PRUEBAS, 1_000_000_000L));
    }

    // ---------- Validaciones de entrada ----------

    private ClaveAccesoRequest conRuc(String ruc) {
        return new ClaveAccesoRequest(FECHA, TipoComprobante.FACTURA, ruc, AmbienteSri.PRUEBAS,
                ESTAB, PTO_EMI, 1L, CODIGO_NUMERICO, TipoEmisionSri.NORMAL);
    }

    @Test
    void rechazaRucQueNoTengaTreceDigitos() {
        assertThrows(IllegalArgumentException.class, () -> conRuc("179001234500"));
        assertThrows(IllegalArgumentException.class, () -> conRuc("17900123450012"));
        assertThrows(IllegalArgumentException.class, () -> conRuc(null));
        assertThrows(IllegalArgumentException.class, () -> conRuc("179001234500A"));
    }

    @Test
    void aceptaCualquierRucDeTreceDigitosSinAplicarAlgoritmosInventados() {
        // La validez tributaria real del RUC la resuelve el SRI (errores 46 y 63).
        // Esta capa solo comprueba la forma que exige la clave de acceso.
        assertEquals(49, generador.generar(conRuc("0000000000000")).length());
        assertEquals(49, generador.generar(conRuc("9999999999999")).length());
    }

    @Test
    void rechazaEstablecimientoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, "01", PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, "0011", PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, "A01", PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
    }

    @Test
    void rechazaPuntoDeEmisionInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, "1", 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, null, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
    }

    @Test
    void rechazaCodigoNumericoQueNoTengaOchoDigitos() {
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                "1234567", TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                "123456789", TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                "1234567X", TipoEmisionSri.NORMAL));
    }

    @Test
    void rechazaCamposObligatoriosNulos() {
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(null,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                null, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, null, ESTAB, PTO_EMI, 1L,
                CODIGO_NUMERICO, TipoEmisionSri.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new ClaveAccesoRequest(FECHA,
                TipoComprobante.FACTURA, RUC, AmbienteSri.PRUEBAS, ESTAB, PTO_EMI, 1L,
                CODIGO_NUMERICO, null));
    }

    @Test
    void rechazaSolicitudNula() {
        assertThrows(IllegalArgumentException.class, () -> generador.generar(null));
    }

    // ---------- Digito verificador ----------

    @Test
    void elUltimoDigitoCoincideConElModuloOnceDeLosPrimerosCuarentaYOcho() {
        String clave = generador.generar(request(AmbienteSri.PRODUCCION, 12345L));
        int esperado = ModuloOnce.digitoVerificador(clave.substring(0, 48));
        assertEquals(esperado, clave.charAt(48) - '0');
        assertTrue(generador.esValida(clave));
    }

    @Test
    void esValidaRechazaClavesMalFormadas() {
        String clave = generador.generar(request(AmbienteSri.PRUEBAS, 42L));
        assertFalse(generador.esValida(null));
        assertFalse(generador.esValida(clave.substring(0, 48)), "48 digitos no es una clave");
        assertFalse(generador.esValida(clave + "0"), "50 digitos no es una clave");
        assertFalse(generador.esValida("X" + clave.substring(1)), "caracter no numerico");

        char ultimo = clave.charAt(48);
        char alterado = (ultimo == '9') ? '0' : (char) (ultimo + 1);
        assertFalse(generador.esValida(clave.substring(0, 48) + alterado), "digito verificador erroneo");
    }

    // ---------- Determinismo ----------

    @Test
    void lasMismasEntradasProducenSiempreLaMismaClave() {
        // Es la propiedad que permite reintentar ante el SRI sin cambiar la clave.
        String primera = generador.generar(request(AmbienteSri.PRODUCCION, 500L));
        String segunda = new ClaveAccesoGenerator().generar(request(AmbienteSri.PRODUCCION, 500L));
        assertEquals(primera, segunda);
    }

    /**
     * Recorrido determinista de 10.000 combinaciones validas (semilla fija, sin
     * dependencias nuevas). Para cada clave: 49 caracteres, todos digitos, y el
     * digito verificador recalculado coincide con el ultimo.
     */
    @Test
    void diezMilClavesGeneradasSonSiempreBienFormadas() {
        Random random = new Random(20260827L);
        AmbienteSri[] ambientes = AmbienteSri.values();

        for (int i = 0; i < 10_000; i++) {
            LocalDate fecha = LocalDate.of(
                    2020 + random.nextInt(15),
                    1 + random.nextInt(12),
                    1 + random.nextInt(28));
            StringBuilder ruc = new StringBuilder(13);
            for (int d = 0; d < 10; d++) {
                ruc.append(random.nextInt(10));
            }
            ruc.append("001");

            ClaveAccesoRequest peticion = new ClaveAccesoRequest(
                    fecha,
                    TipoComprobante.FACTURA,
                    ruc.toString(),
                    ambientes[random.nextInt(ambientes.length)],
                    String.format("%03d", random.nextInt(1000)),
                    String.format("%03d", random.nextInt(1000)),
                    1L + random.nextInt(999_999_999),
                    String.format("%08d", random.nextInt(100_000_000)),
                    TipoEmisionSri.NORMAL);

            String clave = generador.generar(peticion);

            assertEquals(49, clave.length(), "Longitud incorrecta en la iteracion " + i + ": " + clave);
            for (int j = 0; j < clave.length(); j++) {
                char caracter = clave.charAt(j);
                assertTrue(caracter >= '0' && caracter <= '9',
                        "Caracter no numerico en la iteracion " + i + ": " + clave);
            }
            assertEquals(ModuloOnce.digitoVerificador(clave.substring(0, 48)), clave.charAt(48) - '0',
                    "Digito verificador incorrecto en la iteracion " + i + ": " + clave);
            assertTrue(generador.esValida(clave), "esValida fallo en la iteracion " + i + ": " + clave);
        }
    }
}
