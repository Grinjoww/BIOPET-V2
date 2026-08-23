package com.biopet.config;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Namespace versionado para las claves de cache DECLARATIVO (@Cacheable) de
 * BIOPET V2, centralizado aqui en vez de concatenarse en cada anotacion.
 * <p>
 * Sin este prefijo, una clave como "consultas::admin@biopet.ec-0-10-UNSORTED"
 * generada antes de la Correccion A podia sobrevivir en Redis (dentro del TTL
 * de 300s configurado en spring.cache.redis.time-to-live) cuando V2 arranca
 * sobre un Redis/Valkey caliente, y una lectura por esa misma clave exacta la
 * reutilizaria como si ya fuera una respuesta filtrada por rol/duenio.
 * <p>
 * Con "biopet:v2:" antepuesto, V2 solo lee/escribe "biopet:v2:consultas::...";
 * las claves antiguas sin ese prefijo quedan huerfanas y mueren solas por TTL,
 * sin necesidad de FLUSHDB ni de borrado manual.
 * <p>
 * Este bean reconstruye el mismo {@link RedisCacheConfiguration} por defecto
 * que Spring Boot arma a partir de spring.cache.redis.* (TTL, cache-null-
 * values) para no depender del orden de ejecucion frente al customizer
 * interno de Boot: si aqui simplemente se llamara a
 * {@code RedisCacheConfiguration.defaultCacheConfig()} sin releer esas
 * propiedades, se perderia el TTL de 300s configurado.
 */
@Configuration
public class CacheConfig {

    private static final String NAMESPACE_PREFIX = "biopet:v2:";

    @Bean
    public RedisCacheManagerBuilderCustomizer biopetV2CacheKeyNamespace(CacheProperties cacheProperties) {
        return builder -> {
            CacheProperties.Redis redisProperties = cacheProperties.getRedis();
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .computePrefixWith(cacheName -> NAMESPACE_PREFIX + cacheName + "::");

            if (redisProperties.getTimeToLive() != null) {
                config = config.entryTtl(redisProperties.getTimeToLive());
            }
            if (!redisProperties.isCacheNullValues()) {
                config = config.disableCachingNullValues();
            }
            if (!redisProperties.isUseKeyPrefix()) {
                config = config.disableKeyPrefix();
            }

            builder.cacheDefaults(config);
        };
    }
}
