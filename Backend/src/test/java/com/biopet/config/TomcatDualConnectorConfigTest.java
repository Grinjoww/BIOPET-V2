package com.biopet.config;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TomcatDualConnectorConfigTest {

    @Test
    void creaConectorHttpEnPuertoConfigurado() {
        Connector connector = TomcatDualConnectorConfig.crearConectorHttp(8080);

        assertEquals(8080, connector.getPort());
    }

    @Test
    void conectorEsHttpYNoSeguro() {
        Connector connector = TomcatDualConnectorConfig.crearConectorHttp(8080);

        assertEquals("http", connector.getScheme());
        assertFalse(connector.getSecure());
    }

    @Test
    void rechazaPuertoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> TomcatDualConnectorConfig.crearConectorHttp(0));
        assertThrows(IllegalArgumentException.class, () -> TomcatDualConnectorConfig.crearConectorHttp(70000));
        assertThrows(IllegalArgumentException.class, () -> TomcatDualConnectorConfig.crearConectorHttp(-1));
        assertThrows(IllegalArgumentException.class, () -> new TomcatDualConnectorConfig(0));
    }

    @Test
    void elCustomizerAgregaExactamenteUnConectorAdicional() {
        TomcatDualConnectorConfig config = new TomcatDualConnectorConfig(8080);
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer = config.conectorHttpAdicional();

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        customizer.customize(factory);

        assertEquals(1, factory.getAdditionalTomcatConnectors().size());

        Connector conectorAgregado = factory.getAdditionalTomcatConnectors().get(0);
        assertEquals(8080, conectorAgregado.getPort());
        assertEquals("http", conectorAgregado.getScheme());
        assertFalse(conectorAgregado.getSecure());
    }
}
