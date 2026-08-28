package com.biopet.facturacion.exception;

/**
 * El usuario propietario funcional o la mascota asociada no sirven para esta
 * factura: el usuario esta inactivo, la mascota esta inactiva, o la mascota
 * pertenece a otro usuario.
 *
 * <p>No confundir con el comprador fiscal. El titular es quien ve la factura en
 * BIOPET; el comprador es a nombre de quien se emite, y puede ser un tercero
 * (ver el snapshot comprador* de la factura).
 */
public class TitularFacturaInvalidoException extends RuntimeException {

    public TitularFacturaInvalidoException(String mensaje) {
        super(mensaje);
    }
}
