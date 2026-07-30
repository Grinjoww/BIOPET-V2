package com.biopet.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("tls")
public class TomcatDualConnectorConfig {

    static final int PUERTO_MINIMO = 1;
    static final int PUERTO_MAXIMO = 65535;

    private final int puertoHttp;

    public TomcatDualConnectorConfig(@Value("${tls.http-port:8080}") int puertoHttp) {
        validarPuerto(puertoHttp);
        this.puertoHttp = puertoHttp;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> conectorHttpAdicional() {
        return factory -> factory.addAdditionalTomcatConnectors(crearConectorHttp(puertoHttp));
    }

    static Connector crearConectorHttp(int puerto) {
        validarPuerto(puerto);
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(puerto);
        return connector;
    }

    private static void validarPuerto(int puerto) {
        if (puerto < PUERTO_MINIMO || puerto > PUERTO_MAXIMO) {
            throw new IllegalArgumentException(
                    "Puerto HTTP invalido para el conector adicional de BIOPET (1-65535): " + puerto);
        }
    }
}
