package com.biopet.facturacion.domain;

/**
 * Tipo de ambiente del SRI (Ficha Tecnica de Comprobantes Electronicos Offline
 * v2.34, seccion 5.5, TABLA 4). Ocupa 1 digito dentro de la clave de acceso.
 *
 * <p>El ambiente NO se lee aqui de ninguna variable de entorno: las clases de
 * este paquete son puras y lo reciben como dato, de modo que ambos ambientes
 * son testeables sin configuracion.
 */
public enum AmbienteSri {
    PRUEBAS("1"),
    PRODUCCION("2");

    private final String codigo;

    AmbienteSri(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static AmbienteSri desdeCodigo(String codigo) {
        for (AmbienteSri ambiente : values()) {
            if (ambiente.codigo.equals(codigo)) {
                return ambiente;
            }
        }
        throw new IllegalArgumentException("Codigo de ambiente SRI no reconocido: " + codigo);
    }
}
