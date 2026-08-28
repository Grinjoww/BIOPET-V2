package com.biopet.facturacion.entity.converter;

import com.biopet.facturacion.domain.FormaPagoSri;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link FormaPagoSri} como su codigo de dos caracteres (TABLA 24):
 * "01", "15", "16", "17", "18", "19", "20", "21". Reutiliza el enum de la
 * Fase 2; el CHECK de {@code factura_pagos} declara exactamente esos ocho
 * codigos, de modo que un valor invalido se rechaza en la BD aunque llegue por
 * SQL crudo.
 */
@Converter
public class FormaPagoSriConverter implements AttributeConverter<FormaPagoSri, String> {

    @Override
    public String convertToDatabaseColumn(FormaPagoSri formaPago) {
        return formaPago == null ? null : formaPago.codigo();
    }

    @Override
    public FormaPagoSri convertToEntityAttribute(String codigo) {
        return codigo == null ? null : FormaPagoSri.desdeCodigo(codigo);
    }
}
