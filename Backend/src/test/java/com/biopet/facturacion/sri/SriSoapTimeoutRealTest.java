package com.biopet.facturacion.sri;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los timeouts configurados se aplican DE VERDAD sobre el transporte.
 *
 * <p>El resto de la suite simula los fallos con {@code MockWebServiceServer},
 * que sustituye el transporte entero: sirve para comprobar como reacciona
 * BIOPET, pero no demuestra que {@code sri.soap.read-timeout} llegue a
 * {@code HttpURLConnection}. Eso es justo lo que se prueba aqui, y no es un
 * detalle: un timeout que no se aplica no se nota hasta que un dia el SRI acepta
 * la conexion, no contesta, y un hilo de BIOPET se queda esperando para siempre.
 *
 * <p>El servidor es un {@link ServerSocket} local que ACEPTA la conexion y no
 * responde nunca. No hay salida a Internet: todo ocurre en 127.0.0.1.
 */
class SriSoapTimeoutRealTest {

    /** Corto para que el test sea rapido, pero muy por encima del ruido local. */
    private static final Duration LECTURA = Duration.ofMillis(600);

    private ServerSocket mudo;
    private final List<Socket> aceptados = new ArrayList<>();
    private Thread hilo;

    @AfterEach
    void tearDown() throws IOException {
        if (hilo != null) hilo.interrupt();
        for (Socket socket : aceptados) {
            try {
                socket.close();
            } catch (IOException ignorada) {
                // El test ya termino; cerrar es best effort.
            }
        }
        if (mudo != null && !mudo.isClosed()) mudo.close();
    }

    @Test
    void unServidorQueAceptaYNoRespondeProduceTimeoutYNoUnaEsperaInfinita() throws Exception {
        int puerto = arrancarServidorMudo();

        SriSoapProperties propiedades = propiedades(
                "http://127.0.0.1:" + puerto + "/RecepcionComprobantesOffline");
        SriRecepcionClient cliente = new SriRecepcionClient(
                new SriSoapConfig().sriRecepcionWebServiceTemplate(
                        marshallerRecepcion(), propiedades));

        long inicio = System.nanoTime();
        SriComunicacionException fallo = capturar(() ->
                cliente.validarComprobante("<factura/>".getBytes(StandardCharsets.UTF_8)));
        long transcurridoMs = (System.nanoTime() - inicio) / 1_000_000L;

        assertThat(fallo.getTipo()).isEqualTo(TipoFalloSri.TIMEOUT);
        // La cota superior es lo que importa: sin read-timeout esto no
        // terminaria nunca. El margen es amplio a proposito para no volver
        // inestable el build en una maquina cargada.
        assertThat(transcurridoMs)
                .as("debe cortar cerca del read-timeout configurado (%s)", LECTURA)
                .isLessThan(15_000L);
        assertThat(fallo.getDuracionMs()).isNotNegative();
    }

    @Test
    void unPuertoCerradoSeClasificaComoErrorDeConexionYNoComoRechazo() throws Exception {
        int puertoLibre;
        try (ServerSocket efimero = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            puertoLibre = efimero.getLocalPort();
        }

        SriSoapProperties propiedades = propiedades(
                "http://127.0.0.1:" + puertoLibre + "/AutorizacionComprobantesOffline");
        SriAutorizacionClient cliente = new SriAutorizacionClient(
                new SriSoapConfig().sriAutorizacionWebServiceTemplate(
                        marshallerAutorizacion(), propiedades));

        SriComunicacionException fallo = capturar(() -> cliente.autorizacionComprobante(
                "2609202601099000000010012001000000001123456781"));

        // Ni RECHAZADA ni nada parecido: la factura no se ha movido.
        assertThat(fallo.getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(fallo.getTipo().resultadoEvento().name()).isEqualTo("ERROR_TECNICO");
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private int arrancarServidorMudo() throws IOException {
        mudo = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
        hilo = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !mudo.isClosed()) {
                try {
                    // Se acepta y no se escribe nada: el cliente se queda
                    // esperando la respuesta, que es el escenario a reproducir.
                    aceptados.add(mudo.accept());
                } catch (IOException e) {
                    return;
                }
            }
        }, "sri-servidor-mudo");
        hilo.setDaemon(true);
        hilo.start();
        return mudo.getLocalPort();
    }

    private static SriSoapProperties propiedades(String url) {
        SriSoapProperties propiedades = new SriSoapProperties();
        propiedades.setRecepcionUrl(url);
        propiedades.setAutorizacionUrl(url);
        propiedades.setConnectTimeout(Duration.ofSeconds(2));
        propiedades.setReadTimeout(LECTURA);
        return propiedades;
    }

    private static Jaxb2Marshaller marshallerRecepcion() {
        return new SriSoapConfig().sriRecepcionMarshaller();
    }

    private static Jaxb2Marshaller marshallerAutorizacion() {
        return new SriSoapConfig().sriAutorizacionMarshaller();
    }

    /**
     * Ejecuta la accion y devuelve el fallo, para poder afirmar sobre su TIPO.
     * Se hace con try/catch y no con AssertJ porque lo interesante no es que
     * lance, sino que lance el tipo correcto.
     */
    private static SriComunicacionException capturar(Runnable accion) {
        try {
            accion.run();
        } catch (SriComunicacionException e) {
            return e;
        }
        throw new AssertionError("Se esperaba un SriComunicacionException y no se lanzo ninguno.");
    }
}
