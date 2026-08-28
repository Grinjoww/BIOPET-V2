package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.converter.AmbienteSriConverter;
import com.biopet.facturacion.entity.converter.CodigoImpuestoSriConverter;
import com.biopet.facturacion.entity.converter.FormaPagoSriConverter;
import com.biopet.facturacion.entity.converter.TipoIdentificacionSriConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas puras (sin Spring y sin base de datos) de los cuatro convertidores
 * JPA del modulo y del catalogo {@link TipoIdentificacionSri}.
 *
 * <p>Lo que se busca demostrar es la propiedad de ida y vuelta: para todo valor
 * del enum, convertir a columna y volver devuelve exactamente la misma
 * constante. Es la garantia de que un dato guardado hoy se sigue leyendo igual
 * manana, y lo que hace seguro NO usar {@code EnumType.ORDINAL}.
 */
class ConvertidoresYCatalogosTest {

    private final AmbienteSriConverter ambienteConverter = new AmbienteSriConverter();
    private final CodigoImpuestoSriConverter impuestoConverter = new CodigoImpuestoSriConverter();
    private final FormaPagoSriConverter formaPagoConverter = new FormaPagoSriConverter();
    private final TipoIdentificacionSriConverter identificacionConverter =
            new TipoIdentificacionSriConverter();

    // ------------------------------------------------------------------
    // Ambiente
    // ------------------------------------------------------------------

    @Test
    void ambienteVaYVuelvePorSuCodigoNumerico() {
        assertThat(ambienteConverter.convertToDatabaseColumn(AmbienteSri.PRUEBAS))
                .isEqualTo((short) 1);
        assertThat(ambienteConverter.convertToDatabaseColumn(AmbienteSri.PRODUCCION))
                .isEqualTo((short) 2);

        for (AmbienteSri ambiente : AmbienteSri.values()) {
            Short columna = ambienteConverter.convertToDatabaseColumn(ambiente);
            assertThat(ambienteConverter.convertToEntityAttribute(columna)).isEqualTo(ambiente);
        }
    }

    @Test
    void ambienteNuloViajaComoNuloEnAmbosSentidos() {
        // Importante: facturas.ambiente es nulo mientras la factura es BORRADOR.
        assertThat(ambienteConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(ambienteConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unAmbienteDesconocidoEnLaBaseNoSeInterpretaASilencio() {
        // Si alguien colase un 9 por SQL crudo (el CHECK lo impide, pero la
        // defensa en profundidad importa), leerlo debe fallar de forma ruidosa
        // en lugar de devolver PRUEBAS por defecto.
        assertThatThrownBy(() -> ambienteConverter.convertToEntityAttribute((short) 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9");
    }

    // ------------------------------------------------------------------
    // Codigo de impuesto
    // ------------------------------------------------------------------

    @Test
    void codigoDeImpuestoVaYVuelvePorSuCodigoDeCatalogo() {
        assertThat(impuestoConverter.convertToDatabaseColumn(CodigoImpuestoSri.IVA)).isEqualTo("2");
        assertThat(impuestoConverter.convertToDatabaseColumn(CodigoImpuestoSri.ICE)).isEqualTo("3");
        assertThat(impuestoConverter.convertToDatabaseColumn(CodigoImpuestoSri.IRBPNR)).isEqualTo("5");

        for (CodigoImpuestoSri codigo : CodigoImpuestoSri.values()) {
            assertThat(impuestoConverter.convertToEntityAttribute(
                    impuestoConverter.convertToDatabaseColumn(codigo))).isEqualTo(codigo);
        }

        assertThat(impuestoConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(impuestoConverter.convertToEntityAttribute(null)).isNull();
    }

    // ------------------------------------------------------------------
    // Forma de pago
    // ------------------------------------------------------------------

    @Test
    void formaDePagoVaYVuelvePorSuCodigoDeDosCaracteres() {
        assertThat(formaPagoConverter.convertToDatabaseColumn(FormaPagoSri.TARJETA_CREDITO))
                .isEqualTo("19");

        for (FormaPagoSri formaPago : FormaPagoSri.values()) {
            String columna = formaPagoConverter.convertToDatabaseColumn(formaPago);
            assertThat(columna).hasSize(2);
            assertThat(formaPagoConverter.convertToEntityAttribute(columna)).isEqualTo(formaPago);
        }

        assertThat(formaPagoConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(formaPagoConverter.convertToEntityAttribute(null)).isNull();
    }

    // ------------------------------------------------------------------
    // Tipo de identificacion
    // ------------------------------------------------------------------

    @Test
    void tipoDeIdentificacionDeclaraLosCincoCodigosDeLaTabla6() {
        assertThat(TipoIdentificacionSri.RUC.codigo()).isEqualTo("04");
        assertThat(TipoIdentificacionSri.CEDULA.codigo()).isEqualTo("05");
        assertThat(TipoIdentificacionSri.PASAPORTE.codigo()).isEqualTo("06");
        assertThat(TipoIdentificacionSri.CONSUMIDOR_FINAL.codigo()).isEqualTo("07");
        assertThat(TipoIdentificacionSri.IDENTIFICACION_EXTERIOR.codigo()).isEqualTo("08");
        assertThat(TipoIdentificacionSri.values()).hasSize(5);
    }

    @Test
    void tipoDeIdentificacionVaYVuelve() {
        for (TipoIdentificacionSri tipo : TipoIdentificacionSri.values()) {
            String columna = identificacionConverter.convertToDatabaseColumn(tipo);
            assertThat(identificacionConverter.convertToEntityAttribute(columna)).isEqualTo(tipo);
            assertThat(TipoIdentificacionSri.desdeCodigo(tipo.codigo())).isEqualTo(tipo);
        }

        assertThat(identificacionConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(identificacionConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unTipoDeIdentificacionDesconocidoFallaDeFormaRuidosa() {
        assertThatThrownBy(() -> TipoIdentificacionSri.desdeCodigo("99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
        assertThatThrownBy(() -> identificacionConverter.convertToEntityAttribute("99"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
