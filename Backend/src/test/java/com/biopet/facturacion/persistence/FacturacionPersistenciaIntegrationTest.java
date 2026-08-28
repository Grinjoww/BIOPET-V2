package com.biopet.facturacion.persistence;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.*;
import com.biopet.facturacion.repository.*;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Persistencia real de las once entidades del modulo sobre PostgreSQL: se
 * guardan, se vacia el contexto de persistencia y se recargan desde la base
 * para comprobar que lo que volvio es lo que se guardo.
 *
 * <p>El {@code entityManager.flush()} + {@code clear()} de {@link #recargar()}
 * no es decorativo: sin el, Hibernate devolveria la MISMA instancia que quedo
 * en el contexto de primer nivel y el test pasaria aunque el mapeo de columnas
 * fuese incorrecto. Vaciando el contexto se obliga a un SELECT de verdad.
 *
 * <p>La clase es {@code @Transactional}: hace falta una transaccion activa para
 * poder llamar a {@code flush()} y para navegar las relaciones LAZY, y ademas
 * el rollback automatico al final de cada test deja la base como estaba, que
 * importa porque este contenedor se comparte con las otras clases del modulo.
 */
@SpringBootTest
@Transactional
class FacturacionPersistenciaIntegrationTest extends FacturacionPostgresTestBase {

    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired EmisorFiscalRepository emisorFiscalRepository;
    @Autowired PuntoEmisionRepository puntoEmisionRepository;
    @Autowired SecuencialEmisionRepository secuencialEmisionRepository;
    @Autowired TarifaImpuestoRepository tarifaImpuestoRepository;
    @Autowired ConceptoFacturableRepository conceptoFacturableRepository;
    @Autowired DatosFacturacionRepository datosFacturacionRepository;
    @Autowired FacturaRepository facturaRepository;
    @Autowired FacturaDetalleRepository facturaDetalleRepository;
    @Autowired FacturaDocumentoRepository facturaDocumentoRepository;
    @Autowired FacturaEventoSriRepository facturaEventoSriRepository;

    /** Contador para generar valores unicos: la base se comparte entre clases. */
    private static final AtomicInteger SECUENCIA_FIXTURE = new AtomicInteger();

    private Usuario usuario;

    @BeforeEach
    void crearUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Dueno Persistencia")
                .email("persistencia-" + siguiente() + "@biopet.test")
                .passwordHash("x")
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build());
    }

    private int siguiente() {
        return SECUENCIA_FIXTURE.incrementAndGet();
    }

    private void recargar() {
        entityManager.flush();
        entityManager.clear();
    }

    // ==================================================================
    // Catalogos
    // ==================================================================

    @Test
    void emisorPuntoYSecuencialSeGuardanYRecargan() {
        EmisorFiscal emisor = emisorFiscalRepository.save(EmisorFiscal.builder()
                .ruc(rucFicticio())
                .razonSocial("EMISOR FICTICIO PERSISTENCIA")
                .nombreComercial("NOMBRE COMERCIAL FICTICIO")
                .direccionMatriz("Direccion matriz ficticia")
                .obligadoContabilidad(true)
                .contribuyenteEspecial("12345")
                .rimpe(false)
                .agenteRetencionResolucion("RES-FICTICIA")
                .activo(true)
                .build());

        PuntoEmision punto = puntoEmisionRepository.save(PuntoEmision.builder()
                .emisorFiscal(emisor)
                .establecimiento("007")
                .puntoEmision("003")
                .direccionEstablecimiento("Sucursal ficticia")
                .activo(true)
                .build());

        SecuencialEmision pruebas = secuencialEmisionRepository.save(SecuencialEmision.builder()
                .puntoEmision(punto).ambiente(AmbienteSri.PRUEBAS).ultimoSecuencial(41L).build());
        SecuencialEmision produccion = secuencialEmisionRepository.save(SecuencialEmision.builder()
                .puntoEmision(punto).ambiente(AmbienteSri.PRODUCCION).ultimoSecuencial(7L).build());

        recargar();

        EmisorFiscal emisorLeido = emisorFiscalRepository.findById(emisor.getId()).orElseThrow();
        assertThat(emisorLeido.getRuc()).isEqualTo(emisor.getRuc());
        assertThat(emisorLeido.isObligadoContabilidad()).isTrue();
        assertThat(emisorLeido.isRimpe()).isFalse();
        assertThat(emisorLeido.getCreadoEn()).isNotNull();
        assertThat(emisorLeido.getActualizadoEn()).isNotNull();

        PuntoEmision puntoLeido = puntoEmisionRepository.findById(punto.getId()).orElseThrow();
        assertThat(puntoLeido.getEstablecimiento()).isEqualTo("007");
        assertThat(puntoLeido.getPuntoEmision()).isEqualTo("003");
        // La relacion es LAZY: se navega dentro de la transaccion del test.
        assertThat(puntoLeido.getEmisorFiscal().getId()).isEqualTo(emisor.getId());

        // Los dos contadores del MISMO punto no se pisan.
        assertThat(secuencialEmisionRepository.findById(pruebas.getId()).orElseThrow()
                .getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
        assertThat(secuencialEmisionRepository.findById(produccion.getId()).orElseThrow()
                .getAmbiente()).isEqualTo(AmbienteSri.PRODUCCION);

        assertThat(secuencialEmisionRepository
                .findByPuntoEmision_IdAndAmbiente(punto.getId(), AmbienteSri.PRUEBAS)
                .orElseThrow().getUltimoSecuencial()).isEqualTo(41L);
        assertThat(secuencialEmisionRepository
                .findByPuntoEmision_IdAndAmbiente(punto.getId(), AmbienteSri.PRODUCCION)
                .orElseThrow().getUltimoSecuencial()).isEqualTo(7L);
    }

    @Test
    void elAmbienteSeGuardaComoElCodigoNumericoDelSriNoComoTextoNiOrdinal() {
        EmisorFiscal emisor = emisorFiscalRepository.save(nuevoEmisor());
        PuntoEmision punto = puntoEmisionRepository.save(PuntoEmision.builder()
                .emisorFiscal(emisor).establecimiento("010").puntoEmision("001").activo(true).build());
        SecuencialEmision contador = secuencialEmisionRepository.save(SecuencialEmision.builder()
                .puntoEmision(punto).ambiente(AmbienteSri.PRODUCCION).ultimoSecuencial(0L).build());

        recargar();

        // PRODUCCION es el codigo 2 de la TABLA 4. Si el mapeo usara ORDINAL
        // guardaria 1 (la posicion), que es justo el fallo que el converter
        // evita.
        Integer enLaBd = jdbc.queryForObject(
                "SELECT ambiente FROM secuencial_emision WHERE id = ?", Integer.class, contador.getId());
        assertThat(enLaBd).isEqualTo(2);
        assertThat(AmbienteSri.PRODUCCION.codigo()).isEqualTo("2");
    }

    @Test
    void tarifaImpuestoConservaSuPrecisionYAdmiteHistorico() {
        // Valores completamente ficticios: no representan ninguna tarifa real.
        TarifaImpuesto cerrada = tarifaImpuestoRepository.save(TarifaImpuesto.builder()
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("90")
                .descripcion("Tarifa ficticia cerrada")
                .tarifa(new BigDecimal("11.50"))
                .vigenteDesde(LocalDate.of(2020, 1, 1))
                .vigenteHasta(LocalDate.of(2021, 12, 31))
                .activo(false)
                .build());

        TarifaImpuesto vigente = tarifaImpuestoRepository.save(TarifaImpuesto.builder()
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("90")
                .descripcion("Tarifa ficticia vigente")
                .tarifa(new BigDecimal("13.25"))
                .vigenteDesde(LocalDate.of(2022, 1, 1))
                .activo(true)
                .build());

        recargar();

        TarifaImpuesto cerradaLeida = tarifaImpuestoRepository.findById(cerrada.getId()).orElseThrow();
        TarifaImpuesto vigenteLeida = tarifaImpuestoRepository.findById(vigente.getId()).orElseThrow();

        // NUMERIC(4,2): la escala se conserva exactamente, sin perder el 0 final.
        assertThat(cerradaLeida.getTarifa()).isEqualByComparingTo("11.50");
        assertThat(cerradaLeida.getTarifa().scale()).isEqualTo(2);
        assertThat(cerradaLeida.getVigenteHasta()).isEqualTo(LocalDate.of(2021, 12, 31));
        assertThat(vigenteLeida.getVigenteHasta()).isNull();
        assertThat(vigenteLeida.getCodigoImpuesto()).isEqualTo(CodigoImpuestoSri.IVA);

        // El codigo de impuesto viaja a la BD como "2", no como "IVA".
        String enLaBd = jdbc.queryForObject(
                "SELECT codigo_impuesto FROM tarifa_impuesto WHERE id = ?", String.class, vigente.getId());
        assertThat(enLaBd).isEqualTo("2");
    }

    @Test
    void conceptoFacturableGuardaSeisDecimalesDePrecio() {
        ConceptoFacturable concepto = conceptoFacturableRepository.save(ConceptoFacturable.builder()
                .codigo("FICT-" + siguiente())
                .descripcion("Concepto ficticio")
                .tipo(TipoConceptoFacturable.PROCEDIMIENTO)
                .precioUnitario(new BigDecimal("25.542365"))
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("90")
                .activo(true)
                .build());

        recargar();

        ConceptoFacturable leido = conceptoFacturableRepository.findById(concepto.getId()).orElseThrow();
        assertThat(leido.getPrecioUnitario()).isEqualByComparingTo("25.542365");
        assertThat(leido.getPrecioUnitario().scale()).isEqualTo(6);
        assertThat(leido.getTipo()).isEqualTo(TipoConceptoFacturable.PROCEDIMIENTO);

        // El enum interno SI viaja como texto (patron del proyecto).
        assertThat(jdbc.queryForObject("SELECT tipo FROM concepto_facturable WHERE id = ?",
                String.class, concepto.getId())).isEqualTo("PROCEDIMIENTO");
    }

    @Test
    void datosFacturacionSonIndependientesDeUsuarioYAdmitenVariasIdentidades() {
        DatosFacturacion conCedula = datosFacturacionRepository.save(DatosFacturacion.builder()
                .usuario(usuario)
                .tipoIdentificacion(TipoIdentificacionSri.CEDULA)
                .identificacion("0000000000")
                .razonSocial("PERSONA FICTICIA")
                .direccion("Direccion ficticia")
                .telefono("0999999999")
                .emailFacturacion("facturacion-ficticia@biopet.test")
                .predeterminado(true)
                .activo(true)
                .build());

        DatosFacturacion conRuc = datosFacturacionRepository.save(DatosFacturacion.builder()
                .usuario(usuario)
                .tipoIdentificacion(TipoIdentificacionSri.RUC)
                .identificacion(rucFicticio())
                .razonSocial("EMPRESA FICTICIA")
                .predeterminado(false)
                .activo(true)
                .build());

        recargar();

        assertThat(datosFacturacionRepository.findAllByUsuario_IdAndActivoTrue(usuario.getId()))
                .hasSize(2);

        DatosFacturacion predeterminada = datosFacturacionRepository
                .findByUsuario_IdAndPredeterminadoTrueAndActivoTrue(usuario.getId()).orElseThrow();
        assertThat(predeterminada.getId()).isEqualTo(conCedula.getId());
        assertThat(predeterminada.getTipoIdentificacion()).isEqualTo(TipoIdentificacionSri.CEDULA);

        // El tipo viaja como el codigo "05", que es el valor del catalogo.
        assertThat(jdbc.queryForObject("SELECT tipo_identificacion FROM datos_facturacion WHERE id = ?",
                String.class, conCedula.getId())).isEqualTo("05");
        assertThat(datosFacturacionRepository.findById(conRuc.getId()).orElseThrow()
                .getTipoIdentificacion()).isEqualTo(TipoIdentificacionSri.RUC);

        // Usuario NO gano ninguna columna fiscal: sigue siendo identidad pura.
        List<String> columnasDeUsuarios = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'usuarios'",
                String.class);
        assertThat(columnasDeUsuarios).containsExactlyInAnyOrder(
                "id", "nombre", "email", "password_hash", "rol", "activo", "creado_en", "actualizado_en");
    }

    // ==================================================================
    // Factura completa
    // ==================================================================

    @Test
    void facturaConDetallesYPagosSeGuardaYRecargaEntera() {
        Mascota mascota = mascotaRepository.save(Mascota.builder()
                .duenio(usuario).nombre("Firulais").especie("Perro").raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2021, 4, 4)).activo(true).build());

        Factura factura = facturaBorrador();
        factura.setMascota(mascota);
        factura.setEstado(EstadoFactura.EMITIDA);
        factura.setAmbiente(AmbienteSri.PRUEBAS);
        factura.setEstablecimiento("001");
        factura.setPuntoEmisionCodigo("001");
        factura.setSecuencial(123456789L);
        factura.setCodigoNumerico("12345678");
        factura.setClaveAcceso("3".repeat(49));
        factura.setTotalSinImpuestos(new BigDecimal("64.94"));
        factura.setTotalDescuento(new BigDecimal("0.00"));
        factura.setTotalImpuestos(new BigDecimal("8.44"));
        factura.setImporteTotal(new BigDecimal("73.38"));
        factura.setMoneda("DOLAR");

        FacturaDetalle detalle = FacturaDetalle.builder()
                .linea(1)
                .codigoPrincipal("FICT-001")
                .descripcion("Servicio ficticio")
                .cantidad(new BigDecimal("2.542563"))
                .precioUnitario(new BigDecimal("25.542365"))
                .descuento(new BigDecimal("0.00"))
                .precioTotalSinImpuesto(new BigDecimal("64.94"))
                .impuestoCodigo(CodigoImpuestoSri.IVA)
                .impuestoCodigoPorcentaje("90")
                .impuestoTarifa(new BigDecimal("13.00"))
                .baseImponible(new BigDecimal("64.94"))
                .impuestoValor(new BigDecimal("8.44"))
                .origenTipo(OrigenDetalleFactura.CONSULTA)
                .origenId(555L)
                .build();
        factura.agregarDetalle(detalle);

        FacturaPago pago = FacturaPago.builder()
                .formaPago(FormaPagoSri.TARJETA_CREDITO)
                .total(new BigDecimal("73.38"))
                .plazo(30)
                .unidadTiempo("dias")
                .build();
        factura.agregarPago(pago);

        Factura guardada = facturaRepository.save(factura);
        recargar();

        Factura leida = facturaRepository.findById(guardada.getId()).orElseThrow();

        assertThat(leida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(leida.getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
        assertThat(leida.getSecuencial()).isEqualTo(123456789L);
        assertThat(leida.getClaveAcceso()).hasSize(49);
        assertThat(leida.getImporteTotal()).isEqualByComparingTo("73.38");
        assertThat(leida.getImporteTotal().scale()).isEqualTo(2);
        assertThat(leida.getMascota().getId()).isEqualTo(mascota.getId());
        assertThat(leida.getUsuario().getId()).isEqualTo(usuario.getId());

        assertThat(leida.getDetalles()).hasSize(1);
        FacturaDetalle detalleLeido = leida.getDetalles().get(0);
        assertThat(detalleLeido.getCantidad()).isEqualByComparingTo("2.542563");
        assertThat(detalleLeido.getCantidad().scale()).isEqualTo(6);
        assertThat(detalleLeido.getPrecioUnitario()).isEqualByComparingTo("25.542365");
        assertThat(detalleLeido.getImpuestoTarifa()).isEqualByComparingTo("13.00");
        assertThat(detalleLeido.getImpuestoCodigo()).isEqualTo(CodigoImpuestoSri.IVA);
        assertThat(detalleLeido.getOrigenTipo()).isEqualTo(OrigenDetalleFactura.CONSULTA);
        assertThat(detalleLeido.getOrigenId()).isEqualTo(555L);

        assertThat(leida.getPagos()).hasSize(1);
        FacturaPago pagoLeido = leida.getPagos().get(0);
        assertThat(pagoLeido.getFormaPago()).isEqualTo(FormaPagoSri.TARJETA_CREDITO);
        assertThat(pagoLeido.getTotal()).isEqualByComparingTo("73.38");
        assertThat(pagoLeido.getPlazo()).isEqualTo(30);

        // La forma de pago viaja como "19", el codigo de la TABLA 24.
        assertThat(jdbc.queryForObject("SELECT forma_pago FROM factura_pagos WHERE id = ?",
                String.class, pagoLeido.getId())).isEqualTo("19");

        // Trazabilidad por origen clinico.
        assertThat(facturaDetalleRepository.findAllByOrigenTipoAndOrigenId(
                OrigenDetalleFactura.CONSULTA, 555L)).hasSize(1);
    }

    @Test
    void unBorradorSeGuardaSinNumeracionNiCompradorNiEstadoSri() {
        // Regla explicita: ningun NOT NULL fiscal prematuro puede impedir
        // guardar un borrador incompleto.
        Factura borrador = facturaRepository.save(Factura.builder()
                .usuario(usuario)
                .fechaEmision(LocalDate.of(2026, 7, 1))
                .build());

        recargar();

        Factura leido = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(leido.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(leido.getClaveAcceso()).isNull();
        assertThat(leido.getSecuencial()).isNull();
        assertThat(leido.getAmbiente()).isNull();
        assertThat(leido.getPuntoEmision()).isNull();
        assertThat(leido.getMascota()).isNull();
        assertThat(leido.getCompradorIdentificacion()).isNull();
        assertThat(leido.getEstadoRecepcion()).isNull();
        assertThat(leido.getEstadoAutorizacion()).isNull();
        assertThat(leido.getNumeroAutorizacion()).isNull();
        // Los totales de un borrador vacio son cero, no desconocidos.
        assertThat(leido.getImporteTotal()).isEqualByComparingTo("0.00");
        assertThat(leido.getIntentosAutorizacion()).isZero();
    }

    @Test
    void losEstadosDelSriSeGuardanComoTextoYSeRecargan() {
        Factura factura = facturaBorrador();
        factura.setEstado(EstadoFactura.AUTORIZADA);
        factura.setEstadoRecepcion(EstadoRecepcionSri.RECIBIDA);
        factura.setEstadoAutorizacion(EstadoAutorizacionSri.AUT);
        factura.setNumeroAutorizacion("4".repeat(49));
        factura.setIntentosAutorizacion(2);
        Factura guardada = facturaRepository.save(factura);

        recargar();

        Factura leida = facturaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(leida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(leida.getIntentosAutorizacion()).isEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT estado_autorizacion FROM facturas WHERE id = ?",
                String.class, guardada.getId())).isEqualTo("AUT");
    }

    // ==================================================================
    // BYTEA
    // ==================================================================

    @Test
    void elContenidoBinarioVuelveByteAByteIdentico() {
        Factura factura = facturaRepository.save(facturaBorrador());

        // Bytes deliberadamente "dificiles": nulo, secuencia baja, 0xFF, y
        // una secuencia que NO es UTF-8 valido. Si algo por el camino tratase
        // la columna como texto, esto no sobreviviria.
        byte[] original = new byte[256];
        for (int i = 0; i < 256; i++) {
            original[i] = (byte) i;
        }

        FacturaDocumento documento = facturaDocumentoRepository.save(FacturaDocumento.builder()
                .factura(factura)
                .tipo(TipoDocumentoFactura.XML_FIRMADO)
                .contenido(original)
                .sha256("b".repeat(64))
                .bytes(original.length)
                .build());

        recargar();

        FacturaDocumento leido = facturaDocumentoRepository.findById(documento.getId()).orElseThrow();
        assertArrayEquals(original, leido.getContenido());
        assertThat(leido.getBytes()).isEqualTo(256);
        assertThat(leido.getTipo()).isEqualTo(TipoDocumentoFactura.XML_FIRMADO);
        assertThat(leido.getCreadoEn()).isNotNull();

        // Y PostgreSQL lo guardo en la propia tabla como bytea, no como un
        // large object: si fuese oid, la columna contendria un identificador
        // numerico de 4 bytes y no los 256 originales.
        Integer longitudEnLaBd = jdbc.queryForObject(
                "SELECT octet_length(contenido) FROM factura_documentos WHERE id = ?",
                Integer.class, documento.getId());
        assertThat(longitudEnLaBd).isEqualTo(256);

        Integer largeObjects = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_largeobject_metadata", Integer.class);
        assertThat(largeObjects).as("no debe crearse ningun large object").isZero();

        assertThat(facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_FIRMADO))
                .isPresent();
    }

    // ==================================================================
    // JSONB
    // ==================================================================

    @Test
    void elEventoSriGuardaJsonbYLoDevuelveConsultableConOperadoresNativos() {
        Factura factura = facturaRepository.save(facturaBorrador());

        // JSON ficticio: no es una respuesta real del SRI.
        String json = "{\"identificador\":\"35\",\"mensaje\":\"DOCUMENTO DE PRUEBA\"}";

        FacturaEventoSri evento = facturaEventoSriRepository.save(FacturaEventoSri.builder()
                .factura(factura)
                .operacion(OperacionSri.RECEPCION)
                .resultado(ResultadoEventoSri.DEVUELTA)
                .mensajes(json)
                .duracionMs(1234L)
                .intento(1)
                .build());

        recargar();

        FacturaEventoSri leido = facturaEventoSriRepository.findById(evento.getId()).orElseThrow();
        assertThat(leido.getOperacion()).isEqualTo(OperacionSri.RECEPCION);
        assertThat(leido.getResultado()).isEqualTo(ResultadoEventoSri.DEVUELTA);
        assertThat(leido.getDuracionMs()).isEqualTo(1234L);
        assertThat(leido.getIntento()).isEqualTo(1);
        assertThat(leido.getMensajes()).contains("\"identificador\"").contains("35");

        // La prueba de que es JSONB de verdad y no una cadena: PostgreSQL
        // puede navegarlo con sus operadores nativos.
        assertThat(jdbc.queryForObject(
                "SELECT mensajes ->> 'identificador' FROM factura_eventos_sri WHERE id = ?",
                String.class, evento.getId())).isEqualTo("35");
        assertThat(jdbc.queryForObject(
                "SELECT mensajes ->> 'mensaje' FROM factura_eventos_sri WHERE id = ?",
                String.class, evento.getId())).isEqualTo("DOCUMENTO DE PRUEBA");

        // Un evento sin cuerpo (p. ej. un timeout) guarda NULL sin romper nada.
        FacturaEventoSri sinCuerpo = facturaEventoSriRepository.save(FacturaEventoSri.builder()
                .factura(factura)
                .operacion(OperacionSri.AUTORIZACION)
                .resultado(ResultadoEventoSri.TIMEOUT)
                .intento(2)
                .build());
        recargar();
        assertThat(facturaEventoSriRepository.findById(sinCuerpo.getId()).orElseThrow()
                .getMensajes()).isNull();

        assertThat(facturaEventoSriRepository.findAllByFactura_IdOrderByCreadoEnDesc(factura.getId()))
                .hasSize(2);
    }

    // ==================================================================
    // Snapshots
    // ==================================================================

    @Test
    void cambiarElCatalogoDespuesNoAlteraLoQueYaSeFacturo() {
        // 1. Estado inicial del catalogo y de la identidad tributaria.
        DatosFacturacion datos = datosFacturacionRepository.save(DatosFacturacion.builder()
                .usuario(usuario)
                .tipoIdentificacion(TipoIdentificacionSri.CEDULA)
                .identificacion("0000000000")
                .razonSocial("NOMBRE ORIGINAL")
                .direccion("DIRECCION ORIGINAL")
                .predeterminado(true)
                .activo(true)
                .build());

        ConceptoFacturable concepto = conceptoFacturableRepository.save(ConceptoFacturable.builder()
                .codigo("SNAP-" + siguiente())
                .descripcion("DESCRIPCION ORIGINAL")
                .tipo(TipoConceptoFacturable.CONSULTA)
                .precioUnitario(new BigDecimal("10.000000"))
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("90")
                .activo(true)
                .build());

        TarifaImpuesto tarifa = tarifaImpuestoRepository.save(TarifaImpuesto.builder()
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("91")
                .descripcion("Tarifa ficticia snapshot")
                .tarifa(new BigDecimal("12.00"))
                .vigenteDesde(LocalDate.of(2026, 1, 1))
                .activo(true)
                .build());

        EmisorFiscal emisor = emisorFiscalRepository.save(nuevoEmisor());

        // 2. Se emite: los valores se CONGELAN en la factura y su detalle.
        Factura factura = facturaBorrador();
        factura.setEstado(EstadoFactura.EMITIDA);
        factura.setCompradorTipoIdentificacion(datos.getTipoIdentificacion());
        factura.setCompradorIdentificacion(datos.getIdentificacion());
        factura.setCompradorRazonSocial(datos.getRazonSocial());
        factura.setCompradorDireccion(datos.getDireccion());
        factura.setEmisorRuc(emisor.getRuc());
        factura.setEmisorRazonSocial(emisor.getRazonSocial());
        factura.setEmisorDireccionMatriz(emisor.getDireccionMatriz());

        factura.agregarDetalle(FacturaDetalle.builder()
                .linea(1)
                .conceptoFacturable(concepto)
                .codigoPrincipal(concepto.getCodigo())
                .descripcion(concepto.getDescripcion())
                .cantidad(new BigDecimal("1.000000"))
                .precioUnitario(concepto.getPrecioUnitario())
                .descuento(new BigDecimal("0.00"))
                .precioTotalSinImpuesto(new BigDecimal("10.00"))
                .impuestoCodigo(CodigoImpuestoSri.IVA)
                .impuestoCodigoPorcentaje("91")
                .impuestoTarifa(tarifa.getTarifa())
                .baseImponible(new BigDecimal("10.00"))
                .impuestoValor(new BigDecimal("1.20"))
                .build());

        Factura emitida = facturaRepository.save(factura);
        recargar();

        // 3. TODO cambia despues: el cliente se muda, sube el precio, cambia
        //    la tarifa y el emisor se renombra.
        DatosFacturacion datosLeidos = datosFacturacionRepository.findById(datos.getId()).orElseThrow();
        datosLeidos.setRazonSocial("NOMBRE CAMBIADO");
        datosLeidos.setDireccion("DIRECCION CAMBIADA");
        datosFacturacionRepository.save(datosLeidos);

        ConceptoFacturable conceptoLeido = conceptoFacturableRepository.findById(concepto.getId()).orElseThrow();
        conceptoLeido.setDescripcion("DESCRIPCION CAMBIADA");
        conceptoLeido.setPrecioUnitario(new BigDecimal("99.000000"));
        conceptoFacturableRepository.save(conceptoLeido);

        TarifaImpuesto tarifaLeida = tarifaImpuestoRepository.findById(tarifa.getId()).orElseThrow();
        tarifaLeida.setTarifa(new BigDecimal("20.00"));
        tarifaImpuestoRepository.save(tarifaLeida);

        EmisorFiscal emisorLeido = emisorFiscalRepository.findById(emisor.getId()).orElseThrow();
        emisorLeido.setRazonSocial("EMISOR RENOMBRADO");
        emisorLeido.setDireccionMatriz("OTRA DIRECCION");
        emisorFiscalRepository.save(emisorLeido);

        recargar();

        // 4. La factura emitida no se entera de nada.
        Factura releida = facturaRepository.findById(emitida.getId()).orElseThrow();
        assertThat(releida.getCompradorRazonSocial()).isEqualTo("NOMBRE ORIGINAL");
        assertThat(releida.getCompradorDireccion()).isEqualTo("DIRECCION ORIGINAL");
        assertThat(releida.getEmisorRazonSocial()).isEqualTo(emisor.getRazonSocial());
        assertThat(releida.getEmisorRazonSocial()).isNotEqualTo("EMISOR RENOMBRADO");
        assertThat(releida.getEmisorDireccionMatriz()).isNotEqualTo("OTRA DIRECCION");

        FacturaDetalle detalleReleido = releida.getDetalles().get(0);
        assertThat(detalleReleido.getDescripcion()).isEqualTo("DESCRIPCION ORIGINAL");
        assertThat(detalleReleido.getPrecioUnitario()).isEqualByComparingTo("10.000000");
        assertThat(detalleReleido.getImpuestoTarifa()).isEqualByComparingTo("12.00");

        // La relacion sigue ahi como trazabilidad, y SI refleja el cambio:
        // esa es justamente la diferencia entre contexto y snapshot.
        assertThat(detalleReleido.getConceptoFacturable().getDescripcion())
                .isEqualTo("DESCRIPCION CAMBIADA");
        assertThat(detalleReleido.getConceptoFacturable().getPrecioUnitario())
                .isEqualByComparingTo("99.000000");
    }

    // ==================================================================
    // Ayudas
    // ==================================================================

    private Factura facturaBorrador() {
        return Factura.builder()
                .usuario(usuario)
                .fechaEmision(LocalDate.of(2026, 6, 15))
                .estado(EstadoFactura.BORRADOR)
                .build();
    }

    private EmisorFiscal nuevoEmisor() {
        return EmisorFiscal.builder()
                .ruc(rucFicticio())
                .razonSocial("EMISOR FICTICIO " + siguiente())
                .direccionMatriz("Direccion ficticia")
                .obligadoContabilidad(false)
                .rimpe(false)
                .activo(true)
                .build();
    }

    /**
     * RUC ficticio: solo cumple la forma de 13 digitos que exige la BD. No es
     * ni pretende ser un RUC real, y se genera por contador (no al azar) para
     * que un fallo sea siempre reproducible.
     */
    private String rucFicticio() {
        return String.valueOf(9_000_000_000_000L + siguiente());
    }
}
