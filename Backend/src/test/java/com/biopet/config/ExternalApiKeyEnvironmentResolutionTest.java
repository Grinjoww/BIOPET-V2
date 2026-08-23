package com.biopet.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3: verifica -sin asumirlo- que APP_EXTERNAL_API_KEY ya se resuelve como
 * app.external-api.key aunque esa clave no estuviera declarada en
 * application.yml (ahora si esta, solo por documentacion; ver CacheConfig
 * y application.yml para el porque no cambia el comportamiento).
 * <p>
 * El mecanismo es de Spring Framework (no de Spring Boot ni de
 * @ConfigurationProperties): {@link SystemEnvironmentPropertySource} —la
 * misma clase que respalda la fuente "systemEnvironment" de cualquier
 * {@link StandardEnvironment}, incluido el de una app Spring Boot real—
 * traduce un nombre de propiedad con puntos/guiones ("app.external-api.key")
 * a su variante de variable de entorno en mayusculas con guion bajo
 * ("APP_EXTERNAL_API_KEY") al resolver, sin que la propiedad necesite existir
 * en ningun application.yml. Por eso "APP_EXTERNAL_API_KEY" ya funcionaba
 * para {@code @Value("${app.external-api.key:}")} en ExternalApiClient antes
 * de este cambio.
 */
class ExternalApiKeyEnvironmentResolutionTest {

    @Test
    void appExternalApiKeySeResuelveDesdeLaVariableDeEntornoSinEstarDeclaradaEnYml() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("APP_EXTERNAL_API_KEY", "clave-de-prueba")));

        assertThat(environment.getProperty("app.external-api.key")).isEqualTo("clave-de-prueba");
    }

    @Test
    void sinLaVariableDeEntornoLaPropiedadNoSeResuelve() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of()));

        assertThat(environment.getProperty("app.external-api.key")).isNull();
    }
}
