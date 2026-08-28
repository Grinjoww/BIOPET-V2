package com.biopet.facturacion.entity.converter;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link CodigoImpuestoSri} como su codigo del catalogo (TABLA 16):
 * "2" IVA, "3" ICE, "5" IRBPNR. Reutiliza el enum del nucleo fiscal de la
 * Fase 2 en lugar de duplicar los codigos en la capa de persistencia.
 */
@Converter
public class CodigoImpuestoSriConverter implements AttributeConverter<CodigoImpuestoSri, String> {

    @Override
    public String convertToDatabaseColumn(CodigoImpuestoSri codigo) {
        return codigo == null ? null : codigo.codigo();
    }

    @Override
    public CodigoImpuestoSri convertToEntityAttribute(String codigo) {
        return codigo == null ? null : CodigoImpuestoSri.desdeCodigo(codigo);
    }
}
