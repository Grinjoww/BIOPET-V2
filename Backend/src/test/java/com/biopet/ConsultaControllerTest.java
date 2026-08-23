package com.biopet;

import com.biopet.entity.Consulta;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsultaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    MascotaRepository mascotaRepository;

    @Autowired
    ConsultaRepository consultaRepository;

    @Autowired
    CitaRepository citaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @Autowired
    CacheManager cacheManager;

    private Long veterinarioId;
    private Long mascotaId;

    @BeforeEach
    void setUp() {
        consultaRepository.deleteAll();
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();

        // El listado de consultas (ConsultaService.listar) está cacheado por
        // email+página. Sin limpiar aquí, un test posterior que reutilice el
        // mismo email (p.ej. "jaime@biopet.com") podría recibir una página
        // cacheada de datos ya borrados por el deleteAll() de arriba.
        if (cacheManager.getCache("consultas") != null) {
            cacheManager.getCache("consultas").clear();
        }

        Usuario admin = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);

        Usuario veterinario = Usuario.builder()
                .nombre("Vet Real")
                .email("vet@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveVet123*"))
                .rol(Rol.ROLE_VETERINARIO)
                .activo(true)
                .build();

        veterinarioId = usuarioRepository.save(veterinario).getId();

        Usuario dueno = Usuario.builder()
                .nombre("Dueño Real")
                .email("dueno@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveDueno123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();

        Usuario duenoGuardado = usuarioRepository.save(dueno);

        Mascota mascota = Mascota.builder()
                .duenio(duenoGuardado)
                .nombre("Firulais")
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(java.time.LocalDate.of(2020, 1, 1))
                .activo(true)
                .build();

        mascotaId = mascotaRepository.save(mascota).getId();

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void adminCreaConsultaExitosamente() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.veterinarioId").value(veterinarioId))
                .andExpect(jsonPath("$.motivo").value("Chequeo general"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void duenoNoPuedeCrearConsultaDevuelve403() throws Exception {
        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void buscarConsultaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.instance").value("/api/consultas/999999"));
    }

    @Test
    void crearConsultaConCamposInvalidosDevuelve422() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":""}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.errors.motivo").isArray());
    }

    @Test
    void duenoDeOtraMascotaNoPuedeVerConsultaAjena() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated());

        Long consultaId = consultaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Consulta no fue creada"))
                .getId();

        Usuario otroDueno = Usuario.builder()
                .nombre("Otro Dueño")
                .email("otro.dueno@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveOtro123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();

        usuarioRepository.save(otroDueno);

        String tokenOtroDueno = extractCookieValue(
                iniciarSesion("otro.dueno@biopet.com", "ClaveOtro123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenOtroDueno))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEliminaConsultaExitosamente() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated());

        Long consultaId = consultaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Consulta no fue creada"))
                .getId();

        mockMvc.perform(delete("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Consulta eliminada = consultaRepository.findById(consultaId)
                .orElseThrow(() ->
                        new AssertionError("Consulta eliminada físicamente: " + consultaId)
                );

        assertFalse(eliminada.isActivo());
    }

    // ---------- listar / aislamiento por propiedad (Corrección A) ----------

    @Test
    void duenoSoloVeConsultasDeSusPropiasMascotasEnListado() throws Exception {
        Long consultaPropiaId = crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo dueño principal");

        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.listado@biopet.com",
                "ClaveOtro123*",
                Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        Long consultaAjenaId = crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Chequeo otro dueño");

        String tokenDuenoPrincipal = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenDuenoPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(consultaPropiaId))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascotaId));

        String tokenOtroDueno = extractCookieValue(
                iniciarSesion("otro.dueno.listado@biopet.com", "ClaveOtro123*"),
                "access_token"
        );

        // Caso crítico: la consulta del dueño principal nunca debe aparecer
        // en el listado del otro dueño, y viceversa (ya verificado arriba).
        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenOtroDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(consultaAjenaId))
                .andExpect(jsonPath("$.content[0].mascotaId").value(otraMascotaId));
    }

    @Test
    void adminVeTodasLasConsultasEnListado() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");
        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.admin@biopet.com", "ClaveOtro123*", Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Chequeo 2");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void veterinarioVeTodasLasConsultasEnListado() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");
        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.vet@biopet.com", "ClaveOtro123*", Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Chequeo 2");

        String tokenVeterinario = extractCookieValue(
                iniciarSesion("vet@biopet.com", "ClaveVet123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void auxiliarVeTodasLasConsultasEnListado() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");
        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.aux@biopet.com", "ClaveOtro123*", Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Chequeo 2");

        crearUsuarioConRol("auxiliar@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAuxiliar = extractCookieValue(
                iniciarSesion("auxiliar@biopet.com", "ClaveAux123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void duenoSinConsultasRecibeListadoVacioConEstadoOk() throws Exception {
        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void paginacionDelDuenoSeAplicaDespuesDelFiltroDePropiedad() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta propia 1");
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta propia 2");
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta propia 3");

        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.pag@biopet.com", "ClaveOtro123*", Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Consulta ajena");

        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        // El dueño tiene 3 consultas propias (de 4 totales en el sistema).
        // Con size=2, la primera página debe traer 2, y el total de páginas
        // debe calcularse sobre las 3 propias, no sobre las 4 del sistema.
        mockMvc.perform(get("/api/consultas")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void consultaDadaDeBajaNoApareceEnListadoDelDueno() throws Exception {
        Long consultaId = crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo a eliminar");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );
        mockMvc.perform(delete("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ---------- GET /api/consultas/mascota/{mascotaId} (ficha de mascota) ----------

    @Test
    void adminListaConsultasPorMascota() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 2");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascotaId));
    }

    @Test
    void veterinarioListaConsultasPorMascota() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");

        String tokenVeterinario = extractCookieValue(
                iniciarSesion("vet@biopet.com", "ClaveVet123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void auxiliarListaConsultasPorMascota() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo 1");
        crearUsuarioConRol("auxiliar.mascota@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);

        String tokenAuxiliar = extractCookieValue(
                iniciarSesion("auxiliar.mascota@biopet.com", "ClaveAux123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void duenoListaConsultasDeSuPropiaMascota() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo dueño");

        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascotaId));
    }

    @Test
    void duenoNoPuedeListarConsultasDeMascotaAjenaPorMascotaDevuelve403() throws Exception {
        Long otroDuenoId = crearUsuarioConRolYObtenerId(
                "otro.dueno.mascota403@biopet.com", "ClaveOtro123*", Rol.ROLE_DUENO
        );
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearConsultaYObtenerId(otraMascotaId, veterinarioId, "Chequeo ajeno");

        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + otraMascotaId)
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void mascotaSinConsultasDevuelvePaginaVacia() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void consultaDadaDeBajaNoApareceEnListadoPorMascota() throws Exception {
        Long consultaId = crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo a eliminar");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );
        mockMvc.perform(delete("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginacionRealDeConsultasPorMascota() throws Exception {
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta 1");
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta 2");
        crearConsultaYObtenerId(mascotaId, veterinarioId, "Consulta 3");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void rutaMascotaNoColisionaConBusquedaPorId() throws Exception {
        Long consultaId = crearConsultaYObtenerId(mascotaId, veterinarioId, "Chequeo");

        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        // /mascota/{mascotaId} devuelve una Page (con "content"/"totalElements"),
        // nunca una única ConsultaResponse — confirma que no se coló por /{id}.
        mockMvc.perform(get("/api/consultas/mascota/" + mascotaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.motivo").doesNotExist());

        // Y /{id} sigue funcionando exactamente igual que antes.
        mockMvc.perform(get("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(consultaId))
                .andExpect(jsonPath("$.content").doesNotExist());

        // Una mascota inexistente da 404 "Mascota no encontrada" (no un
        // intento fallido de interpretar "mascota" como id numérico: el
        // patrón \\d+ en /{id} ya lo impide en tiempo de enrutamiento).
        mockMvc.perform(get("/api/consultas/mascota/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Mascota no encontrada: 999999"));
    }

    private Long crearConsultaYObtenerId(Long mascotaId, Long veterinarioId, String motivo) {
        Mascota mascota = mascotaRepository.findById(mascotaId).orElseThrow();
        Usuario veterinario = usuarioRepository.findById(veterinarioId).orElseThrow();

        Consulta consulta = Consulta.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaConsulta(Instant.now())
                .motivo(motivo)
                .activo(true)
                .build();

        return consultaRepository.save(consulta).getId();
    }

    private Long crearMascotaYObtenerId(Long duenioId, String nombre) {
        Usuario duenio = usuarioRepository.findById(duenioId).orElseThrow();

        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre(nombre)
                .especie("Gato")
                .raza("Mestizo")
                .fechaNacimiento(java.time.LocalDate.of(2021, 3, 1))
                .activo(true)
                .build();

        return mascotaRepository.save(mascota).getId();
    }

    private Long crearUsuarioConRolYObtenerId(String email, String password, Rol rol) {
        crearUsuarioConRol(email, password, rol);

        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("Usuario no encontrado tras crearlo: " + email))
                .getId();
    }

    private void crearUsuarioConRol(String email, String password, Rol rol) {
        Usuario usuario = Usuario.builder()
                .nombre("Usuario Prueba")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();

        usuarioRepository.save(usuario);
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
        List<String> setCookieHeaders =
                result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        String header = setCookieHeaders.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "No se encontró la cookie '" +
                                        cookieName +
                                        "' en las cabeceras Set-Cookie"
                        )
                );

        int separatorIndex = header.indexOf(';');

        String pair = separatorIndex >= 0
                ? header.substring(0, separatorIndex)
                : header;

        return pair.substring(cookieName.length() + 1);
    }
}