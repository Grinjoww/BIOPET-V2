package com.biopet.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-1: prueba unitaria aislada (MockEnvironment, sin contexto Spring) de
 * RequiredSecretsValidator para cada una de las 3 claves sensibles: ausente
 * (placeholder sin resolver) vs. valida -incluyendo el caso deliberado de
 * spring.datasource.password="" que usa el perfil "test" para H2, que NO
 * debe rechazarse.
 */
class RequiredSecretsValidatorTest {

    private static final String JWT = "security.jwt.secret";
    private static final String DB_APP = "spring.datasource.password";
    private static final String DB_FLYWAY = "spring.flyway.password";

    @Test
    void todasLasPropiedadesPresentesNoLanzaExcepcion() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c")
                .withProperty(DB_APP, "algun-password")
                .withProperty(DB_FLYWAY, "algun-password");

        assertThatCode(() -> new RequiredSecretsValidator(env)).doesNotThrowAnyException();
    }

    @Test
    void jwtSecretAusenteLanzaIllegalStateException() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(DB_APP, "algun-password")
                .withProperty(DB_FLYWAY, "algun-password");

        assertThatThrownBy(() -> new RequiredSecretsValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void dbAppPasswordEnBlancoNoLanzaExcepcion() {
        // spring.datasource.password="" es el valor real y deliberado del
        // perfil "test" (H2, usuario "sa" sin contrasena): no es un fallback
        // silencioso a un secreto conocido, asi que no debe rechazarse.
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c")
                .withProperty(DB_APP, "")
                .withProperty(DB_FLYWAY, "");

        assertThatCode(() -> new RequiredSecretsValidator(env)).doesNotThrowAnyException();
    }

    @Test
    void dbAppPasswordAusenteLanzaIllegalStateException() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c")
                .withProperty(DB_FLYWAY, "algun-password");

        assertThatThrownBy(() -> new RequiredSecretsValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_APP_PASSWORD");
    }

    @Test
    void dbPasswordDeFlywayAusenteLanzaIllegalStateException() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c")
                .withProperty(DB_APP, "algun-password");

        assertThatThrownBy(() -> new RequiredSecretsValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void conFlywayDeshabilitadoNoValidaDbPasswordDeFlyway() {
        // Mismo escenario que application-test.yml: flyway.enabled=false y
        // spring.flyway.password sin definir (queda como "${DB_PASSWORD}" sin
        // resolver via la capa base de application.yml) -no debe fallar,
        // porque flyway nunca llega a usar esa contrasena.
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c")
                .withProperty(DB_APP, "")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty(DB_FLYWAY, "${DB_PASSWORD}");

        assertThatCode(() -> new RequiredSecretsValidator(env)).doesNotThrowAnyException();
    }

    @Test
    void placeholderSinResolverEnElEnvironmentTambienLanzaIllegalStateException() {
        // Simula lo que produce application.yml real ("${JWT_SECRET}" sin
        // resolver via @Value) si alguien intentara leerlo por getProperty.
        MockEnvironment env = new MockEnvironment()
                .withProperty(JWT, "${JWT_SECRET}")
                .withProperty(DB_APP, "algun-password")
                .withProperty(DB_FLYWAY, "algun-password");

        assertThatThrownBy(() -> new RequiredSecretsValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
