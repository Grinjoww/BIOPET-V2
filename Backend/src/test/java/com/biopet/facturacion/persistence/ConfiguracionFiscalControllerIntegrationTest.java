package com.biopet.facturacion.persistence;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.service.SecuencialService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 8B: REST de configuracion fiscal (conceptos, puntos de emision,
 * tarifas, emisor), con autenticacion JWT real via {@code POST /api/auth/login}
 * -misma tecnica que {@link FacturaControllerIntegrationTest}, pero sin
 * necesidad de simular el SRI: ninguno de estos endpoints toca el pipeline de
 * emision-.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguracionFiscalControllerIntegrationTest extends FacturaEscenarioTestBase {

    private static final String CLAVE = "ClaveDePrueba123*";

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecuencialService secuencialService;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    // ==================================================================
    // Conceptos facturables
    // ==================================================================

    @Test
    void adminCreaConceptoYAuxiliarLoLee() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        String tokenAuxiliar = login(crearUsuario(Rol.ROLE_AUXILIAR));
        String codigo = "CPT-" + siguiente();

        MvcResult creado = mockMvc.perform(post("/api/facturacion/conceptos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"%s","descripcion":"Consulta general","tipo":"CONSULTA",
                                 "precioUnitario":15.50,"codigoImpuesto":"IVA","codigoPorcentaje":"4"}
                                """.formatted(codigo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        Long id = extraerId(creado);

        mockMvc.perform(get("/api/facturacion/conceptos").header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/facturacion/conceptos/" + id).header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo));

        // AUXILIAR no administra: ni crear, ni editar, ni cambiar estado.
        mockMvc.perform(post("/api/facturacion/conceptos")
                        .header("Authorization", "Bearer " + tokenAuxiliar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"CPT-X","descripcion":"x","tipo":"OTRO",
                                 "precioUnitario":1,"codigoImpuesto":"IVA","codigoPorcentaje":"4"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/facturacion/conceptos/" + id + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void duenoNoAccedeAlCatalogoDeConceptos() throws Exception {
        String tokenDueno = login(crearUsuario(Rol.ROLE_DUENO));
        mockMvc.perform(get("/api/facturacion/conceptos").header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden());
    }

    @Test
    void validacionRechazaConceptoConCodigoPorcentajeInvalido() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        mockMvc.perform(post("/api/facturacion/conceptos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"CPT-BAD","descripcion":"x","tipo":"OTRO",
                                 "precioUnitario":1,"codigoImpuesto":"IVA","codigoPorcentaje":"ABC"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    // ==================================================================
    // Puntos de emision
    // ==================================================================

    @Test
    void adminCreaPuntoDuplicadoDaConflicto() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();

        String establecimiento = String.format("%03d", siguiente() % 1000);
        String cuerpo = """
                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001",
                 "direccionEstablecimiento":"Sucursal"}
                """.formatted(emisor.getId(), establecimiento);

        mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict());
    }

    @Test
    void auxiliarSoloVePuntosActivos() throws Exception {
        EmisorFiscal emisor = nuevoEmisor();
        PuntoEmision activo = nuevoPunto(emisor, true);
        nuevoPunto(emisor, false);

        String tokenAuxiliar = login(crearUsuario(Rol.ROLE_AUXILIAR));
        mockMvc.perform(get("/api/facturacion/puntos-emision").header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + activo.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.activo == false)]").doesNotExist());

        // Ni alta ni edicion ni baja: solo lectura.
        mockMvc.perform(put("/api/facturacion/puntos-emision/" + activo.getId())
                        .header("Authorization", "Bearer " + tokenAuxiliar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEstablecimiento\":\"Otra\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Correccion post-8B: provisionar SecuencialEmision al crear el punto
    // ------------------------------------------------------------------

    @Test
    void crearPuntoEmisionProvisionaSecuencialEnAmbienteDeServidorConCero() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();
        String establecimiento = String.format("%03d", siguiente() % 1000);

        MvcResult creado = mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001",
                                 "direccionEstablecimiento":"Sucursal"}
                                """.formatted(emisor.getId(), establecimiento)))
                .andExpect(status().isCreated())
                .andReturn();
        Long puntoId = extraerId(creado);

        // El ambiente efectivo del contenedor de pruebas es PRUEBAS (ver
        // application.yml/SriAmbienteProperties): el servidor decide, nunca el
        // cliente, que no incluyo -ni podria- ese campo en el body.
        SecuencialEmision secuencial = secuencialEmisionRepository
                .findByPuntoEmision_IdAndAmbiente(puntoId, AmbienteSri.PRUEBAS)
                .orElseThrow(() -> new AssertionError("No se provisiono el SecuencialEmision de PRUEBAS."));
        org.assertj.core.api.Assertions.assertThat(secuencial.getUltimoSecuencial()).isEqualTo(0L);

        // Nunca se crea el contador de un ambiente que nadie pidio.
        org.assertj.core.api.Assertions.assertThat(
                secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(puntoId, AmbienteSri.PRODUCCION))
                .isEmpty();
    }

    @Test
    void crearPuntoEmisionIgnoraCamposDeSecuencialEnviadosPorElCliente() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();
        String establecimiento = String.format("%03d", siguiente() % 1000);

        // PuntoEmisionRequest no declara "ambiente" ni ningun campo de
        // secuencial: el deserializador ignora lo que no reconoce, asi que la
        // peticion se acepta pero esos valores NUNCA llegan al servicio. Lo que
        // importa comprobar no es el codigo HTTP sino que el ambiente/contador
        // resultantes son los del SERVIDOR (PRUEBAS/0), nunca los que el
        // cliente intento colar.
        MvcResult creado = mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001",
                                 "ambiente":"PRODUCCION","ultimoSecuencial":999,"siguienteSecuencial":1000,
                                 "claveAcceso":"x"}
                                """.formatted(emisor.getId(), establecimiento)))
                .andExpect(status().isCreated())
                .andReturn();
        Long puntoId = extraerId(creado);

        org.assertj.core.api.Assertions.assertThat(
                secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(puntoId, AmbienteSri.PRODUCCION))
                .as("el ambiente sugerido por el cliente nunca debe provisionarse")
                .isEmpty();

        SecuencialEmision secuencial = secuencialEmisionRepository
                .findByPuntoEmision_IdAndAmbiente(puntoId, AmbienteSri.PRUEBAS)
                .orElseThrow(() -> new AssertionError("Debio provisionarse el ambiente real del servidor."));
        org.assertj.core.api.Assertions.assertThat(secuencial.getUltimoSecuencial())
                .as("el valor inicial sugerido por el cliente (999) nunca debe respetarse")
                .isEqualTo(0L);
    }

    @Test
    void editarPuntoEmisionNoResetaSuSecuencial() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();
        PuntoEmision punto = nuevoPunto(emisor);
        nuevoContador(punto, AmbienteSri.PRUEBAS, 7L);

        mockMvc.perform(put("/api/facturacion/puntos-emision/" + punto.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionEstablecimiento\":\"Direccion editada\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(ultimoSecuencial(punto, AmbienteSri.PRUEBAS)).isEqualTo(7L);
    }

    @Test
    void desactivarYReactivarPuntoEmisionNoResetaSuSecuencial() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();
        PuntoEmision punto = nuevoPunto(emisor);
        nuevoContador(punto, AmbienteSri.PRUEBAS, 3L);

        mockMvc.perform(patch("/api/facturacion/puntos-emision/" + punto.getId() + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(ultimoSecuencial(punto, AmbienteSri.PRUEBAS)).isEqualTo(3L);

        mockMvc.perform(patch("/api/facturacion/puntos-emision/" + punto.getId() + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(ultimoSecuencial(punto, AmbienteSri.PRUEBAS)).isEqualTo(3L);
    }

    @Test
    void secuencialProvisionadoAlCrearPermiteReservarElPrimerNumero() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        EmisorFiscal emisor = nuevoEmisor();
        String establecimiento = String.format("%03d", siguiente() % 1000);

        MvcResult creado = mockMvc.perform(post("/api/facturacion/puntos-emision")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emisorFiscalId":%d,"establecimiento":"%s","puntoEmision":"001"}
                                """.formatted(emisor.getId(), establecimiento)))
                .andExpect(status().isCreated())
                .andReturn();
        Long puntoId = extraerId(creado);

        long reservado = secuencialService.reservar(puntoId, AmbienteSri.PRUEBAS);
        org.assertj.core.api.Assertions.assertThat(reservado).isEqualTo(1L);
    }

    // ==================================================================
    // Tarifas de impuesto
    // ==================================================================

    @Test
    void crearNuevaVigenciaCierraAutomaticamenteLaAbierta() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        TarifaImpuesto abierta = nuevaTarifa(codigoPorcentaje, "12.00", LocalDate.of(2020, 1, 1), null);

        mockMvc.perform(post("/api/facturacion/tarifas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoImpuesto":"IVA","codigoPorcentaje":"%s","descripcion":"Nueva tarifa",
                                 "tarifa":15.00,"vigenteDesde":"2026-01-01"}
                                """.formatted(codigoPorcentaje)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vigenteHasta").doesNotExist());

        TarifaImpuesto recargada = tarifaImpuestoRepository.findById(abierta.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(recargada.getVigenteHasta())
                .isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void crearVigenciaQueEmpiezaAntesQueLaAbiertaEsConflicto() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        String codigoPorcentaje = nuevoCodigoPorcentaje();
        nuevaTarifa(codigoPorcentaje, "12.00", LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(post("/api/facturacion/tarifas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoImpuesto":"IVA","codigoPorcentaje":"%s","descripcion":"x",
                                 "tarifa":15.00,"vigenteDesde":"2025-01-01"}
                                """.formatted(codigoPorcentaje)))
                .andExpect(status().isConflict());
    }

    @Test
    void veterinarioNoAccedeATarifas() throws Exception {
        String tokenVet = login(crearUsuario(Rol.ROLE_VETERINARIO));
        mockMvc.perform(get("/api/facturacion/tarifas").header("Authorization", "Bearer " + tokenVet))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Emisor fiscal
    // ==================================================================

    @Test
    void adminConfiguraEmisorYAuxiliarSoloLee() throws Exception {
        String tokenAdmin = login(crearUsuario(Rol.ROLE_ADMIN));
        String tokenAuxiliar = login(crearUsuario(Rol.ROLE_AUXILIAR));
        String ruc = String.format("%010d", 900_000_000L + siguiente()) + "001";

        mockMvc.perform(put("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"%s","razonSocial":"BIOPET CIA LTDA","direccionMatriz":"Direccion",
                                 "obligadoContabilidad":true,"rimpe":false,"activo":true}
                                """.formatted(ruc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value(ruc));

        mockMvc.perform(get("/api/facturacion/emisor").header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value(ruc));

        // AUXILIAR no modifica configuracion fiscal.
        mockMvc.perform(put("/api/facturacion/emisor")
                        .header("Authorization", "Bearer " + tokenAuxiliar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"%s","razonSocial":"OTRO","direccionMatriz":"x",
                                 "obligadoContabilidad":false,"rimpe":false,"activo":true}
                                """.formatted(ruc)))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Autenticacion
    // ==================================================================

    @Test
    void endpointsDeConfiguracionRequierenAutenticacion() throws Exception {
        mockMvc.perform(get("/api/facturacion/conceptos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/facturacion/emisor")).andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private Usuario crearUsuario(Rol rol) {
        String email = ("cfg-" + rol + "-" + siguiente() + "@biopet.test").toLowerCase(java.util.Locale.ROOT);
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
