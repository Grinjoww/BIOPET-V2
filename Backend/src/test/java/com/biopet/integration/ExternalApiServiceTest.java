package com.biopet.integration;

import com.biopet.dto.ExternalApiResponse;
import com.biopet.exception.ExternalApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura de ramas de ExternalApiService: hit/miss de caché, resultado vacío,
 * presencia/ausencia de taxonomía y características, y manejo de fallos al
 * escribir/leer Redis sin romper la respuesta al usuario.
 */
class ExternalApiServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private ExternalApiClient externalApiClient;
    private ExternalApiService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        externalApiClient = mock(ExternalApiClient.class);
        service = new ExternalApiService(externalApiClient, redisTemplate, objectMapper);
    }

    @Test
    void cacheHitDevuelveDatosDesdeRedisSinLlamarApiExterna() throws Exception {
        ExternalApiResponse original = new ExternalApiResponse(
                "perro", "Canis lupus familiaris", "domestico", "omnivoro", "api-ninjas", Instant.now());
        String json = objectMapper.writeValueAsString(original);
        when(valueOperations.get("external-api:animal:perro")).thenReturn(json);

        ExternalApiResponse resultado = service.obtenerInfoEspecie("perro");

        assertEquals("perro", resultado.especieConsultada());
        assertEquals("cache", resultado.origen());
        assertEquals("Canis lupus familiaris", resultado.nombreCientifico());
        verify(externalApiClient, never()).buscarPorEspecie(anyString());
    }

    @Test
    void cacheConJsonCorruptoLanzaExcepcion() {
        when(valueOperations.get("external-api:animal:gato")).thenReturn("{no-es-json-valido");

        assertThrows(ExternalApiException.class, () -> service.obtenerInfoEspecie("gato"));
    }

    @Test
    void cacheMissConResultadosVaciosLanzaExcepcion() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(externalApiClient.buscarPorEspecie("ave")).thenReturn(List.of());

        assertThrows(ExternalApiException.class, () -> service.obtenerInfoEspecie("Ave"));
    }

    @Test
    void cacheMissConTaxonomiaYCaracteristicasCompletasGuardaEnCache() {
        when(valueOperations.get(anyString())).thenReturn(null);
        ExternalApiClient.AnimalApiNinjasDto.Taxonomy taxonomy =
                new ExternalApiClient.AnimalApiNinjasDto.Taxonomy("Animalia", "Chordata", "Felis catus", "Carnivora");
        ExternalApiClient.AnimalApiNinjasDto.Characteristics characteristics =
                new ExternalApiClient.AnimalApiNinjasDto.Characteristics("carnivoro", "domestico", "12-18 años", "slogan");
        ExternalApiClient.AnimalApiNinjasDto dto =
                new ExternalApiClient.AnimalApiNinjasDto("Cat", taxonomy, characteristics, List.of("mundial"));
        when(externalApiClient.buscarPorEspecie("gato")).thenReturn(List.of(dto));

        ExternalApiResponse resultado = service.obtenerInfoEspecie("gato");

        assertEquals("Felis catus", resultado.nombreCientifico());
        assertEquals("domestico", resultado.habitat());
        assertEquals("carnivoro", resultado.dieta());
        assertEquals("api-ninjas", resultado.origen());
        verify(valueOperations, times(1)).set(eq("external-api:animal:gato"), anyString(), any(Duration.class));
    }

    @Test
    void cacheMissSinTaxonomiaNiCaracteristicasDevuelveCamposNulos() {
        when(valueOperations.get(anyString())).thenReturn(null);
        ExternalApiClient.AnimalApiNinjasDto dto =
                new ExternalApiClient.AnimalApiNinjasDto("Desconocido", null, null, List.of());
        when(externalApiClient.buscarPorEspecie("desconocido")).thenReturn(List.of(dto));

        ExternalApiResponse resultado = service.obtenerInfoEspecie("desconocido");

        assertNull(resultado.nombreCientifico());
        assertNull(resultado.habitat());
        assertNull(resultado.dieta());
    }

    @Test
    void fallaAlEscribirEnRedisNoRompeLaRespuesta() {
        when(valueOperations.get(anyString())).thenReturn(null);
        ExternalApiClient.AnimalApiNinjasDto dto =
                new ExternalApiClient.AnimalApiNinjasDto("Perro", null, null, List.of());
        when(externalApiClient.buscarPorEspecie("perro")).thenReturn(List.of(dto));
        doThrow(new RuntimeException("Redis caído")).when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        ExternalApiResponse resultado = service.obtenerInfoEspecie("perro");

        assertEquals("api-ninjas", resultado.origen());
    }
}
