package com.biopet.facturacion.sri;

/**
 * No hubo respuesta funcional del SRI: timeout, fallo de conexion, SOAP Fault o
 * cuerpo ininteligible.
 *
 * <p>Es deliberadamente distinta de un rechazo. Un comprobante DEVUELTO o NO
 * AUTORIZADO es una respuesta del SRI y se persiste como tal; esto es la
 * AUSENCIA de respuesta, y la factura queda exactamente igual que antes de la
 * llamada salvo por el evento de bitacora. Confundir las dos cosas llevaria a
 * marcar RECHAZADA una factura perfectamente valida porque se cayo la red.
 *
 * <p>Lleva la duracion de la llamada fallida porque la bitacora la necesita: en
 * un timeout, "tardo 60 s" es justamente el dato que explica el evento.
 */
public class SriComunicacionException extends RuntimeException {

    private final TipoFalloSri tipo;
    private final long duracionMs;

    public SriComunicacionException(TipoFalloSri tipo, long duracionMs, String mensaje,
                                    Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
        this.duracionMs = duracionMs;
    }

    public TipoFalloSri getTipo() {
        return tipo;
    }

    public long getDuracionMs() {
        return duracionMs;
    }
}
