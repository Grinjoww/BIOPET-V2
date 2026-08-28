package com.biopet.facturacion.entity;

/**
 * Tipo de identificacion del comprador (Ficha v2.34, TABLA 6). Se persiste el
 * CODIGO de dos digitos, no el nombre de la constante: el codigo es el valor
 * canonico del catalogo y es lo que viaja en el tag
 * {@code <tipoIdentificacionComprador>} del XML.
 *
 * <p>Se declaran los cinco codigos de la tabla y ninguno mas.
 */
public enum TipoIdentificacionSri {
    RUC("04"),
    CEDULA("05"),
    PASAPORTE("06"),
    CONSUMIDOR_FINAL("07"),
    IDENTIFICACION_EXTERIOR("08");

    private final String codigo;

    TipoIdentificacionSri(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static TipoIdentificacionSri desdeCodigo(String codigo) {
        for (TipoIdentificacionSri tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de identificacion SRI no reconocido: " + codigo);
    }
}
