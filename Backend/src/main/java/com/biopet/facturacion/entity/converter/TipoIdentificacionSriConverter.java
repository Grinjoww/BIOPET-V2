package com.biopet.facturacion.entity.converter;

import com.biopet.facturacion.entity.TipoIdentificacionSri;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link TipoIdentificacionSri} como su codigo de dos digitos
 * (TABLA 6): "04" RUC, "05" cedula, "06" pasaporte, "07" consumidor final,
 * "08" identificacion del exterior.
 */
@Converter
public class TipoIdentificacionSriConverter implements AttributeConverter<TipoIdentificacionSri, String> {

    @Override
    public String convertToDatabaseColumn(TipoIdentificacionSri tipo) {
        return tipo == null ? null : tipo.codigo();
    }

    @Override
    public TipoIdentificacionSri convertToEntityAttribute(String codigo) {
        return codigo == null ? null : TipoIdentificacionSri.desdeCodigo(codigo);
    }
}
