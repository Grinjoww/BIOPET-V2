package com.biopet.facturacion.sri;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sonda de conectividad contra CELCER. <b>NO envia ningun comprobante.</b>
 *
 * <h2>Por que esta desactivada por defecto</h2>
 *
 * <p>Dos motivos, y los dos importan:
 *
 * <ul>
 *   <li>La suite normal no puede depender de Internet ni de que un servicio del
 *       SRI este arriba. Un test que falla porque CELCER esta en mantenimiento
 *       no dice nada sobre BIOPET y acaba enseniando al equipo a ignorar los
 *       fallos del build.</li>
 *   <li>Esta fase NO manda comprobantes a CELCER a proposito. El certificado
 *       disponible es autofirmado y de pruebas: un rechazo del SRI mezclaria
 *       cuatro causas posibles -certificado, RUC, algoritmo de firma y XML- y
 *       no permitiria concluir nada sobre ninguna. La aceptacion real queda
 *       pendiente de tener certificado emitido por entidad acreditada, RUC
 *       habilitado y ambiente de pruebas activado.</li>
 * </ul>
 *
 * <p>Lo que si aporta, cuando se ejecuta a mano, es evidencia tecnica de que las
 * URL configuradas existen, resuelven por DNS, negocian TLS y publican un WSDL
 * con las operaciones que este modulo implementa. Es una peticion GET al
 * contrato: no crea nada, no consume numeracion y no deja rastro fiscal.
 *
 * <p>Para ejecutarla:
 * <pre>
 *   SRI_SONDA_CELCER=true mvn test -Dtest=CelcerSondaConectividadTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SRI_SONDA_CELCER", matches = "true")
class CelcerSondaConectividadTest {

    private static final String BASE = "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/";

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Test
    void elWsdlDeRecepcionPublicaLaOperacionValidarComprobante() throws Exception {
        String wsdl = descargar(BASE + "RecepcionComprobantesOffline?wsdl");

        assertThat(wsdl).contains("validarComprobante");
        assertThat(wsdl).contains("http://ec.gob.sri.ws.recepcion");
    }

    @Test
    void elWsdlDeAutorizacionPublicaLaOperacionAutorizacionComprobante() throws Exception {
        String wsdl = descargar(BASE + "AutorizacionComprobantesOffline?wsdl");

        assertThat(wsdl).contains("autorizacionComprobante");
        assertThat(wsdl).contains("http://ec.gob.sri.ws.autorizacion");
    }

    /**
     * GET del contrato y nada mas. Sin POST, sin cuerpo, sin comprobante: es
     * literalmente lo mismo que abrir la URL en un navegador.
     */
    private static String descargar(String url) throws Exception {
        HttpURLConnection conexion = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conexion.setRequestMethod("GET");
        conexion.setConnectTimeout((int) ESPERA.toMillis());
        conexion.setReadTimeout((int) ESPERA.toMillis());
        try {
            assertThat(conexion.getResponseCode()).isEqualTo(200);
            return new String(conexion.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conexion.disconnect();
        }
    }
}
