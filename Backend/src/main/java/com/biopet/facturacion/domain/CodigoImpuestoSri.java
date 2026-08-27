package com.biopet.facturacion.domain;

/**
 * Codigo de impuesto (Ficha v2.34, seccion 9.12, TABLA 16). Corresponde al tag
 * {@code <codigo>} dentro de {@code <impuesto>}; el XSD oficial de factura
 * restringe este campo al patron {@code [235]}, exactamente estos tres valores.
 */
public enum CodigoImpuestoSri {
    IVA("2"),
    ICE("3"),
    IRBPNR("5");

    private final String codigo;

    CodigoImpuestoSri(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static CodigoImpuestoSri desdeCodigo(String codigo) {
        for (CodigoImpuestoSri impuesto : values()) {
            if (impuesto.codigo.equals(codigo)) {
                return impuesto;
            }
        }
        throw new IllegalArgumentException("Codigo de impuesto SRI no reconocido: " + codigo);
    }
}
