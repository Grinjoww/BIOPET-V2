package com.biopet.facturacion.sri;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Configuracion de los web services offline del SRI ({@code sri.soap.*}).
 *
 * <h2>El default es CELCER, nunca produccion</h2>
 *
 * <p>Los dos endpoints por defecto apuntan al ambiente de PRUEBAS. Es una
 * decision de seguridad, no de comodidad: un despliegue mal configurado debe
 * fallar hacia pruebas, no emitir comprobantes reales contra el SRI de
 * produccion. Los de produccion se inyectan por variable de entorno
 * ({@code SRI_SOAP_RECEPCION_URL}, {@code SRI_SOAP_AUTORIZACION_URL}) de forma
 * explicita y deliberada.
 *
 * <p>No hay ninguna bandera "ambiente" que elija la URL dentro del cliente. Los
 * clientes reciben la URL ya resuelta; el que decide es el despliegue.
 *
 * <h2>Timeouts obligatorios</h2>
 *
 * <p>Ambos tienen valor por defecto finito. Sin ellos, {@code HttpURLConnection}
 * espera indefinidamente: un SRI que acepta la conexion y no responde dejaria
 * un hilo de BIOPET colgado para siempre. El de lectura es holgado (60 s)
 * porque la autorizacion del SRI es lenta en horas punta, pero finito.
 *
 * <h2>Sin secretos</h2>
 *
 * <p>Aqui no hay credenciales: los servicios offline del SRI no las usan. La
 * identidad la aporta la firma XAdES del propio comprobante. El certificado
 * sigue configurandose aparte, en {@code sri.firma.*}.
 */
@Component
@ConfigurationProperties(prefix = "sri.soap")
public class SriSoapProperties {

    private static final Logger log = LoggerFactory.getLogger(SriSoapProperties.class);

    /** Hosts en los que se tolera texto plano: solo pruebas locales. */
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    /** Host del ambiente de produccion del SRI, para poder avisar de que se esta usando. */
    private static final String HOST_PRODUCCION = "cel.sri.gob.ec";

    private String recepcionUrl =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";

    private String autorizacionUrl =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * Comprueba la forma de las URL al arrancar y deja constancia en el log de
     * contra que ambiente va a hablar BIOPET.
     *
     * <p>Fallar aqui es correcto: una URL mal escrita no se descubre "cuando se
     * facture", se descubre al desplegar. Y avisar en WARN de que se apunta a
     * produccion evita el peor escenario de esta fase, que es enviar
     * comprobantes reales creyendo estar en pruebas.
     */
    @PostConstruct
    void validar() {
        exigirUrlValida("sri.soap.recepcion-url", recepcionUrl);
        exigirUrlValida("sri.soap.autorizacion-url", autorizacionUrl);
        exigirPositivo("sri.soap.connect-timeout", connectTimeout);
        exigirPositivo("sri.soap.read-timeout", readTimeout);

        if (apuntaAProduccion()) {
            log.warn("SRI: endpoints de PRODUCCION configurados ({}). Los comprobantes enviados "
                    + "tendran validez tributaria real.", HOST_PRODUCCION);
        } else {
            log.info("SRI: endpoints de pruebas. recepcion={} autorizacion={}",
                    recepcionUrl, autorizacionUrl);
        }
    }

    /** true si alguno de los dos endpoints apunta al ambiente de produccion del SRI. */
    public boolean apuntaAProduccion() {
        return host(recepcionUrl).equals(HOST_PRODUCCION) || host(autorizacionUrl).equals(HOST_PRODUCCION);
    }

    private static String host(String url) {
        try {
            String h = new URI(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private static void exigirUrlValida(String clave, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(clave + " no puede estar vacia.");
        }
        URI uri;
        try {
            uri = new URI(valor);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(clave + " no es una URL valida: " + valor, e);
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!esquema.equals("https") && !(esquema.equals("http") && LOOPBACK.contains(host))) {
            throw new IllegalStateException(clave + " debe ser https (solo se admite http contra "
                    + "localhost, para pruebas): " + valor);
        }
        // El endpoint no es el WSDL. Enviar el POST a "...?wsdl" devuelve el
        // contrato, no una respuesta de negocio, y el fallo resultante es
        // confuso; mejor rechazarlo aqui con un mensaje que lo diga.
        if (valor.toLowerCase(Locale.ROOT).contains("?wsdl")) {
            throw new IllegalStateException(clave + " debe ser el endpoint del servicio, sin el "
                    + "sufijo ?wsdl: " + valor);
        }
    }

    private static void exigirPositivo(String clave, Duration valor) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalStateException(clave + " debe ser una duracion positiva; un timeout "
                    + "ausente o nulo equivale a esperar indefinidamente.");
        }
    }

    public String getRecepcionUrl() {
        return recepcionUrl;
    }

    public void setRecepcionUrl(String recepcionUrl) {
        this.recepcionUrl = recepcionUrl;
    }

    public String getAutorizacionUrl() {
        return autorizacionUrl;
    }

    public void setAutorizacionUrl(String autorizacionUrl) {
        this.autorizacionUrl = autorizacionUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public String toString() {
        return "SriSoapProperties[recepcion=" + recepcionUrl
                + ", autorizacion=" + autorizacionUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
