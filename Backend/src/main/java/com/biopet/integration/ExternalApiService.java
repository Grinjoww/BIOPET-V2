package com.biopet.integration;

import com.biopet.dto.ExternalApiResponse;
import com.biopet.exception.ExternalApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ExternalApiService {

    private static final String CACHE_PREFIX = "external-api:animal:";

    private final ExternalApiClient externalApiClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.external-api.cache-ttl-seconds:600}") // 10 min por defecto, igual al criterio de datos "en vivo" de la guía
    private long ttlSegundos;

    public ExternalApiService(ExternalApiClient externalApiClient,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.externalApiClient = externalApiClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ExternalApiResponse obtenerInfoEspecie(String especie) {
        String claveNormalizada = especie.trim().toLowerCase();
        String cacheKey = CACHE_PREFIX + claveNormalizada;

        String cacheado = redisTemplate.opsForValue().get(cacheKey);
        if (cacheado != null) {
            return deserializar(cacheado, especie, "cache");
        }

        List<ExternalApiClient.AnimalApiNinjasDto> resultados = externalApiClient.buscarPorEspecie(claveNormalizada);
        if (resultados.isEmpty()) {
            throw new ExternalApiException("No se encontró información para la especie: " + especie);
        }

        ExternalApiClient.AnimalApiNinjasDto animal = resultados.get(0);
        ExternalApiResponse response = new ExternalApiResponse(
                especie,
                animal.taxonomy() != null ? animal.taxonomy().scientific_name() : null,
                animal.characteristics() != null ? animal.characteristics().habitat() : null,
                animal.characteristics() != null ? animal.characteristics().diet() : null,
                "api-ninjas",
                Instant.now()
        );

        guardarEnCache(cacheKey, response);
        return response;
    }

    private void guardarEnCache(String cacheKey, ExternalApiResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttlSegundos));
        } catch (Exception ex) {
            // Si Redis falla al escribir, no rompemos la respuesta al usuario; solo no se cachea.
        }
    }

    private ExternalApiResponse deserializar(String json, String especieOriginal, String origen) {
        try {
            ExternalApiResponse cacheado = objectMapper.readValue(json, ExternalApiResponse.class);
            return new ExternalApiResponse(
                    especieOriginal,
                    cacheado.nombreCientifico(),
                    cacheado.habitat(),
                    cacheado.dieta(),
                    origen,
                    cacheado.consultadoEn()
            );
        } catch (Exception ex) {
            throw new ExternalApiException("Error leyendo caché de la API externa.", ex);
        }
    }
}