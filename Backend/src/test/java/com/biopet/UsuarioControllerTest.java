package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el CRUD administrativo de usuarios (POST/GET/PUT/DELETE /api/usuarios),
 * restringido a ROLE_ADMIN, sin afectar /api/usuarios/me ni /api/auth/registro
 * (autoregistro público, verificado por separado en AuthControllerTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;

    private static final String EMAIL_ADMIN = "jaime@biopet.com";
    private static final String PASSWORD_ADMIN = "ClaveCorrecta123*";

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        Usuario admin = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email(EMAIL_ADMIN)
                .passwordHash(passwordEncoder.encode(PASSWORD_ADMIN))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void listarUsuariosComoAdmin() throws Exception {
        Long duenoId = registrarUsuarioYObtenerId("listado.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(duenoId.intValue())));
    }

    @Test
    void buscarUsuarioPorId() throws Exception {
        Long duenoId = registrarUsuarioYObtenerId("buscar.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/" + duenoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(duenoId))
                .andExpect(jsonPath("$.email").value("buscar.dueno@biopet.com"))
                .andExpect(jsonPath("$.rol").value("ROLE_DUENO"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void buscarUsuarioInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/usuarios/999999"));
    }

    @Test
    void crearUsuarioDevuelve201() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Auxiliar Nuevo","email":"nuevo.auxiliar@biopet.com","password":"ClaveAux123*","rol":"ROLE_AUXILIAR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuevo.auxiliar@biopet.com"))
                .andExpect(jsonPath("$.rol").value("ROLE_AUXILIAR"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertFalse(usuarioRepository.findByEmail("nuevo.auxiliar@biopet.com").isEmpty());
    }

    @Test
    void crearUsuarioConEmailDuplicadoDevuelve409() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otro Admin","email":"%s","password":"OtraClave123*","rol":"ROLE_ADMIN"}
                                """.formatted(EMAIL_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:conflict"))
                .andExpect(jsonPath("$.title").value("Conflicto de datos"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/api/usuarios"));
    }

    @Test
    void crearUsuarioConCamposInvalidosDevuelve422() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","email":"no-es-un-email","password":"corta","rol":"ROLE_AUXILIAR"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/api/usuarios"))
                .andExpect(jsonPath("$.errors.nombre").isArray())
                .andExpect(jsonPath("$.errors.email").isArray())
                .andExpect(jsonPath("$.errors.password").isArray());
    }

    @Test
    void crearUsuarioSinPasswordDevuelve400() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Sin Password","email":"sinpassword@biopet.com","rol":"ROLE_AUXILIAR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:bad-request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/usuarios"));
    }

    @Test
    void actualizarUsuarioDevuelve200() throws Exception {
        Long duenoId = registrarUsuarioYObtenerId("actualizar.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(put("/api/usuarios/" + duenoId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Dueño Renombrado","email":"actualizar.dueno@biopet.com","rol":"ROLE_DUENO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Dueño Renombrado"))
                .andExpect(jsonPath("$.id").value(duenoId));
    }

    @Test
    void actualizarUsuarioInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(put("/api/usuarios/999999")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"No Existe","email":"noexiste@biopet.com","rol":"ROLE_DUENO"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"));
    }

    @Test
    void adminNoPuedeEscalarSuPropioRolDevuelve403() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");
        Long adminId = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow().getId();

        mockMvc.perform(put("/api/usuarios/" + adminId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Jaime Mariscal","email":"%s","rol":"ROLE_DUENO"}
                                """.formatted(EMAIL_ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));

        Usuario adminSinCambios = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Rol.ROLE_ADMIN, adminSinCambios.getRol());
    }

    @Test
    void eliminarUsuarioDevuelve204YBajaLogica() throws Exception {
        Long duenoId = registrarUsuarioYObtenerId("eliminar.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(delete("/api/usuarios/" + duenoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Usuario usuarioEliminado = usuarioRepository.findById(duenoId)
                .orElseThrow(() -> new AssertionError("El usuario fue eliminado físicamente de la base de datos: " + duenoId));
        assertFalse(usuarioEliminado.isActivo());

        mockMvc.perform(get("/api/usuarios/" + duenoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarUsuarioInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(delete("/api/usuarios/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"));
    }

    @Test
    void adminNoPuedeDarseDeBajaASiMismo() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");
        Long adminId = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow().getId();

        mockMvc.perform(delete("/api/usuarios/" + adminId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));

        Usuario adminSinCambios = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(adminSinCambios.isActivo());

        // Sigue pudiendo autenticarse: el rechazo no lo dejó fuera del sistema.
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void adminPuedeDarDeBajaAOtroAdmin() throws Exception {
        Long otroAdminId = registrarUsuarioYObtenerId("otro.admin@biopet.com", "ClaveAdmin123*", Rol.ROLE_ADMIN);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(delete("/api/usuarios/" + otroAdminId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Usuario otroAdminDadoDeBaja = usuarioRepository.findById(otroAdminId).orElseThrow();
        assertFalse(otroAdminDadoDeBaja.isActivo());

        Usuario adminQueEjecutoLaAccion = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(adminQueEjecutoLaAccion.isActivo());
    }

    @Test
    void accesoSinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/usuarios"));
    }

    @Test
    void accesoConRolNoAdminDevuelve403() throws Exception {
        registrarUsuarioYObtenerId("rolno.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenDueno = extractCookieValue(iniciarSesion("rolno.dueno@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"))
                .andExpect(jsonPath("$.instance").value("/api/usuarios"));
    }

    @Test
    void veterinarioNoPuedeCrearUsuariosDevuelve403() throws Exception {
        registrarUsuarioYObtenerId("rolno.veterinario@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        String tokenVeterinario = extractCookieValue(iniciarSesion("rolno.veterinario@biopet.com", "ClaveVet123*"), "access_token");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenVeterinario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Intento","email":"intento@biopet.com","password":"ClaveIntento123*","rol":"ROLE_AUXILIAR"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void meSigueFuncionando() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_ADMIN))
                .andExpect(jsonPath("$.rol").value("ROLE_ADMIN"));
    }

    // ---------- /api/usuarios/duenios y /api/usuarios/veterinarios (Corrección B) ----------

    @Test
    void auxiliarObtieneDueniosSeleccionables() throws Exception {
        registrarUsuarioYObtenerId("aux.duenios@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        Long duenoId = registrarUsuarioYObtenerId("sel.dueno1@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAuxiliar = extractCookieValue(iniciarSesion("aux.duenios@biopet.com", "ClaveAux123*"), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(duenoId))
                .andExpect(jsonPath("$[0].email").value("sel.dueno1@biopet.com"))
                .andExpect(jsonPath("$[0].rol").value("ROLE_DUENO"));
    }

    @Test
    void auxiliarObtieneVeterinariosSeleccionables() throws Exception {
        registrarUsuarioYObtenerId("aux.vets@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        Long vetId = registrarUsuarioYObtenerId("sel.vet1@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        String tokenAuxiliar = extractCookieValue(iniciarSesion("aux.vets@biopet.com", "ClaveAux123*"), "access_token");

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(vetId))
                .andExpect(jsonPath("$[0].email").value("sel.vet1@biopet.com"))
                .andExpect(jsonPath("$[0].rol").value("ROLE_VETERINARIO"));
    }

    @Test
    void veterinarioAccedeAAmbosListadosSeleccionables() throws Exception {
        registrarUsuarioYObtenerId("vet.selector@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        registrarUsuarioYObtenerId("sel.dueno2@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        registrarUsuarioYObtenerId("sel.vet2@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        String tokenVeterinario = extractCookieValue(iniciarSesion("vet.selector@biopet.com", "ClaveVet123*"), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk());
    }

    @Test
    void adminAccedeAAmbosListadosSeleccionables() throws Exception {
        registrarUsuarioYObtenerId("sel.dueno3@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        registrarUsuarioYObtenerId("sel.vet3@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void duenoNoPuedeAccederAListadosSeleccionablesDevuelve403() throws Exception {
        registrarUsuarioYObtenerId("sel.dueno.bloqueado@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenDueno = extractCookieValue(iniciarSesion("sel.dueno.bloqueado@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void usuarioNoAutenticadoRecibe401EnSeleccionables() throws Exception {
        mockMvc.perform(get("/api/usuarios/duenios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"));

        mockMvc.perform(get("/api/usuarios/veterinarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"));
    }

    @Test
    void seleccionablesExcluyenUsuariosInactivos() throws Exception {
        Long duenoInactivoId = registrarUsuarioYObtenerId("sel.dueno.inactivo@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        Usuario duenoInactivo = usuarioRepository.findById(duenoInactivoId).orElseThrow();
        duenoInactivo.setActivo(false);
        usuarioRepository.save(duenoInactivo);

        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dueniosSoloContieneRolDueno() throws Exception {
        registrarUsuarioYObtenerId("filtro.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        registrarUsuarioYObtenerId("filtro.vet@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        registrarUsuarioYObtenerId("filtro.aux@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rol").value("ROLE_DUENO"));
    }

    @Test
    void veterinariosSoloContieneRolVeterinario() throws Exception {
        registrarUsuarioYObtenerId("filtro2.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        registrarUsuarioYObtenerId("filtro2.vet@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        registrarUsuarioYObtenerId("filtro2.aux@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rol").value("ROLE_VETERINARIO"));
    }

    @Test
    void seleccionablesNuncaExponenAdminNiAuxiliar() throws Exception {
        Long adminId = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow().getId();
        Long auxiliarId = registrarUsuarioYObtenerId("no.enumerable.aux@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        registrarUsuarioYObtenerId("no.enumerable.dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        registrarUsuarioYObtenerId("no.enumerable.vet@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(adminId.intValue()))))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(auxiliarId.intValue()))));

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(adminId.intValue()))))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(auxiliarId.intValue()))));
    }

    @Test
    void jsonDeSeleccionablesNoExponePasswordNiActivo() throws Exception {
        registrarUsuarioYObtenerId("sel.sinpassword@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].activo").doesNotExist());
    }

    @Test
    void dueniosSeleccionablesOrdenadosPorNombreAscendente() throws Exception {
        crearUsuarioConNombreYRol("Zoe Andrade", "orden.zoe@biopet.com", Rol.ROLE_DUENO);
        crearUsuarioConNombreYRol("Ana Torres", "orden.ana@biopet.com", Rol.ROLE_DUENO);
        crearUsuarioConNombreYRol("Miguel Ruiz", "orden.miguel@biopet.com", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Ana Torres"))
                .andExpect(jsonPath("$[1].nombre").value("Miguel Ruiz"))
                .andExpect(jsonPath("$[2].nombre").value("Zoe Andrade"));
    }

    @Test
    void rutaDueniosNoColisionaConBusquedaPorId() throws Exception {
        registrarUsuarioYObtenerId("sel.dueno.ruta@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        // "duenios" y "veterinarios" no son dígitos, así que nunca deben
        // resolverse contra GET /api/usuarios/{id:\d+}; deben devolver un
        // array, no un único UsuarioResponse ni un 404 de "id no encontrado".
        mockMvc.perform(get("/api/usuarios/duenios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private Long crearUsuarioConNombreYRol(String nombre, String email, Rol rol) {
        Usuario usuario = Usuario.builder()
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode("ClavePrueba123*"))
                .rol(rol)
                .activo(true)
                .build();
        return usuarioRepository.save(usuario).getId();
    }

    private Long registrarUsuarioYObtenerId(String email, String password, Rol rol) {
        Usuario usuario = Usuario.builder()
                .nombre("Usuario Prueba")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();
        return usuarioRepository.save(usuario).getId();
    }

    private MvcResult iniciarSesion(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookieHeaders.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la cookie '" + cookieName + "' en las cabeceras Set-Cookie"));
        int separatorIndex = header.indexOf(';');
        String pair = separatorIndex >= 0 ? header.substring(0, separatorIndex) : header;
        return pair.substring(cookieName.length() + 1);
    }
}
