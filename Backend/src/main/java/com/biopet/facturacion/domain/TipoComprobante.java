package com.biopet.facturacion.domain;

/**
 * Tipo de comprobante electronico (Ficha v2.34, seccion 5.4, TABLA 3). Ocupa 2
 * digitos dentro de la clave de acceso y viaja en el XML como {@code <codDoc>}.
 *
 * <p>Solo se declara FACTURA. La TABLA 3 define ademas 03 (liquidacion de
 * compra), 04 (nota de credito), 05 (nota de debito), 06 (guia de remision) y
 * 07 (comprobante de retencion), pero BIOPET todavia no emite ninguno de esos
 * documentos: declararlos aqui sugeriria un soporte que no existe. Este enum
 * existe unicamente para que el "01" no quede como literal suelto.
 */
public enum TipoComprobante {
    FACTURA("01");

    private final String codDoc;

    TipoComprobante(String codDoc) {
        this.codDoc = codDoc;
    }

    public String codDoc() {
        return codDoc;
    }
}
