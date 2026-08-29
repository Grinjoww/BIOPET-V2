package com.biopet.facturacion.persistence;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.firma.CertificadoPruebaFactory;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaSriService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

/**
 * REST del RIDE ({@code GET /api/facturas/{id}/ride}, Fase 10): permisos y
 * ownership por HTTP, con autenticacion JWT real -mismo patron que
 * {@code FacturaControllerIntegrationTest}-.
 *
 * <p>El contenido del PDF y la idempotencia ya se prueban exhaustivamente en
 * {@code FacturaRideServiceIntegrationTest} (llamando al servicio
 * directamente); aqui SOLO se ejercita la superficie nueva de esta fase: el
 * endpoint, el {@code @PreAuthorize}, el ownership real de DUENO y el mapeo a
 * 409/403/401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FacturaRideControllerIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 5);
    private static final String CLAVE = "ClaveDePrueba123*";
    private static final Path P12 = Path.of("target", "tmp", "ride-controller.p12");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
        registry.add("sri.soap.recepcion-url", () -> "http://localhost:1/RecepcionComprobantesOffline");
        registry.add("sri.soap.autorizacion-url", () -> "http://localhost:1/AutorizacionComprobantesOffline");
    }

    private static Long conceptoCompartidoId;

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaFirmaService firmaService;
    @Autowired FacturaSriService sriService;

    @Autowired @Qualifier("sriRecepcionWebServiceTemplate")
    WebServiceTemplate plantillaRecepcion;

    @Autowired @Qualifier("sriAutorizacionWebServiceTemplate")
    WebServiceTemplate plantillaAutorizacion;

    private MockWebServiceServer sriRecepcion;
    private MockWebServiceServer sriAutorizacion;

    @BeforeEach
    void setUp() {
        sriRecepcion = MockWebServiceServer.createServer(plantillaRecepcion);
        sriAutorizacion = MockWebServiceServer.createServer(plantillaAutorizacion);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        if (conceptoCompartidoId == null) {
            String codigo = nuevoCodigoPorcentaje();
            nuevaTarifa(codigo, "15.00", LocalDate.of(2020, 1, 1), null);
            conceptoCompartidoId = nuevoConcepto(codigo, "20.000000").getId();
        }
    }

    // ==================================================================
    // ADMIN / AUXILIAR: cualquier factura autorizada
    // ==================================================================

    @Test
    void adminDescargaElRideDeUnaFacturaAutorizada() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada();
        String token = login(esc.admin);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(result -> {
                    byte[] cuerpo = result.getResponse().getContentAsByteArray();
                    org.assertj.core.api.Assertions.assertThat(cuerpo.length).isGreaterThan(4);
                    org.assertj.core.api.Assertions.assertThat(
                            new String(cuerpo, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                });
    }

    @Test
    void auxiliarDescargaElRideDeUnaFacturaAutorizada() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada();
        String token = login(esc.auxiliar);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    // ==================================================================
    // DUENO: solo lo propio, y solo AUTORIZADA
    // ==================================================================

    @Test
    void duenoDescargaElRideDeSuPropiaFacturaAutorizada() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada();
        String token = login(esc.dueno);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    void duenoNoPuedeDescargarElRideDeLaFacturaAutorizadaDeOtroDueno() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada();
        Usuario otroDueno = crearUsuario(Rol.ROLE_DUENO, CLAVE);
        String token = login(otroDueno);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // VETERINARIO: sin acceso (misma politica que el resto de documentos)
    // ==================================================================

    @Test
    void veterinarioNuncaAccedeAlRide() throws Exception {
        // El @PreAuthorize del endpoint excluye VETERINARIO directamente
        // (misma politica que el resto de descargas de documentos fiscales,
        // ver FacturaConsultaService#exigirAccesoADocumento): ni siquiera
        // hace falta que la factura tenga una linea clinica suya para
        // comprobar el 403, la Fase 10 no le amplia el acceso solo por
        // existir el RIDE.
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada();
        Usuario veterinario = crearUsuario(Rol.ROLE_VETERINARIO, CLAVE);
        String token = login(veterinario);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Prematuro: 409 coherente
    // ==================================================================

    @Test
    void pedirElRideDeUnaFacturaTodaviaNoAutorizadaEs409() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        String token = login(esc.admin);

        mockMvc.perform(get("/api/facturas/" + emitida.getId() + "/ride")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    // ==================================================================
    // Autenticacion
    // ==================================================================

    @Test
    void sinAutenticacionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/facturas/1/ride")).andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

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

    private Usuario crearUsuario(Rol rol, String password) {
        String email = ("ride-" + rol + "-" + siguiente() + "@biopet.test").toLowerCase(java.util.Locale.ROOT);
        return usuarioRepository.save(Usuario.builder()
                .nombre("Fixture " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build());
    }

    private static StringSource recibida() {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>RECIBIDA</estado><comprobantes/>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """);
    }

    private static StringSource autorizado(String clave) {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>AUTORIZADO</estado>
                      <numeroAutorizacion>%s</numeroAutorizacion>
                      <fechaAutorizacion>2026-10-05T10:30:00-05:00</fechaAutorizacion>
                      <ambiente>PRUEBAS</ambiente>
                      <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
                      <mensajes/>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(clave, clave, clave));
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private final class Escenario {
        final EmisorFiscal emisor = nuevoEmisor();
        final PuntoEmision punto = nuevoPunto(emisor);
        final ConceptoFacturable concepto;
        final Usuario admin = crearUsuario(Rol.ROLE_ADMIN, CLAVE);
        final Usuario auxiliar = crearUsuario(Rol.ROLE_AUXILIAR, CLAVE);
        final Usuario dueno = crearUsuario(Rol.ROLE_DUENO, CLAVE);
        final DatosFacturacion datos;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            concepto = conceptoFacturableRepository.findById(conceptoCompartidoId).orElseThrow();
            datos = nuevosDatos(dueno, TipoIdentificacionSri.CEDULA,
                    String.format("%010d", siguiente()), "PERSONA FICTICIA " + siguiente());
        }

        Long borradorListoParaEmitir() {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(dueno.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return borrador.getId();
        }

        Factura emitida() {
            Long borradorId = borradorListoParaEmitir();
            return emisionService.emitir(new EmitirFacturaCommand(borradorId, punto.getId(), AmbienteSri.PRUEBAS));
        }

        Factura autorizada() {
            Factura emitida = emitida();
            xmlService.generarXml(emitida.getId());
            firmaService.firmarFactura(emitida.getId());
            String clave = emitida.getClaveAcceso();

            sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
            sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));
            sriService.enviar(emitida.getId());

            return facturaRepository.findById(emitida.getId()).orElseThrow();
        }
    }
}
