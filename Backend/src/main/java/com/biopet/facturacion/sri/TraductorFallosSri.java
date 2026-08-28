package com.biopet.facturacion.sri;

import org.springframework.oxm.XmlMappingException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.WebServiceTransportException;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Convierte lo que lanza Spring-WS en un {@link TipoFalloSri} concreto.
 *
 * <p>Existe porque el requisito de la fase es DISTINGUIR: timeout, error de
 * conexion, SOAP Fault y respuesta ininteligible tienen consecuencias
 * distintas. En particular, un timeout es el unico caso en el que el
 * comprobante puede haber llegado igualmente al SRI, y por eso obliga a
 * consultar autorizacion antes de reenviar nada.
 *
 * <h2>El orden de las comprobaciones no es cosmetico</h2>
 *
 * <p>{@link WebServiceTransportException} <b>hereda de</b>
 * {@link WebServiceIOException}, asi que tiene que mirarse ANTES; al reves su
 * rama seria inalcanzable y un error HTTP del servidor acabaria clasificado por
 * la logica de entrada/salida, que espera una IOException real como causa.
 * {@link SoapFaultClientException} va la primera por el mismo motivo: es hija
 * de {@code WebServiceClientException} igual que las otras dos.
 */
final class TraductorFallosSri {

    private TraductorFallosSri() {
    }

    static SriComunicacionException traducir(String operacion, long duracionMs, RuntimeException e) {
        if (e instanceof SriComunicacionException ya) {
            return ya;
        }
        if (e instanceof SoapFaultClientException fault) {
            return new SriComunicacionException(TipoFalloSri.SOAP_FAULT, duracionMs,
                    "El SRI respondio con un SOAP Fault en " + operacion + ": "
                            + fault.getFaultStringOrReason(), e);
        }
        // Antes que WebServiceIOException: es una subclase suya.
        if (e instanceof WebServiceTransportException transporte) {
            return new SriComunicacionException(TipoFalloSri.CONEXION, duracionMs,
                    "El servicio de " + operacion + " del SRI respondio con un error de transporte: "
                            + transporte.getMessage(), e);
        }
        if (e instanceof WebServiceIOException io) {
            return desdeIo(operacion, duracionMs, io);
        }
        if (e instanceof XmlMappingException) {
            return new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA, duracionMs,
                    "No se pudo interpretar la respuesta de " + operacion + " del SRI segun el "
                            + "contrato publicado: " + e.getMessage(), e);
        }
        return new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA, duracionMs,
                "Fallo inesperado hablando con el servicio de " + operacion + " del SRI: "
                        + e.getMessage(), e);
    }

    /**
     * Un {@code WebServiceIOException} envuelve la IOException real. El timeout
     * de lectura llega como {@link SocketTimeoutException} y el de conexion
     * tambien; se agrupan porque para BIOPET significan lo mismo: no se sabe el
     * desenlace.
     */
    private static SriComunicacionException desdeIo(String operacion, long duracionMs,
                                                    WebServiceIOException e) {
        Throwable causa = e.getCause();
        if (causa instanceof SocketTimeoutException || causa instanceof InterruptedIOException) {
            return new SriComunicacionException(TipoFalloSri.TIMEOUT, duracionMs,
                    "Se agoto el tiempo de espera hablando con el servicio de " + operacion
                            + " del SRI. El comprobante PUEDE haber llegado: hay que consultar "
                            + "autorizacion antes de reenviar.", e);
        }
        if (causa instanceof ConnectException || causa instanceof UnknownHostException
                || causa instanceof NoRouteToHostException) {
            return new SriComunicacionException(TipoFalloSri.CONEXION, duracionMs,
                    "No se pudo conectar con el servicio de " + operacion + " del SRI: "
                            + causa.getMessage(), e);
        }
        return new SriComunicacionException(TipoFalloSri.CONEXION, duracionMs,
                "Fallo de entrada/salida hablando con el servicio de " + operacion + " del SRI: "
                        + e.getMessage(), e);
    }
}
