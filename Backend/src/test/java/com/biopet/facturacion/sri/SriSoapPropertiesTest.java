package com.biopet.facturacion.sri;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuracion de los endpoints del SRI.
 *
 * <p>Lo que se protege aqui no es cosmetico: que el default sea CELCER y no
 * produccion es lo unico que impide que un despliegue con una variable de
 * entorno mal escrita empiece a emitir comprobantes con validez tributaria
 * real.
 */
class SriSoapPropertiesTest {

    @Test
    void porDefectoApuntaACelcerYNuncaAProduccion() {
        SriSoapProperties propiedades = new SriSoapProperties();

        assertThat(propiedades.getRecepcionUrl()).startsWith("https://celcer.sri.gob.ec/");
        assertThat(propiedades.getAutorizacionUrl()).startsWith("https://celcer.sri.gob.ec/");
        assertThat(propiedades.apuntaAProduccion()).isFalse();
        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    @Test
    void losTimeoutsPorDefectoSonFinitos() {
        SriSoapProperties propiedades = new SriSoapProperties();

        assertThat(propiedades.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(propiedades.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void detectaQueSeApuntaAProduccion() {
        SriSoapProperties propiedades = new SriSoapProperties();
        propiedades.setRecepcionUrl(
                "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline");

        assertThat(propiedades.apuntaAProduccion()).isTrue();
        // Apuntar a produccion es legitimo si es deliberado: se avisa, no se
        // impide.
        assertThatCode(propiedades::validar).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnTimeoutNuloOCero() {
        SriSoapProperties sinLectura = new SriSoapProperties();
        sinLectura.setReadTimeout(Duration.ZERO);
        assertThatThrownBy(sinLectura::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-timeout");

        SriSoapProperties sinConexion = new SriSoapProperties();
        sinConexion.setConnectTimeout(null);
        assertThatThrownBy(sinConexion::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect-timeout");
    }

    @Test
    void rechazaTextoPlanoSalvoContraLocalhost() {
        SriSoapProperties remotaEnClaro = new SriSoapProperties();
        remotaEnClaro.setRecepcionUrl("http://celcer.sri.gob.ec/ws/Recepcion");
        assertThatThrownBy(remotaEnClaro::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");

        // Los tests necesitan un servidor local; ese caso si se admite.
        SriSoapProperties local = new SriSoapProperties();
        local.setRecepcionUrl("http://localhost:8089/Recepcion");
        local.setAutorizacionUrl("http://127.0.0.1:8089/Autorizacion");
        assertThatCode(local::validar).doesNotThrowAnyException();
    }

    @Test
    void rechazaLaUrlDelWsdlEnLugarDelEndpoint() {
        SriSoapProperties propiedades = new SriSoapProperties();
        propiedades.setAutorizacionUrl("https://celcer.sri.gob.ec/comprobantes-electronicos-ws/"
                + "AutorizacionComprobantesOffline?wsdl");

        assertThatThrownBy(propiedades::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("?wsdl");
    }

    @Test
    void rechazaUnaUrlVaciaOMalFormada() {
        SriSoapProperties vacia = new SriSoapProperties();
        vacia.setRecepcionUrl("  ");
        assertThatThrownBy(vacia::validar).isInstanceOf(IllegalStateException.class);

        SriSoapProperties rota = new SriSoapProperties();
        rota.setRecepcionUrl("https://sri gob ec/ws");
        assertThatThrownBy(rota::validar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noExponeSecretosAlImprimirse() {
        // No hay ninguno que exponer -los servicios offline no usan
        // credenciales-, y este test lo deja fijado: si alguien anadiera una
        // clave a estas propiedades, tendria que decidir conscientemente que
        // hacer con toString.
        assertThat(new SriSoapProperties().toString())
                .contains("recepcion=")
                .contains("readTimeout=")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret");
    }
}
