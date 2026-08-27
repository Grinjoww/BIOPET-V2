package com.biopet.facturacion.domain;

/**
 * Formas de pago (Ficha v2.34, TABLA 24). Los ocho codigos declarados son los
 * unicos que la tabla publica, y todos figuran con FECHA FIN vacia, es decir
 * siguen vigentes. No se inventa ninguno.
 *
 * <p>Nota sobre el XSD: el tipo {@code formaPago} del esquema oficial admite el
 * patron {@code [0][1-9]|[1][0-9]|[2][0-1]}, un rango mas amplio (01-09, 10-19,
 * 20-21) que los ocho codigos del catalogo. Se toma como fuente el catalogo, no
 * el patron: un codigo que pasa el XSD pero no existe en la TABLA 24 seria
 * rechazado en la validacion de negocio del SRI, no en la de esquema.
 */
public enum FormaPagoSri {
    SIN_UTILIZACION_SISTEMA_FINANCIERO("01", "Sin utilizacion del sistema financiero"),
    COMPENSACION_DEUDAS("15", "Compensacion de deudas"),
    TARJETA_DEBITO("16", "Tarjeta de debito"),
    DINERO_ELECTRONICO("17", "Dinero electronico"),
    TARJETA_PREPAGO("18", "Tarjeta prepago"),
    TARJETA_CREDITO("19", "Tarjeta de credito"),
    OTROS_CON_SISTEMA_FINANCIERO("20", "Otros con utilizacion del sistema financiero"),
    ENDOSO_TITULOS("21", "Endoso de titulos");

    private final String codigo;
    private final String descripcion;

    FormaPagoSri(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public static FormaPagoSri desdeCodigo(String codigo) {
        for (FormaPagoSri formaPago : values()) {
            if (formaPago.codigo.equals(codigo)) {
                return formaPago;
            }
        }
        throw new IllegalArgumentException("Forma de pago SRI no reconocida: " + codigo);
    }
}
