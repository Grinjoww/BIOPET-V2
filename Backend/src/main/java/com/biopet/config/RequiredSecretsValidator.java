package com.biopet.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * P0-1: fuerza un fail-fast real al arrancar cuando falta un secreto
 * requerido (JWT_SECRET, DB_APP_PASSWORD, DB_PASSWORD).
 * <p>
 * Por que existe este bean en vez de confiar solo en
 * {@code ${JWT_SECRET}} sin default en application.yml: {@code @Value}
 * resuelve sus placeholders con {@link Environment#resolvePlaceholders},
 * la variante NO estricta (ignoreUnresolvablePlaceholders=true). Si
 * JWT_SECRET no esta definida, un campo {@code @Value("${security.jwt.secret}")}
 * NO lanza excepcion: termina con el literal sin resolver
 * {@code "${JWT_SECRET}"} (13 caracteres) como valor, en vez de fallar. En
 * JwtService eso hoy "funciona por accidente" porque 13 bytes {@literal <}
 * 32 hace que su propia validacion de longitud lo rechace -pero para
 * spring.datasource.password / spring.flyway.password no existe ninguna
 * validacion equivalente: Hikari intentaria conectar con la contrasena
 * literal "${DB_APP_PASSWORD}", fallando mas tarde con un error de
 * autenticacion confuso en vez de un error de arranque claro.
 * <p>
 * {@link Environment#getProperty(String)} si usa la variante estricta
 * (resolveRequiredPlaceholders) para placeholders anidados sin resolver, por
 * lo que se usa aqui explicitamente para las 3 claves sensibles. Al ser un
 * bean singleton mas (sin @Lazy), Spring lo instancia durante el mismo
 * preInstantiateSingletons() que JwtService/DataSource; si cualquiera de los
 * tres falta, el contexto no termina de arrancar.
 */
@Component
public class RequiredSecretsValidator {

    public RequiredSecretsValidator(Environment environment) {
        requireResolved(environment, "security.jwt.secret", "JWT_SECRET");
        requireResolved(environment, "spring.datasource.password", "DB_APP_PASSWORD");
        // Solo si flyway esta habilitado: el perfil "test" (H2, flyway.enabled=
        // false) no sobreescribe spring.flyway.password -sigue siendo, via la
        // capa base de application.yml, "${DB_PASSWORD}" sin resolver-, pero
        // como flyway nunca corre en ese perfil, ese valor jamas se usa y no
        // hay nada que fallar-rapido aqui.
        if (environment.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            requireResolved(environment, "spring.flyway.password", "DB_PASSWORD");
        }
    }

    private void requireResolved(Environment environment, String propertyKey, String envVarHint) {
        String value;
        try {
            value = environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            // Placeholder anidado sin resolver (p.ej. "${JWT_SECRET}" literal).
            throw new IllegalStateException(mensaje(propertyKey, envVarHint), ex);
        }
        if (value == null) {
            throw new IllegalStateException(mensaje(propertyKey, envVarHint));
        }
        // Nota: un valor en blanco ("") NO se rechaza aqui a proposito: el
        // perfil "test" (application-test.yml) usa deliberadamente
        // spring.datasource.password="" para H2 (usuario "sa" sin
        // contrasena), un valor real y documentado, no un fallback silencioso
        // a un secreto conocido. Lo que este validador impide es que el
        // PLACEHOLDER quede sin resolver (arriba), no que el valor este vacio.
    }

    private String mensaje(String propertyKey, String envVarHint) {
        return "Falta la variable de entorno requerida " + envVarHint
                + " (propiedad '" + propertyKey + "'). No existe un valor por defecto: "
                + "definila antes de arrancar (ver .env.example).";
    }
}
