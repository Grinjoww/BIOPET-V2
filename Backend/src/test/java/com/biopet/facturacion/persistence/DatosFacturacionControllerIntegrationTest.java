package com.biopet.facturacion.persistence;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 8B: REST de {@code DatosFacturacion} -el hueco operativo que la Fase 8A
 * dejo abierto-. El foco es el OWNERSHIP real de DUENO (backend, no solo
 * {@code @PreAuthorize}) y el manejo del perfil predeterminado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatosFacturacionControllerIntegrationTest extends FacturaEscenarioTestBase {

    private static final String CLAVE = "ClaveDePrueba123*";

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void duenoCreaYLaPrimeraQuedaPredeterminadaSola() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        String token = login(dueno);

        MvcResult creado = mockMvc.perform(post("/api/usuarios/" + dueno.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoIdentificacion":"CEDULA","identificacion":"%s",
                                 "razonSocial":"PERSONA FICTICIA"}
                                """.formatted(String.format("%010d", siguiente()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predeterminado").value(true))
                .andReturn();

        Long id = extraerId(creado);

        mockMvc.perform(get("/api/usuarios/" + dueno.getId() + "/datos-facturacion/predeterminado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void duenoNoAccedeALosDatosDeFacturacionDeOtroUsuario() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        Usuario otro = crearUsuario(Rol.ROLE_DUENO);
        String token = login(dueno);

        mockMvc.perform(get("/api/usuarios/" + otro.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/usuarios/" + otro.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoIdentificacion":"CEDULA","identificacion":"0000000001",
                                 "razonSocial":"INTENTO AJENO"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void auxiliarGestionaDatosDeFacturacionDeCualquierDueno() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        Usuario auxiliar = crearUsuario(Rol.ROLE_AUXILIAR);
        String token = login(auxiliar);

        mockMvc.perform(post("/api/usuarios/" + dueno.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoIdentificacion":"CEDULA","identificacion":"%s",
                                 "razonSocial":"GESTIONADO POR AUXILIAR"}
                                """.formatted(String.format("%010d", siguiente()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/usuarios/" + dueno.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void veterinarioNoAccedeADatosDeFacturacion() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        Usuario veterinario = crearUsuario(Rol.ROLE_VETERINARIO);
        String token = login(veterinario);

        mockMvc.perform(get("/api/usuarios/" + dueno.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambiarPredeterminadoDejaExactamenteUnoActivo() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        String token = login(dueno);

        Long primero = extraerId(crearDatos(dueno, token));
        Long segundo = extraerId(crearDatos(dueno, token));

        mockMvc.perform(patch("/api/usuarios/" + dueno.getId() + "/datos-facturacion/" + segundo + "/predeterminado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predeterminado").value(true));

        long predeterminados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM datos_facturacion WHERE usuario_id = ? AND predeterminado = TRUE",
                Long.class, dueno.getId());
        org.assertj.core.api.Assertions.assertThat(predeterminados).isEqualTo(1L);

        mockMvc.perform(get("/api/usuarios/" + dueno.getId() + "/datos-facturacion/predeterminado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(segundo));

        org.assertj.core.api.Assertions.assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void desactivarEsBajaLogicaYQuitaElPredeterminado() throws Exception {
        Usuario dueno = crearUsuario(Rol.ROLE_DUENO);
        String token = login(dueno);
        Long id = extraerId(crearDatos(dueno, token));

        mockMvc.perform(delete("/api/usuarios/" + dueno.getId() + "/datos-facturacion/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/usuarios/" + dueno.getId() + "/datos-facturacion/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        String activo = jdbc.queryForObject(
                "SELECT activo::text FROM datos_facturacion WHERE id = ?", String.class, id);
        org.assertj.core.api.Assertions.assertThat(activo).isEqualTo("false");
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private MvcResult crearDatos(Usuario dueno, String token) throws Exception {
        return mockMvc.perform(post("/api/usuarios/" + dueno.getId() + "/datos-facturacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoIdentificacion":"CEDULA","identificacion":"%s",
                                 "razonSocial":"PERSONA FICTICIA %d"}
                                """.formatted(String.format("%010d", siguiente()), siguiente())))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private Usuario crearUsuario(Rol rol) {
        String email = ("dfac-" + rol + "-" + siguiente() + "@biopet.test").toLowerCase(java.util.Locale.ROOT);
        return usuarioRepository.save(Usuario.builder()
                .nombre("Fixture " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(CLAVE))
                .rol(rol)
                .activo(true)
                .build());
    }

    private String login(Usuario usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(usuario.getEmail(), CLAVE)))
                .andExpect(status().isOk())
                .andReturn();
        return resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(valor -> valor.startsWith("access_token="))
                .findFirst()
                .map(valor -> valor.substring("access_token=".length()).split(";", 2)[0])
                .orElseThrow(() -> new AssertionError("No se encontro la cookie access_token en el login."));
    }

    private Long extraerId(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString();
        return Long.valueOf(cuerpo.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }
}
