package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.dto.ActualizarPuntoEmisionRequest;
import com.biopet.facturacion.dto.PuntoEmisionRequest;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import com.biopet.facturacion.sri.SriAmbienteProperties;
import com.biopet.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba con dobles de repository de la correccion post-8B: al crear un
 * {@code PuntoEmision}, el {@code SecuencialEmision} del ambiente del servidor
 * se provisiona una sola vez y nunca se toca en edicion ni cambio de estado.
 *
 * <p>La persistencia real (que la fila quede en PostgreSQL con
 * {@code ultimo_secuencial = 0} y respete la restriccion unica
 * {@code (punto_emision, ambiente)}) se prueba en
 * {@code ConfiguracionFiscalControllerIntegrationTest}; aqui solo se fija el
 * CONTRATO del servicio: cuando ya existe una fila para ese par, jamas se
 * llama a {@code save} sobre ella -escenario que la API REST no puede
 * reproducir hoy porque cada {@code POST} crea un punto con id nuevo, pero que
 * el metodo protege igual por si se reutiliza en el futuro-.
 */
class PuntoEmisionServiceTest {

    private PuntoEmisionRepository puntoEmisionRepository;
    private EmisorFiscalRepository emisorFiscalRepository;
    private SecuencialEmisionRepository secuencialEmisionRepository;
    private SriAmbienteProperties ambienteProperties;
    private UsuarioRepository usuarioRepository;
    private PuntoEmisionService service;

    private EmisorFiscal emisor;

    @BeforeEach
    void setUp() {
        puntoEmisionRepository = mock(PuntoEmisionRepository.class);
        emisorFiscalRepository = mock(EmisorFiscalRepository.class);
        secuencialEmisionRepository = mock(SecuencialEmisionRepository.class);
        ambienteProperties = mock(SriAmbienteProperties.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new PuntoEmisionService(
                puntoEmisionRepository, emisorFiscalRepository, secuencialEmisionRepository,
                ambienteProperties, usuarioRepository);

        emisor = EmisorFiscal.builder().id(1L).activo(true).build();
        when(emisorFiscalRepository.findById(1L)).thenReturn(Optional.of(emisor));
        when(puntoEmisionRepository.findByEmisorFiscal_IdAndEstablecimientoAndPuntoEmision(1L, "001", "001"))
                .thenReturn(Optional.empty());
        when(ambienteProperties.getAmbiente()).thenReturn(AmbienteSri.PRUEBAS);
        // save() devuelve la misma entidad con un id, como el JpaRepository real.
        when(puntoEmisionRepository.save(any(PuntoEmision.class))).thenAnswer(inv -> {
            PuntoEmision p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });
    }

    @Test
    void crearProvisionaElSecuencialDelAmbienteDelServidorConCero() {
        when(secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(42L, AmbienteSri.PRUEBAS))
                .thenReturn(Optional.empty());

        service.crear(new PuntoEmisionRequest(1L, "001", "001", "Sucursal"));

        ArgumentCaptor<SecuencialEmision> captor = ArgumentCaptor.forClass(SecuencialEmision.class);
        verify(secuencialEmisionRepository).save(captor.capture());
        SecuencialEmision creado = captor.getValue();
        assertThat(creado.getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
        assertThat(creado.getUltimoSecuencial()).isEqualTo(0L);
        assertThat(creado.getPuntoEmision().getId()).isEqualTo(42L);
    }

    @Test
    void crearNuncaProvisionaUnAmbienteDistintoAlConfiguradoEnElServidor() {
        when(ambienteProperties.getAmbiente()).thenReturn(AmbienteSri.PRODUCCION);
        when(secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(42L, AmbienteSri.PRODUCCION))
                .thenReturn(Optional.empty());

        service.crear(new PuntoEmisionRequest(1L, "001", "001", "Sucursal"));

        ArgumentCaptor<SecuencialEmision> captor = ArgumentCaptor.forClass(SecuencialEmision.class);
        verify(secuencialEmisionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmbiente()).isEqualTo(AmbienteSri.PRODUCCION);
    }

    @Test
    void siYaExisteElSecuencialCrearNuncaLoSobrescribe() {
        SecuencialEmision existente = SecuencialEmision.builder()
                .id(9L).ambiente(AmbienteSri.PRUEBAS).ultimoSecuencial(123L).build();
        when(secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(42L, AmbienteSri.PRUEBAS))
                .thenReturn(Optional.of(existente));

        service.crear(new PuntoEmisionRequest(1L, "001", "001", "Sucursal"));

        // Se comprobo que existia, pero jamas se llamo a save: ni para
        // resetearlo ni para "actualizarlo" con el mismo valor.
        verify(secuencialEmisionRepository, never()).save(any(SecuencialEmision.class));
    }

    @Test
    void actualizarNuncaTocaElSecuencial() {
        PuntoEmision punto = PuntoEmision.builder().id(5L).emisorFiscal(emisor).activo(true).build();
        when(puntoEmisionRepository.findById(5L)).thenReturn(Optional.of(punto));
        when(puntoEmisionRepository.save(any(PuntoEmision.class))).thenReturn(punto);

        service.actualizar(5L, new ActualizarPuntoEmisionRequest("Nueva direccion"));

        verifyNoInteractions(secuencialEmisionRepository);
    }

    @Test
    void cambiarEstadoNuncaTocaElSecuencial() {
        PuntoEmision punto = PuntoEmision.builder().id(5L).emisorFiscal(emisor).activo(true).build();
        when(puntoEmisionRepository.findById(5L)).thenReturn(Optional.of(punto));
        when(puntoEmisionRepository.save(any(PuntoEmision.class))).thenReturn(punto);

        service.cambiarEstado(5L, false);

        verifyNoInteractions(secuencialEmisionRepository);
    }
}
