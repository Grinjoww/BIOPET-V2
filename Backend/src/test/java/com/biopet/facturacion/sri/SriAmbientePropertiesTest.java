package com.biopet.facturacion.sri;

import com.biopet.facturacion.domain.AmbienteSri;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correccion pre-commit de la Fase 8A, punto 1: el ambiente fiscal es
 * exclusivamente del backend, y debe ser coherente con los endpoints SOAP
 * configurados.
 *
 * <p>Prueba unitaria pura -sin Spring, sin base de datos-: construye
 * {@link SriAmbienteProperties} a mano sobre un {@link SriSoapProperties}
 * tambien construido a mano, y llama a {@code validar()} directamente. Es
 * deliberadamente mas barato que levantar el contexto completo solo para
 * comprobar cuatro combinaciones de dos enums.
 */
class SriAmbientePropertiesTest {

    private static final String ENDPOINT_PRUEBAS =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    private static final String ENDPOINT_PRODUCCION =
            "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";

    @Test
    void porDefectoEsPruebasYNoRequiereConfiguracion() {
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap(ENDPOINT_PRUEBAS));

        org.assertj.core.api.Assertions.assertThat(propiedades.getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    @Test
    void pruebasConEndpointsDePruebasEsConsistente() {
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap(ENDPOINT_PRUEBAS));
        propiedades.setAmbiente(AmbienteSri.PRUEBAS);

        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    @Test
    void produccionConEndpointsDeProduccionEsConsistente() {
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap(ENDPOINT_PRODUCCION));
        propiedades.setAmbiente(AmbienteSri.PRODUCCION);

        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    @Test
    void pruebasConEndpointsDeProduccionFallaAlArrancar() {
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap(ENDPOINT_PRODUCCION));
        propiedades.setAmbiente(AmbienteSri.PRUEBAS);

        assertThatThrownBy(propiedades::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sri.ambiente=PRUEBAS")
                .hasMessageContaining("produccion");
    }

    @Test
    void produccionConEndpointsDePruebasFallaAlArrancar() {
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap(ENDPOINT_PRUEBAS));
        propiedades.setAmbiente(AmbienteSri.PRODUCCION);

        assertThatThrownBy(propiedades::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sri.ambiente=PRODUCCION");
    }

    /**
     * Un endpoint local de pruebas (como los que usa toda la suite SOAP
     * simulada de este modulo, {@code http://localhost:1/...}) no es
     * produccion: PRUEBAS + localhost debe seguir siendo valido, o esta prueba
     * -y toda la suite de integracion de facturacion- dejaria de arrancar.
     */
    @Test
    void pruebasConEndpointLocalDeTestSigueSiendoValido() {
        SriSoapProperties soap = new SriSoapProperties();
        soap.setRecepcionUrl("http://localhost:1/RecepcionComprobantesOffline");
        soap.setAutorizacionUrl("http://localhost:1/AutorizacionComprobantesOffline");
        SriAmbienteProperties propiedades = new SriAmbienteProperties(soap);
        propiedades.setAmbiente(AmbienteSri.PRUEBAS);

        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    private static SriSoapProperties soap(String urlRecepcion) {
        SriSoapProperties propiedades = new SriSoapProperties();
        propiedades.setRecepcionUrl(urlRecepcion);
        propiedades.setAutorizacionUrl(urlRecepcion.replace("Recepcion", "Autorizacion"));
        return propiedades;
    }
}
