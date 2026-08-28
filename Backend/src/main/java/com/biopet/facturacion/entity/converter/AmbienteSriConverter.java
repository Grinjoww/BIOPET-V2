package com.biopet.facturacion.entity.converter;

import com.biopet.facturacion.domain.AmbienteSri;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link AmbienteSri} como el codigo numerico del catalogo del SRI
 * (TABLA 4): 1 = PRUEBAS, 2 = PRODUCCION.
 *
 * <p>Por que un converter y no {@code @Enumerated(EnumType.STRING)}: el
 * ambiente NO es un estado interno de BIOPET sino un codigo del catalogo del
 * SRI, y el valor canonico de ese catalogo es el numero. Guardarlo como
 * SMALLINT permite ademas la restriccion declarativa
 * {@code CHECK (ambiente IN (1, 2))}, que es la que exige el diseno del
 * contador por ambiente. Tampoco se usa {@code EnumType.ORDINAL}: eso ataria el
 * valor almacenado al ORDEN de declaracion del enum, y renombrar o reordenar
 * las constantes corromperia datos ya guardados.
 *
 * <p>{@code autoApply = false} (el defecto) a proposito: se aplica solo con
 * {@code @Convert} explicito en cada campo, para no alterar el mapeo de nada
 * fuera de este modulo.
 */
@Converter
public class AmbienteSriConverter implements AttributeConverter<AmbienteSri, Short> {

    @Override
    public Short convertToDatabaseColumn(AmbienteSri ambiente) {
        return ambiente == null ? null : Short.valueOf(ambiente.codigo());
    }

    @Override
    public AmbienteSri convertToEntityAttribute(Short codigo) {
        return codigo == null ? null : AmbienteSri.desdeCodigo(String.valueOf(codigo));
    }
}
