package com.biopet.facturacion.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base comun de las pruebas de persistencia del modulo de facturacion.
 *
 * <p>A diferencia del resto de tests de integracion del proyecto (que declaran
 * {@code @Container} y por tanto arrancan y paran un PostgreSQL propio por
 * clase), aqui el contenedor es un SINGLETON estatico que se arranca una sola
 * vez para toda la JVM y no se para explicitamente: lo reclama Ryuk al terminar
 * la ejecucion. El motivo es de coste: esta fase anade tres clases de
 * integracion sobre PostgreSQL real, y levantar tres contenedores para
 * comprobar el mismo esquema seria tiempo de build regalado. Como ademas las
 * propiedades dinamicas son identicas en las tres, Spring reutiliza tambien el
 * mismo contexto de aplicacion.
 *
 * <p>Consecuencia a tener en cuenta al escribir tests aqui: la base de datos se
 * COMPARTE entre clases y no se limpia entre ellas. Por eso cada test crea sus
 * propios fixtures con valores unicos (RUC, codigos, emails) en lugar de
 * asumir una tabla vacia.
 *
 * <p>{@code ddl-auto} se deja en {@code validate}, como en produccion: si el DDL
 * de V7/V8 y las entidades JPA no coincidieran exactamente, el contexto ni
 * siquiera arrancaria y todas las clases que heredan de esta fallarian.
 */
public abstract class FacturacionPostgresTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_facturacion_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
