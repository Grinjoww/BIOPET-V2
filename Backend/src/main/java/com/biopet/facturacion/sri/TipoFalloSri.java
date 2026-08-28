package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.ResultadoEventoSri;

/**
 * Por que no hubo una respuesta funcional del SRI.
 *
 * <p>Existe para no colapsar en un unico "error" tres situaciones que se
 * diagnostican y se reintentan de forma distinta. Ninguna de ellas es un
 * rechazo del comprobante: la factura conserva numeracion, clave y XML firmado
 * en los tres casos.
 */
public enum TipoFalloSri {

    /** Se agoto el tiempo de conexion o de lectura. El SRI pudo haberlo recibido. */
    TIMEOUT(ResultadoEventoSri.TIMEOUT),

    /** No se pudo abrir la conexion (DNS, red, TLS, servicio caido). */
    CONEXION(ResultadoEventoSri.ERROR_TECNICO),

    /** El servidor respondio con un SOAP Fault en lugar de un cuerpo de negocio. */
    SOAP_FAULT(ResultadoEventoSri.ERROR_TECNICO),

    /** Respondio, pero el cuerpo no se pudo interpretar segun el contrato. */
    RESPUESTA_INVALIDA(ResultadoEventoSri.ERROR_TECNICO);

    private final ResultadoEventoSri resultado;

    TipoFalloSri(ResultadoEventoSri resultado) {
        this.resultado = resultado;
    }

    /**
     * Como se registra en la bitacora. TIMEOUT tiene entrada propia porque es el
     * unico caso en el que el comprobante puede estar ya en el SRI sin que
     * BIOPET lo sepa, y eso cambia la estrategia de reintento (consultar
     * autorizacion antes de reenviar).
     */
    public ResultadoEventoSri resultadoEvento() {
        return resultado;
    }
}
