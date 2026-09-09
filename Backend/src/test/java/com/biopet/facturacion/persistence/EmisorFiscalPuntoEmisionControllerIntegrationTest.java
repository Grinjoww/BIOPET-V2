package com.biopet.facturacion.persistence;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corrección "demo local" (fase de facturación): cubre por HTTP real, contra
 * Postgres real (Testcontainers, ver FacturacionPostgresTestBase), el hueco
 * que dejaba sin probar "Crear Emisor"/"Crear Punto de emisión" -hasta ahora
 * los fixtures de todo el módulo insertaban {@code EmisorFiscal}/
 * {@code PuntoEmision} directo por repositorio
 * (FacturaEscenarioTestBase.nuevoEmisor/nuevoPunto), sin pasar nunca por
 * {@code EmisorFiscalController}/{@code PuntoEmisionController}-.
 *
 * <p>Diagnóstico: el flujo REST en sí funciona (confirmado aquí). El síntoma
 * reportado ("Crear Emisor ❌") era un bug de FRONTEND: cuando el formulario
 * Reactive Forms era inválido (RUC no de 13 dígitos, serie no de 3 dígitos),
 * el submit no hacía nada visible -ni banner, ni error por campo- (ver
 * facturacion-config.component.ts, corregido en esta misma fase).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmisorFiscalPuntoEmisionControllerIntegrationTest extends FacturaEscenarioTestBase {

    private static final String CLAVE = "ClaveDePrueba123*";

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void adminConfiguraElEmisorFiscalPorRestYQuedaDisponibleEnGet() throws Exception {
        Usuario admin = crearUsuario(Rol.ROLE_ADMIN);
        String token = login(admin);
        String ruc = String.format("%010d", 900_000_000L + siguiente()) + "001";

        mockMvc.perform(put("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"%s","razonSocial":"Clinica Demo","nombreComercial":"Biopet Demo",
                                 "direccionMatriz":"Av. Siempre Viva 123","obligadoContabilidad":false,
                                 "contribuyenteEspecial":null,"rimpe":false,"agenteRetencionResolucion":null,
                                 "activo":true}
                                """.formatted(ruc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value(ruc))
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value(ruc));
    }

    @Test
    void rucQueNoSonTrecerDigitosDevuelve422ConMensajeClaro() throws Exception {
        Usuario admin = crearUsuario(Rol.ROLE_ADMIN);
        String token = login(admin);

        mockMvc.perform(put("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"123","razonSocial":"Clinica Demo",
                                 "direccionMatriz":"Av. Siempre Viva 123","obligadoContabilidad":false,
                                 "rimpe":false,"activo":true}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void auxiliarPuedeLeerElEmisorPeroNoActualizarlo() throws Exception {
        Usuario auxiliar = crearUsuario(Rol.ROLE_AUXILIAR);
        String token = login(auxiliar);

        mockMvc.perform(put("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"1000000001001","razonSocial":"X","direccionMatriz":"Y",
                                 "obligadoContabilidad":false,"rimpe":false,"activo":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreaPuntoDeEmisionYQuedaListoParaEmitir_SecuencialProvisionadoEnCero() throws Exception {
        Usuario admin = crearUsuario(Rol.ROLE_ADMIN);
        String token = login(admin);
        var emisor = nuevoEmisor(); // fixture directo por repositorio: el emisor NO es lo que se está probando aquí.
        String establecimiento = String.format("%03d", siguiente() % 1000);

        MvcResult creado = mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001",
                                 "direccionEstablecimiento":"Sucursal norte"}
                                """.formatted(emisor.getId(), establecimiento)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.establecimiento").value(establecimiento))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        Long puntoId = Long.valueOf(creado.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // Correccion post-8B (ver PuntoEmisionService): el punto queda con su
        // SecuencialEmision ya provisionado, sin intervencion manual.
        assertEquals(0L, ultimoSecuencial(puntoEmisionRepository.findById(puntoId).orElseThrow(), AmbienteSri.PRUEBAS));
    }

    @Test
    void crearPuntoDeEmisionDuplicadoDevuelve409() throws Exception {
        Usuario admin = crearUsuario(Rol.ROLE_ADMIN);
        String token = login(admin);
        var emisor = nuevoEmisor();
        String establecimiento = String.format("%03d", siguiente() % 1000);
        String cuerpo = """
                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001"}
                """.formatted(emisor.getId(), establecimiento);

        mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict());
    }

    @Test
    void serieQueNoSonTresDigitosDevuelve422() throws Exception {
        Usuario admin = crearUsuario(Rol.ROLE_ADMIN);
        String token = login(admin);
        var emisor = nuevoEmisor();

        mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emisorFiscalId":%d,"establecimiento":"1","puntoEmision":"001"}
                                """.formatted(emisor.getId())))
                .andExpect(status().isUnprocessableEntity());
    }

    private Usuario crearUsuario(Rol rol) {
        String email = ("efpe-" + rol + "-" + siguiente() + "@biopet.test").toLowerCase(java.util.Locale.ROOT);
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
}
