package com.biopet.facturacion.persistence;

import com.biopet.entity.Cita;
import com.biopet.entity.Consulta;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.entity.Vacuna;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.repository.ConceptoFacturableRepository;
import com.biopet.facturacion.repository.DatosFacturacionRepository;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import com.biopet.facturacion.repository.FacturaDetalleRepository;
import com.biopet.facturacion.repository.FacturaPagoRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import com.biopet.facturacion.repository.TarifaImpuestoRepository;
import com.biopet.facturacion.service.FacturaBorradorService;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.repository.VacunaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.biopet.facturacion.entity.TarifaImpuesto;

/**
 * Fixtures compartidos por las pruebas de facturacion de la Fase 5A.
 *
 * <p>Todos los datos son FICTICIOS: los RUC solo cumplen la forma de 13 digitos
 * que exige la base, y ningun precio ni porcentaje pretende representar una
 * tarifa real. Nada de esto vive en las migraciones, que siguen sin sembrar
 * datos.
 *
 * <p>El contenedor y la base se comparten entre todas las clases del modulo (ver
 * {@link FacturacionPostgresTestBase}) y estas pruebas SI confirman lo que
 * escriben, asi que cada fixture toma sus valores de un contador compartido para
 * no chocar con los de otra clase.
 */
public abstract class FacturaEscenarioTestBase extends FacturacionPostgresTestBase {

    /** Compartido por todas las subclases: la base es una sola. */
    protected static final AtomicInteger SEQ = new AtomicInteger();

    /** Contador aparte para los codigos de porcentaje (ver nuevoCodigoPorcentaje). */
    private static final AtomicInteger SEQ_PORCENTAJE = new AtomicInteger();

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected MascotaRepository mascotaRepository;
    @Autowired protected ConsultaRepository consultaRepository;
    @Autowired protected VacunaRepository vacunaRepository;
    @Autowired protected CitaRepository citaRepository;
    @Autowired protected EmisorFiscalRepository emisorFiscalRepository;
    @Autowired protected PuntoEmisionRepository puntoEmisionRepository;
    @Autowired protected SecuencialEmisionRepository secuencialEmisionRepository;
    @Autowired protected TarifaImpuestoRepository tarifaImpuestoRepository;
    @Autowired protected ConceptoFacturableRepository conceptoFacturableRepository;
    @Autowired protected DatosFacturacionRepository datosFacturacionRepository;
    @Autowired protected FacturaRepository facturaRepository;
    @Autowired protected FacturaDetalleRepository facturaDetalleRepository;
    @Autowired protected FacturaPagoRepository facturaPagoRepository;
    @Autowired protected FacturaBorradorService borradorService;

    protected int siguiente() {
        return SEQ.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Personas y mascotas
    // ------------------------------------------------------------------

    protected Usuario nuevoUsuario() {
        return nuevoUsuario(Rol.ROLE_DUENO);
    }

    protected Usuario nuevoVeterinario() {
        return nuevoUsuario(Rol.ROLE_VETERINARIO);
    }

    protected Usuario nuevoUsuario(Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Fixture " + rol)
                .email("fixture-" + siguiente() + "@biopet.test")
                .passwordHash("x")
                .rol(rol)
                .activo(true)
                .build());
    }

    protected Mascota nuevaMascota(Usuario duenio) {
        return mascotaRepository.save(Mascota.builder()
                .duenio(duenio)
                .nombre("Mascota " + siguiente())
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2021, 1, 1))
                .activo(true)
                .build());
    }

    protected Consulta nuevaConsulta(Mascota mascota, Usuario veterinario) {
        return consultaRepository.save(Consulta.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaConsulta(Instant.parse("2026-09-01T10:00:00Z"))
                .motivo("Control ficticio")
                .activo(true)
                .build());
    }

    protected Vacuna nuevaVacuna(Mascota mascota, Usuario veterinario) {
        return vacunaRepository.save(Vacuna.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .tipo("Vacuna ficticia")
                .fechaAplicacion(LocalDate.of(2026, 9, 1))
                .activo(true)
                .build());
    }

    protected Cita nuevaCita(Mascota mascota, Usuario veterinario, EstadoCita estado) {
        return citaRepository.save(Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(Instant.parse("2026-09-01T09:00:00Z"))
                .estado(estado)
                .motivo("Cita ficticia")
                .activo(true)
                .build());
    }

    // ------------------------------------------------------------------
    // Configuracion fiscal
    // ------------------------------------------------------------------

    protected EmisorFiscal nuevoEmisor() {
        return nuevoEmisor(true);
    }

    protected EmisorFiscal nuevoEmisor(boolean activo) {
        return emisorFiscalRepository.save(EmisorFiscal.builder()
                .ruc(String.valueOf(6_000_000_000_000L + siguiente()))
                .razonSocial("EMISOR FICTICIO " + siguiente())
                .nombreComercial("NOMBRE COMERCIAL FICTICIO")
                .direccionMatriz("Direccion matriz ficticia")
                .obligadoContabilidad(true)
                .contribuyenteEspecial("12345")
                .rimpe(false)
                .agenteRetencionResolucion("RES-FICTICIA")
                .activo(activo)
                .build());
    }

    protected PuntoEmision nuevoPunto(EmisorFiscal emisor) {
        return nuevoPunto(emisor, true);
    }

    protected PuntoEmision nuevoPunto(EmisorFiscal emisor, boolean activo) {
        return puntoEmisionRepository.save(PuntoEmision.builder()
                .emisorFiscal(emisor)
                .establecimiento(String.format("%03d", siguiente() % 1000))
                .puntoEmision("001")
                .direccionEstablecimiento("Sucursal ficticia")
                .activo(activo)
                .build());
    }

    protected SecuencialEmision nuevoContador(PuntoEmision punto, AmbienteSri ambiente, long ultimo) {
        return secuencialEmisionRepository.save(SecuencialEmision.builder()
                .puntoEmision(punto)
                .ambiente(ambiente)
                .ultimoSecuencial(ultimo)
                .build());
    }

    /**
     * Cada grupo de tarifas usa su propio codigo de porcentaje para no chocar
     * con el de otro test: la clave unica de tarifa_impuesto es
     * (codigo_impuesto, codigo_porcentaje, vigente_desde), y varios tests usan
     * la misma fecha de inicio de vigencia.
     *
     * <p>Contador PROPIO, no el general: la columna admite 2 digitos, asi que
     * solo hay 100 valores y gastarlos con cada usuario o mascota que se crea
     * los agotaria en cuanto todas las clases corren juntas. Si algun dia se
     * pasa de 100 grupos, el fallo debe decir esto y no una violacion de
     * constraint dificil de atribuir.
     */
    protected String nuevoCodigoPorcentaje() {
        int grupo = SEQ_PORCENTAJE.getAndIncrement();
        if (grupo > 99) {
            throw new IllegalStateException(
                    "Se agotaron los codigos de porcentaje de prueba (100). Reutilice grupos de "
                            + "tarifas entre tests o varie vigente_desde.");
        }
        return String.valueOf(grupo);
    }

    protected TarifaImpuesto nuevaTarifa(String codigoPorcentaje, String tarifa,
                                         LocalDate desde, LocalDate hasta) {
        return tarifaImpuestoRepository.save(TarifaImpuesto.builder()
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje(codigoPorcentaje)
                .descripcion("Tarifa ficticia " + codigoPorcentaje)
                .tarifa(new BigDecimal(tarifa))
                .vigenteDesde(desde)
                .vigenteHasta(hasta)
                .activo(true)
                .build());
    }

    protected ConceptoFacturable nuevoConcepto(String codigoPorcentaje, String precio) {
        return nuevoConcepto(codigoPorcentaje, precio, TipoConceptoFacturable.CONSULTA, true);
    }

    protected ConceptoFacturable nuevoConcepto(String codigoPorcentaje, String precio,
                                               TipoConceptoFacturable tipo, boolean activo) {
        return conceptoFacturableRepository.save(ConceptoFacturable.builder()
                .codigo("CPT-" + siguiente())
                .descripcion("Concepto ficticio " + tipo)
                .tipo(tipo)
                .precioUnitario(new BigDecimal(precio))
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje(codigoPorcentaje)
                .activo(activo)
                .build());
    }

    protected DatosFacturacion nuevosDatos(Usuario usuario) {
        return nuevosDatos(usuario, TipoIdentificacionSri.CEDULA, "0000000000", "PERSONA FICTICIA");
    }

    protected DatosFacturacion nuevosDatos(Usuario usuario, TipoIdentificacionSri tipo,
                                           String identificacion, String razonSocial) {
        return datosFacturacionRepository.save(DatosFacturacion.builder()
                .usuario(usuario)
                .tipoIdentificacion(tipo)
                .identificacion(identificacion)
                .razonSocial(razonSocial)
                .direccion("Direccion ficticia del comprador")
                .telefono("0999999999")
                .emailFacturacion("comprador-" + siguiente() + "@biopet.test")
                .predeterminado(false)
                .activo(true)
                .build());
    }

    // ------------------------------------------------------------------
    // Lecturas directas (evitan proxies LAZY fuera de transaccion)
    // ------------------------------------------------------------------

    protected long ultimoSecuencial(PuntoEmision punto, AmbienteSri ambiente) {
        return jdbc.queryForObject(
                "SELECT ultimo_secuencial FROM secuencial_emision "
                        + "WHERE punto_emision_id = ? AND ambiente = ?",
                Long.class, punto.getId(), Short.valueOf(ambiente.codigo()));
    }

    protected String columnaFactura(Long facturaId, String columna) {
        return jdbc.queryForObject(
                "SELECT " + columna + "::text FROM facturas WHERE id = ?", String.class, facturaId);
    }
}
