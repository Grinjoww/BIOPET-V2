package com.biopet.facturacion.sri;

/**
 * Un mensaje funcional del SRI, ya desligado del binding SOAP.
 *
 * <p>Se conservan los CUATRO campos del contrato. El que gobierna las
 * decisiones es {@code identificador}: es el codigo oficial del catalogo del
 * SRI y es estable, mientras que {@code mensaje} es texto libre que el SRI
 * puede reformular sin previo aviso. Ninguna rama de este modulo se decide
 * comparando cadenas de texto.
 *
 * <p>{@code informacionAdicional} suele traer el detalle util (que campo, que
 * valor); por eso se persiste tambien y no se descarta.
 */
public record MensajeSri(String identificador,
                         String mensaje,
                         String informacionAdicional,
                         String tipo) {

    /** true si este mensaje lleva el codigo indicado, comparando sin espacios. */
    public boolean tieneCodigo(String codigo) {
        return identificador != null && identificador.trim().equals(codigo);
    }
}
