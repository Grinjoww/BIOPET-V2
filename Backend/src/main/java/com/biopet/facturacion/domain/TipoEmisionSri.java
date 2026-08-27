package com.biopet.facturacion.domain;

/**
 * Tipo de emision (Ficha v2.34, seccion 5.3, TABLA 2). Ocupa 1 digito dentro de
 * la clave de acceso.
 *
 * <p>Un solo valor a proposito. La nota al pie de la TABLA 2 dice literalmente:
 * "Para el metodo de autorizacion offline, solo existe el tipo de emision
 * normal". No se modelan alternativas inexistentes.
 */
public enum TipoEmisionSri {
    NORMAL("1");

    private final String codigo;

    TipoEmisionSri(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
