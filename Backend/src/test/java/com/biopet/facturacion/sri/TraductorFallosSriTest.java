package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.ResultadoEventoSri;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.UnmarshallingFailureException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.WebServiceTransportException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clasificacion de los fallos que puede lanzar Spring-WS.
 *
 * <p>Merece prueba propia porque es la pieza que decide la ESTRATEGIA DE
 * REINTENTO. Confundir un timeout con un error de conexion no es un detalle de
 * etiquetado: solo el timeout obliga a consultar autorizacion antes de volver a
 * enviar, porque es el unico caso en el que el comprobante puede estar ya en el
 * SRI sin que BIOPET lo sepa. Clasificarlo mal es el camino directo a emitir el
 * mismo comprobante dos veces.
 */
class TraductorFallosSriTest {

    private static final long DURACION = 1234L;

    @Test
    void unSocketTimeoutEsTimeout() {
        assertThat(traducir(new WebServiceIOException("io", new SocketTimeoutException("timeout")))
                .getTipo()).isEqualTo(TipoFalloSri.TIMEOUT);
    }

    @Test
    void unaInterrupcionDeEntradaSalidaTambienEsTimeout() {
        // InterruptedIOException es la superclase de SocketTimeoutException y la
        // que usan algunos transportes al cortar por tiempo.
        assertThat(traducir(new WebServiceIOException("io", new InterruptedIOException("corte")))
                .getTipo()).isEqualTo(TipoFalloSri.TIMEOUT);
    }

    @Test
    void losFallosDeRedSonErroresDeConexion() {
        assertThat(traducir(new WebServiceIOException("io", new ConnectException("refused")))
                .getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(traducir(new WebServiceIOException("io", new UnknownHostException("dns")))
                .getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(traducir(new WebServiceIOException("io", new NoRouteToHostException("ruta")))
                .getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(traducir(new WebServiceIOException("io", new IOException("otro")))
                .getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(traducir(new WebServiceIOException("sin causa"))
                .getTipo()).isEqualTo(TipoFalloSri.CONEXION);
    }

    @Test
    void unErrorDeTransporteSeClasificaComoConexionYNoCaeEnLaRamaDeEntradaSalida() {
        // WebServiceTransportException HEREDA de WebServiceIOException. Si el
        // orden de las comprobaciones se invirtiera, este caso acabaria en la
        // rama de IO, que espera una IOException como causa y aqui no la hay.
        SriComunicacionException fallo =
                traducir(new WebServiceTransportException("500 Internal Server Error"));

        assertThat(fallo.getTipo()).isEqualTo(TipoFalloSri.CONEXION);
        assertThat(fallo.getMessage()).contains("error de transporte");
    }

    @Test
    void unaRespuestaQueNoEncajaConElContratoEsRespuestaInvalida() {
        assertThat(traducir(new UnmarshallingFailureException("elemento inesperado"))
                .getTipo()).isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
    }

    @Test
    void cualquierOtroFalloInesperadoNoSeConfundeConUnRechazo() {
        SriComunicacionException fallo = traducir(new IllegalStateException("algo raro"));

        assertThat(fallo.getTipo()).isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
        assertThat(fallo.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unFalloYaClasificadoSeDejaComoEsta() {
        SriComunicacionException original = new SriComunicacionException(
                TipoFalloSri.TIMEOUT, 99L, "ya clasificado", null);

        assertThat(traducir(original)).isSameAs(original);
    }

    @Test
    void laDuracionDeLaLlamadaFallidaSeConserva() {
        // En un timeout, "tardo N ms" es justamente el dato que explica el
        // evento de la bitacora.
        assertThat(traducir(new WebServiceIOException("io", new SocketTimeoutException("t")))
                .getDuracionMs()).isEqualTo(DURACION);
    }

    @Test
    void cadaTipoSabeComoSeRegistraEnLaBitacora() {
        // Solo el timeout tiene entrada propia: es el unico que cambia la
        // estrategia de reintento.
        assertThat(TipoFalloSri.TIMEOUT.resultadoEvento())
                .isEqualTo(ResultadoEventoSri.TIMEOUT);
        assertThat(TipoFalloSri.CONEXION.resultadoEvento())
                .isEqualTo(ResultadoEventoSri.ERROR_TECNICO);
        assertThat(TipoFalloSri.SOAP_FAULT.resultadoEvento())
                .isEqualTo(ResultadoEventoSri.ERROR_TECNICO);
        assertThat(TipoFalloSri.RESPUESTA_INVALIDA.resultadoEvento())
                .isEqualTo(ResultadoEventoSri.ERROR_TECNICO);
    }

    private static SriComunicacionException traducir(RuntimeException e) {
        return TraductorFallosSri.traducir("recepcion", DURACION, e);
    }
}
