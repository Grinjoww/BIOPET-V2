package com.biopet.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-1: prueba de extremo a extremo (sin Postgres/Redis, sin
 * @SpringBootTest completo) de que el application.yml REAL del classpath de
 * main, combinado con {@link RequiredSecretsValidator}, efectivamente
 * impide arrancar cuando falta JWT_SECRET -y arranca cuando esta presente y
 * es valida.
 * <p>
 * Se usa {@link SpringApplicationBuilder#environment} con la fuente
 * "systemEnvironment" reemplazada por un mapa vacio para que el resultado no
 * dependa de que JWT_SECRET este definida como variable de entorno real del
 * proceso de "mvn test" (maven-surefire-plugin la define en pom.xml para que
 * otros @SpringBootTest de integracion -los que no usan
 * @ActiveProfiles("test")- sigan arrancando).
 */
class SensitiveDefaultsConfigTest {

    private static final String SECRETO_VALIDO =
            "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c";

    @Test
    void sinJwtSecretElContextoRealNoArranca() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = builderSinVariablesSensibles().run()) {
                // No deberia llegar aqui: RequiredSecretsValidator debe abortar el arranque.
            }
        }).hasStackTraceContaining("JWT_SECRET");
    }

    @Test
    void conJwtSecretValidoElContextoRealArranca() {
        try (ConfigurableApplicationContext ctx = builderSinVariablesSensibles()
                .properties(
                        "JWT_SECRET=" + SECRETO_VALIDO,
                        "DB_APP_PASSWORD=password-de-prueba",
                        "DB_PASSWORD=password-de-prueba")
                .run()) {
            assertThat(ctx.isActive()).isTrue();
            assertThat(ctx.getBean(RequiredSecretsValidator.class)).isNotNull();
        }
    }

    /**
     * Solo carga application.yml (classpath de main) + RequiredSecretsValidator,
     * sin @SpringBootApplication (evita autoconfigurar DataSource/Redis, que
     * exigirian Postgres/Redis reales para este test).
     */
    private static SpringApplicationBuilder builderSinVariablesSensibles() {
        return new SpringApplicationBuilder(RequiredSecretsValidator.class)
                .web(WebApplicationType.NONE)
                .environment(environmentSinVariablesSensibles());
    }

    private static StandardEnvironment environmentSinVariablesSensibles() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of()));
        return environment;
    }
}
