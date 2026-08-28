package com.biopet.facturacion.persistence;

import com.biopet.entity.Consulta;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withServerOrReceiverFault;

/**
 * Fase 8A: API REST de facturacion sobre MockMvc + PostgreSQL real, con el
 * SRI simulado (sin red, igual que {@code FacturaSriServiceIntegrationTest}).
 *
 * <p>El ARRANGE de cada test usa los servicios de dominio DIRECTAMENTE (sin
 * pasar por HTTP) para construir el estado previo -un borrador, una EMITIDA,
 * una AUTORIZADA-: eso ya lo prueba la Fase 4-7 exhaustivamente y repetirlo
 * aqui via MockMvc solo anadiria ruido. Lo que SI se ejercita por HTTP, con
 * autenticacion JWT real via {@code POST /api/auth/login}, es exactamente la
 * superficie nueva de esta fase: los endpoints, los permisos por rol, el
 * ownership de DUENO y el mapeo de errores.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FacturaControllerIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 1);
    private static final Path P12 = Path.of("target", "tmp", "factura-controller.p12");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
        registry.add("sri.soap.recepcion-url",
                () -> "http://localhost:1/RecepcionComprobantesOffline");
        registry.add("sri.soap.autorizacion-url",
                () -> "http://localhost:1/AutorizacionComprobantesOffline");
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
    // 1. ADMIN consulta
    // ==================================================================

    @Test
    void adminConsultaListadoYDetalle() throws Exception {
        Escenario esc = new Escenario();
        Factura factura = esc.emitida();

        String token = login(esc.admin, CLAVE);

        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/facturas/" + factura.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(factura.getId()))
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.claveAcceso").value(factura.getClaveAcceso()));
    }

    // ==================================================================
    // 2-5. AUXILIAR: crear, emitir, generar XML, firmar
    // ==================================================================

    @Test
    void auxiliarCreaBorrador() throws Exception {
        Escenario esc = new Escenario();
        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId": %d, "fechaEmision": "%s"}
                                """.formatted(esc.dueno.getId(), FECHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.usuarioId").value(esc.dueno.getId()))
                .andExpect(jsonPath("$.claveAcceso").doesNotExist());
    }

    @Test
    void auxiliarEmite() throws Exception {
        Escenario esc = new Escenario();
        Long borradorId = esc.borradorListoParaEmitir();
        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + borradorId + "/emitir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"puntoEmisionId": %d}
                                """.formatted(esc.punto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.secuencial").value(1))
                .andExpect(jsonPath("$.claveAcceso").isNotEmpty())
                // El ambiente sale SIEMPRE del backend (sri.ambiente=PRUEBAS
                // en test), nunca del request: no se mando ninguno.
                .andExpect(jsonPath("$.ambiente").value("PRUEBAS"));

        // Idempotencia REST: emitir dos veces no reserva otro secuencial.
        mockMvc.perform(post("/api/facturas/" + borradorId + "/emitir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"puntoEmisionId": %d}
                                """.formatted(esc.punto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secuencial").value(1));
    }

    /**
     * Correccion pre-commit de la Fase 8A, punto 1: EmitirFacturaRequest ya no
     * tiene campo {@code ambiente} -no compila si se intenta leer-, y aunque el
     * cliente mande la clave JSON "ambiente" en el cuerpo (Jackson la ignora,
     * no hay setter que la reciba), la factura emitida usa el ambiente que
     * dice el backend, nunca PRODUCCION pedido desde fuera.
     */
    @Test
    void noExisteFormaHttpDeSolicitarProduccion() throws Exception {
        Escenario esc = new Escenario();
        Long borradorId = esc.borradorListoParaEmitir();
        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + borradorId + "/emitir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"puntoEmisionId": %d, "ambiente": "PRODUCCION"}
                                """.formatted(esc.punto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambiente").value("PRUEBAS"));
    }

    @Test
    void auxiliarGeneraXml() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/generar-xml")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentosDisponibles[0]").value("XML_GENERADO"));
    }

    @Test
    void auxiliarFirma() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/firmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentosDisponibles", org.hamcrest.Matchers.hasItem("XML_FIRMADO")));
    }

    // ==================================================================
    // 6-7. SRI: enviar y sincronizar
    // ==================================================================

    @Test
    void auxiliarInvocaEnviarSriConSoapMockeado() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());
        String clave = emitida.getClaveAcceso();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));

        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/enviar-sri")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUTORIZADA"))
                .andExpect(jsonPath("$.estadoAutorizacion").value("AUT"))
                .andExpect(jsonPath("$.numeroAutorizacion").value(clave));

        sriRecepcion.verify();
        sriAutorizacion.verify();
    }

    @Test
    void sincronizacionSriResuelveUnaPendiente() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());
        String clave = emitida.getClaveAcceso();

        // Todas las respuestas se programan por adelantado y EN ORDEN (ver la
        // misma leccion documentada en FacturaSriServiceIntegrationTest):
        // MockWebServiceServer no admite anadir expectativas nuevas una vez
        // que ya se consumio alguna de las declaradas antes.
        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));

        sriService.enviar(emitida.getId());

        String token = login(esc.auxiliar, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/sincronizar-sri")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUTORIZADA"));

        sriRecepcion.verify();
        sriAutorizacion.verify();
    }

    // ==================================================================
    // 8-9. VETERINARIO: puede preparar, no puede tocar el pipeline fiscal
    // ==================================================================

    @Test
    void veterinarioNoPuedeEmitir() throws Exception {
        Escenario esc = new Escenario();
        Long borradorId = esc.borradorListoParaEmitir();
        String token = login(esc.veterinario, CLAVE);

        mockMvc.perform(post("/api/facturas/" + borradorId + "/emitir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"puntoEmisionId": %d}
                                """.formatted(esc.punto.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarioNoPuedeEnviarSri() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());
        String token = login(esc.veterinario, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/enviar-sri")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Nunca llego a tocar el SOAP simulado: sin expectativas declaradas,
        // verify() confirma que no se llamo a nada.
        sriRecepcion.verify();
        sriAutorizacion.verify();
    }

    /**
     * Correccion pre-commit Fase 8A (ultima ronda): se evaluo si existe una
     * relacion real y verificable entre un VETERINARIO y un BORRADOR para
     * proteger la ESCRITURA. No existe -ver "Por que VETERINARIO no escribe
     * borradores" en {@code FacturaController}-, asi que se retiro VETERINARIO
     * de los cinco endpoints de escritura de borrador. Este test cubre los
     * cinco, siempre con 403: crear, cabecera, comprador, detalles, pagos.
     */
    @Test
    void veterinarioNoPuedeEscribirNingunEndpointDeBorrador() throws Exception {
        Escenario esc = new Escenario();
        Long borradorId = esc.borradorListoParaEmitir();
        String token = login(esc.veterinario, CLAVE);

        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId": %d, "fechaEmision": "%s"}
                                """.formatted(esc.dueno.getId(), FECHA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/facturas/" + borradorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaEmision": "%s"}
                                """.formatted(FECHA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/facturas/" + borradorId + "/comprador")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datosFacturacionId\": " + esc.datos.getId() + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/facturas/" + borradorId + "/detalles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\": []}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/facturas/" + borradorId + "/pagos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagos\": []}"))
                .andExpect(status().isForbidden());
    }

    /**
     * ADMIN sigue pudiendo escribir borradores (sanity minima: AUXILIAR ya lo
     * cubren varios tests de las secciones 2-5 de arriba).
     */
    @Test
    void adminPuedeCrearYEditarBorrador() throws Exception {
        Escenario esc = new Escenario();
        String token = login(esc.admin, CLAVE);

        MvcResult creado = mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId": %d, "fechaEmision": "%s"}
                                """.formatted(esc.dueno.getId(), FECHA)))
                .andExpect(status().isCreated())
                .andReturn();

        long borradorId = ((Number) com.jayway.jsonpath.JsonPath
                .read(creado.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/facturas/" + borradorId + "/comprador")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datosFacturacionId\": " + esc.datos.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compradorIdentificacion").isNotEmpty());
    }

    // ==================================================================
    // Correccion pre-commit Fase 8A, punto 2: VETERINARIO sin lectura global
    // ==================================================================

    @Test
    void veterinarioVeFacturaConOrigenEnSuPropiaConsulta() throws Exception {
        Escenario esc = new Escenario();
        Mascota mascota = nuevaMascota(esc.dueno);
        Consulta consulta = nuevaConsulta(mascota, esc.veterinario);
        Factura factura = esc.emitidaConOrigen(mascota, OrigenDetalleFactura.CONSULTA, consulta.getId());

        String token = login(esc.veterinario, CLAVE);

        mockMvc.perform(get("/api/facturas/" + factura.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(factura.getId()));

        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(factura.getId()));
    }

    @Test
    void veterinarioNoVeFacturaRelacionadaSoloConOtroVeterinario() throws Exception {
        Escenario esc = new Escenario();
        Usuario otroVeterinario = crearUsuario(Rol.ROLE_VETERINARIO, CLAVE);
        Mascota mascota = nuevaMascota(esc.dueno);
        Consulta consultaDeOtro = nuevaConsulta(mascota, otroVeterinario);
        Factura factura = esc.emitidaConOrigen(mascota, OrigenDetalleFactura.CONSULTA, consultaDeOtro.getId());

        String token = login(esc.veterinario, CLAVE);

        // Acceso directo por id ajeno -> 403, siguiendo la misma convencion
        // que el ownership de DUENO (nunca 404: no hay que confirmar ni negar
        // si el id existe).
        mockMvc.perform(get("/api/facturas/" + factura.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));

        // Y no aparece colada en su listado.
        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void adminYAuxiliarSiguenViendoCualquierFacturaSinImportarElOrigen() throws Exception {
        Escenario esc = new Escenario();
        Usuario otroVeterinario = crearUsuario(Rol.ROLE_VETERINARIO, CLAVE);
        Mascota mascota = nuevaMascota(esc.dueno);
        Consulta consultaDeOtro = nuevaConsulta(mascota, otroVeterinario);
        Factura factura = esc.emitidaConOrigen(mascota, OrigenDetalleFactura.CONSULTA, consultaDeOtro.getId());

        for (Usuario global : List.of(esc.admin, esc.auxiliar)) {
            String token = login(global, CLAVE);
            mockMvc.perform(get("/api/facturas/" + factura.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(factura.getId()));
        }
    }

    // ==================================================================
    // 10-13. DUENO: solo lo suyo, solo AUTORIZADA, solo XML_AUTORIZADO
    // ==================================================================

    @Test
    void duenoVeFacturaPropiaAutorizada() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada(sriRecepcion, sriAutorizacion);
        String token = login(esc.dueno, CLAVE);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUTORIZADA"));

        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(autorizada.getId()));
    }

    @Test
    void duenoNoVeFacturaDeOtroDueno() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada(sriRecepcion, sriAutorizacion);

        Usuario otroDueno = crearUsuario(Rol.ROLE_DUENO, CLAVE);
        String tokenOtro = login(otroDueno, CLAVE);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId())
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void duenoNoVeBorradorNiEmitidaAunqueSeaSuyaPropia() throws Exception {
        Escenario esc = new Escenario();
        Long borradorId = esc.borradorListoParaEmitir();
        Factura emitidaAjena = esc.emitida();
        String token = login(esc.dueno, CLAVE);

        mockMvc.perform(get("/api/facturas/" + borradorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/facturas/" + emitidaAjena.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Y tampoco aparecen coladas en su propio listado.
        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void duenoDescargaSoloXmlAutorizadoPropio() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada(sriRecepcion, sriAutorizacion);
        String token = login(esc.dueno, CLAVE);

        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/documentos/XML_AUTORIZADO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML));

        // Ni firmado ni generado, aunque tambien existan.
        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/documentos/XML_FIRMADO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/facturas/" + autorizada.getId() + "/documentos/XML_GENERADO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // 14. Descarga: bytes exactos
    // ==================================================================

    @Test
    void descargaMantieneBytesExactos() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada(sriRecepcion, sriAutorizacion);
        byte[] esperado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(autorizada.getId(),
                        com.biopet.facturacion.entity.TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow().getContenido();

        String token = login(esc.admin, CLAVE);

        MvcResult resultado = mockMvc.perform(
                        get("/api/facturas/" + autorizada.getId() + "/documentos/XML_AUTORIZADO")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsByteArray())
                .isEqualTo(esperado);
    }

    // ==================================================================
    // 15. Transicion invalida -> 409
    // ==================================================================

    @Test
    void firmarSinXmlGeneradoDevuelve409() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        String token = login(esc.admin, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/firmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:conflict"));
    }

    @Test
    void editarDetallesDeUnaFacturaYaEmitidaDevuelve409() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        String token = login(esc.admin, CLAVE);

        mockMvc.perform(put("/api/facturas/" + emitida.getId() + "/detalles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"detalles": []}
                                """))
                .andExpect(status().isConflict());
    }

    // ==================================================================
    // 16-17. Fallos del SRI: 504 y 502
    // ==================================================================

    @Test
    void timeoutSriDevuelve504() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());

        sriRecepcion.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        String token = login(esc.admin, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/enviar-sri")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:external-service-timeout"));
    }

    @Test
    void errorSoapDevuelve502() throws Exception {
        Escenario esc = new Escenario();
        Factura emitida = esc.emitida();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());

        sriRecepcion.expect(anything())
                .andRespond(withServerOrReceiverFault("Servicio no disponible", java.util.Locale.getDefault()));

        String token = login(esc.admin, CLAVE);

        mockMvc.perform(post("/api/facturas/" + emitida.getId() + "/enviar-sri")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:external-service"));
    }

    // ==================================================================
    // 18. Mass assignment: campos prohibidos, ignorados por el DTO
    // ==================================================================

    @Test
    void noEsPosibleInyectarPrecioSecuencialOEstadoDesdeElCliente() throws Exception {
        Escenario esc = new Escenario();
        String token = login(esc.auxiliar, CLAVE);

        // El DTO de creacion no tiene esos campos: Jackson los ignora
        // silenciosamente (no hay setter que mapee). Se comprueba que el
        // borrador creado no adopta ninguno de los valores "colados".
        MvcResult creado = mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId": %d, "fechaEmision": "%s",
                                 "estado": "AUTORIZADA",
                                 "claveAcceso": "0000000000000000000000000000000000000000000000",
                                 "secuencial": 999999, "importeTotal": 1.00}
                                """.formatted(esc.dueno.getId(), FECHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.claveAcceso").doesNotExist())
                .andExpect(jsonPath("$.secuencial").doesNotExist())
                .andReturn();

        long borradorId = ((Number) com.jayway.jsonpath.JsonPath
                .read(creado.getResponse().getContentAsString(), "$.id")).longValue();

        // Igual con el precio de una linea: el catalogo manda, no el cliente.
        mockMvc.perform(post("/api/facturas/" + borradorId + "/comprador")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datosFacturacionId\": " + esc.datos.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/facturas/" + borradorId + "/detalles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"detalles": [
                                  {"conceptoFacturableId": %d, "cantidad": 1, "precioUnitario": 0.01,
                                   "impuestoTarifa": 0}
                                ]}
                                """.formatted(esc.concepto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(20.0))
                .andExpect(jsonPath("$.detalles[0].impuestoTarifa").value(15.0));
    }

    // ==================================================================
    // 19. Sin BYTEA ni secretos en la respuesta
    // ==================================================================

    @Test
    void laRespuestaDeFacturaNoContieneXmlNiSecretos() throws Exception {
        Escenario esc = new Escenario();
        Factura autorizada = esc.autorizada(sriRecepcion, sriAutorizacion);
        String token = login(esc.admin, CLAVE);

        MvcResult resultado = mockMvc.perform(get("/api/facturas/" + autorizada.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cuerpo)
                .doesNotContain("<factura")
                .doesNotContain("ds:Signature")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase(".p12")
                .doesNotContainIgnoringCase("SRI_CERT");
    }

    // ==================================================================
    // 20. Autenticacion obligatoria
    // ==================================================================

    @Test
    void endpointsSensiblesRequierenAutenticacion() throws Exception {
        mockMvc.perform(get("/api/facturas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // Utilidades: autenticacion
    // ==================================================================

    private static final String CLAVE = "ClaveDePrueba123*";

    private String login(Usuario usuario, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(usuario.getEmail(), password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(resultado, "access_token");
    }

    private String extractCookieValue(MvcResult resultado, String nombreCookie) {
        return resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(valor -> valor.startsWith(nombreCookie + "="))
                .findFirst()
                .map(valor -> valor.substring((nombreCookie + "=").length()).split(";", 2)[0])
                .orElseThrow(() -> new AssertionError(
                        "No se encontro la cookie '" + nombreCookie + "' en la respuesta de login."));
    }

    /**
     * Usuario con contrasena REAL (bcrypt), a diferencia de
     * {@code FacturaEscenarioTestBase#nuevoUsuario}, que usa un hash ficticio
     * "x" -sirve para fixtures que no inician sesion, pero aqui hace falta
     * poder autenticarse de verdad via {@code POST /api/auth/login}.
     */
    private Usuario crearUsuario(Rol rol, String password) {
        // El email SIEMPRE en minuscula: AuthService.login hace
        // request.email().toLowerCase() antes de buscar, y Postgres compara
        // "email" con igualdad exacta (sensible a mayusculas). Un email con
        // alguna mayuscula aqui autentica con 401 aunque la contrasena sea
        // correcta.
        String email = ("controller-" + rol + "-" + siguiente() + "@biopet.test")
                .toLowerCase(java.util.Locale.ROOT);
        return usuarioRepository.save(Usuario.builder()
                .nombre("Fixture " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build());
    }

    // ------------------------------------------------------------------
    // Respuestas del SRI simulado (mismo binding cualificado que
    // FacturaSriServiceIntegrationTest / SriBindingContraWsdlTest).
    // ------------------------------------------------------------------

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
                      <fechaAutorizacion>2026-10-01T10:30:00-05:00</fechaAutorizacion>
                      <ambiente>PRUEBAS</ambiente>
                      <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
                      <mensajes/>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(clave, clave, clave));
    }

    private static StringSource enProcesamiento() {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>EN PROCESAMIENTO</estado>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """);
    }

    // ==================================================================
    // Escenario compartido
    // ==================================================================

    private final class Escenario {
        final EmisorFiscal emisor = nuevoEmisor();
        final PuntoEmision punto = nuevoPunto(emisor);
        final ConceptoFacturable concepto;
        final Usuario admin = crearUsuario(Rol.ROLE_ADMIN, CLAVE);
        final Usuario auxiliar = crearUsuario(Rol.ROLE_AUXILIAR, CLAVE);
        final Usuario veterinario = crearUsuario(Rol.ROLE_VETERINARIO, CLAVE);
        final Usuario dueno = crearUsuario(Rol.ROLE_DUENO, CLAVE);
        final DatosFacturacion datos;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            concepto = conceptoFacturableRepository.findById(conceptoCompartidoId).orElseThrow();
            datos = nuevosDatos(dueno, TipoIdentificacionSri.CEDULA,
                    String.format("%010d", siguiente()), "PERSONA FICTICIA " + siguiente());
        }

        /** Borrador con comprador, un detalle y un pago que cuadra: listo para /emitir. */
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
            return emisionService.emitir(
                    new EmitirFacturaCommand(borradorId, punto.getId(), AmbienteSri.PRUEBAS));
        }

        /**
         * Igual que {@link #emitida()}, pero con la mascota indicada y UN
         * detalle con el origen clinico dado -para las pruebas de la regla de
         * visibilidad de VETERINARIO-. El comprador sigue siendo
         * {@code dueno}: {@code mascota} debe pertenecerle
         * ({@code FacturaBorradorService} lo exige).
         */
        Factura emitidaConOrigen(Mascota mascota, OrigenDetalleFactura origenTipo, Long origenId) {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(dueno.getId(), mascota.getId(), FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(), List.of(
                    new DetalleBorradorCommand(concepto.getId(), new BigDecimal("2"), null,
                            origenTipo, origenId)));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return emisionService.emitir(
                    new EmitirFacturaCommand(borrador.getId(), punto.getId(), AmbienteSri.PRUEBAS));
        }

        /**
         * Lleva una factura hasta AUTORIZADA por completo (XML, firma, SRI),
         * usando el mismo servidor SOAP simulado que el test ya tiene
         * preparado. Las expectativas se declaran aqui porque son siempre las
         * mismas dos (recepcion RECIBIDA, autorizacion AUT).
         */
        Factura autorizada(MockWebServiceServer recepcion, MockWebServiceServer autorizacion) {
            Factura emitida = emitida();
            xmlService.generarXml(emitida.getId());
            firmaService.firmarFactura(emitida.getId());
            String clave = emitida.getClaveAcceso();

            recepcion.expect(anything()).andRespond(withPayload(recibida()));
            autorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));
            sriService.enviar(emitida.getId());

            return facturaRepository.findById(emitida.getId()).orElseThrow();
        }
    }
}
