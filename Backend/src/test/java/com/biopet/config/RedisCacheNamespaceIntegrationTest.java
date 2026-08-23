package com.biopet.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-2: namespace versionado de las claves de cache DECLARATIVO (@Cacheable)
 * de BIOPET V2, contra un Redis real (Testcontainers) en vez de mocks -las
 * claves reales que escribe RedisCacheManager solo pueden observarse asi.
 * <p>
 * Reutiliza el mismo patron de PostgreSQLContainer + @DynamicPropertySource
 * ya establecido en ProcedimientosBiopetIntegrationTest (sin
 * @ActiveProfiles("test"): ese perfil fuerza spring.cache.type=simple, que
 * no pasa por Redis en absoluto y no serviria para este caso).
 */
@Testcontainers
@SpringBootTest
class RedisCacheNamespaceIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void limpiarRedisDeTestcontainers() {
        // El Redis de este @Container es efimero y exclusivo de esta clase de
        // test (no el Redis real de desarrollo/produccion): limpiarlo entre
        // metodos de test es higiene de aislamiento, no la operacion FLUSHDB
        // que la tarea prohibe sobre datos reales.
        stringRedisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    void unaEscrituraEnLaCacheConsultasQuedaBajoElNamespaceBiopetV2() {
        Cache consultas = cacheManager.getCache("consultas");
        assertThat(consultas).isNotNull();

        consultas.put("admin@biopet.ec-0-10-UNSORTED", "valor-de-prueba");

        Set<String> clavesV2 = stringRedisTemplate.keys("biopet:v2:consultas::*");
        assertThat(clavesV2).contains("biopet:v2:consultas::admin@biopet.ec-0-10-UNSORTED");

        Set<String> clavesSinNamespace = stringRedisTemplate.keys("consultas::*");
        assertThat(clavesSinNamespace).isEmpty();
    }

    @Test
    void unaEscrituraEnLaCacheMascotasQuedaBajoElNamespaceBiopetV2() {
        Cache mascotas = cacheManager.getCache("mascotas");
        assertThat(mascotas).isNotNull();

        mascotas.put("duenio@biopet.ec-0-10-UNSORTED", "valor-de-prueba");

        Set<String> clavesV2 = stringRedisTemplate.keys("biopet:v2:mascotas::*");
        assertThat(clavesV2).contains("biopet:v2:mascotas::duenio@biopet.ec-0-10-UNSORTED");
    }

    @Test
    void unaEntradaLegadoSinNamespaceNoEsResueltaComoHitPorLaCacheV2() {
        // Simula una clave "consultas::<email>-<page>-<size>-<sort>" escrita
        // por una version pre-V2 (sin este namespace), que sigue viva dentro
        // del TTL cuando V2 arranca sobre el mismo Redis/Valkey.
        String claveLegado = "consultas::admin@biopet.ec-0-10-UNSORTED";
        stringRedisTemplate.opsForValue().set(claveLegado, "payload-legado-sin-filtrar-por-rol");

        Cache consultas = cacheManager.getCache("consultas");
        assertThat(consultas).isNotNull();

        Cache.ValueWrapper hit = consultas.get("admin@biopet.ec-0-10-UNSORTED");

        assertThat(hit).isNull();
        // La clave legado sigue en Redis (no se borro nada), simplemente V2 no la lee.
        assertThat(stringRedisTemplate.hasKey(claveLegado)).isTrue();
    }

    @Test
    void elTtlDe300sSigueAplicandoseConElNamespaceNuevo() {
        Cache consultas = cacheManager.getCache("consultas");
        assertThat(consultas).isNotNull();
        consultas.put("admin@biopet.ec-0-10-UNSORTED", "valor-de-prueba");

        Long ttlSegundos = stringRedisTemplate.getExpire("biopet:v2:consultas::admin@biopet.ec-0-10-UNSORTED");

        assertThat(ttlSegundos).isNotNull();
        // spring.cache.redis.time-to-live (CACHE_TTL_MS) = 300000ms = 300s por
        // defecto; se tolera el pequeno margen de reloj entre el put() y esta
        // lectura.
        assertThat(ttlSegundos).isGreaterThan(0).isLessThanOrEqualTo(300);
    }

    @Test
    void evictAllEntriesSoloBorraClavesDelNamespaceV2NoLasLegado() {
        String claveLegado = "consultas::otro@biopet.ec-0-10-UNSORTED";
        stringRedisTemplate.opsForValue().set(claveLegado, "payload-legado");

        Cache consultas = cacheManager.getCache("consultas");
        assertThat(consultas).isNotNull();
        consultas.put("admin@biopet.ec-0-10-UNSORTED", "valor-de-prueba");

        consultas.clear(); // equivalente a @CacheEvict(value = "consultas", allEntries = true)

        assertThat(stringRedisTemplate.keys("biopet:v2:consultas::*")).isEmpty();
        assertThat(stringRedisTemplate.hasKey(claveLegado)).isTrue();
    }
}
