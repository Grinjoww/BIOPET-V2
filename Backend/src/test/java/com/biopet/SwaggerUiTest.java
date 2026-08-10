package com.biopet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que Swagger UI quedó expuesto en /api/docs (Bloque 3A) y que el
 * documento OpenAPI JSON, reubicado a /api/openapi, sigue siendo público y
 * describe realmente la API de BIOPET. Ambas rutas deben ser accesibles sin
 * autenticación (ver SecurityConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerUiTest {
    @Autowired MockMvc mockMvc;

    @Test
    void swaggerUiEnApiDocsEsAccesibleSinAutenticacionYSirveLaInterfazReal() throws Exception {
        MvcResult redireccion = mockMvc.perform(get("/api/docs"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = redireccion.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(location, "GET /api/docs debe redirigir a la interfaz real de Swagger UI");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(mvcResult -> {
                    String body = mvcResult.getResponse().getContentAsString();
                    assertTrue(body.contains("swagger-ui"),
                            "La página servida tras la redirección de /api/docs debe ser la interfaz real de Swagger UI");
                });
    }

    @Test
    void documentoOpenApiEsAccesibleSinAutenticacionYDescribeLaApi() throws Exception {
        mockMvc.perform(get("/api/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("BIOPET API"))
                .andExpect(jsonPath("$.paths").exists());
    }
}
