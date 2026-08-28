package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.exception.SecuencialAgotadoException;
import com.biopet.facturacion.exception.SecuencialNoConfiguradoException;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas con doble del repository. Deliberadamente pocas: el comportamiento
 * real de {@link SecuencialService} -exclusion mutua, rollback, independencia
 * entre series- solo se puede demostrar contra PostgreSQL, y eso ya lo hacen
 * {@code SecuencialServiceIntegrationTest} y
 * {@code SecuencialConcurrenciaIntegrationTest}.
 *
 * <p>Aqui se comprueba unicamente lo que un mock puede afirmar y la base de
 * datos no: que el servicio NO llega a escribir en los caminos de error. Que el
 * contador quede en el mismo valor (lo que verifica la integracion) es
 * compatible con haber intentado un UPDATE inutil; que no se invoque
 * {@code saveAndFlush} es una afirmacion mas fuerte.
 */
class SecuencialServiceTest {

    private static final long PUNTO = 42L;

    private SecuencialEmisionRepository repository;
    private SecuencialService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecuencialEmisionRepository.class);
        service = new SecuencialService(repository);
    }

    @Test
    void conArgumentosNulosNiSiquieraConsultaElRepositorio() {
        assertThatThrownBy(() -> service.reservar(null, AmbienteSri.PRUEBAS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reservar(PUNTO, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Sin consulta no hay bloqueo tomado ni transaccion ensuciada.
        verifyNoInteractions(repository);
    }

    @Test
    void sinContadorConfiguradoNoIntentaEscribirNada() {
        when(repository.bloquearPorPuntoEmisionYAmbiente(PUNTO, AmbienteSri.PRODUCCION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservar(PUNTO, AmbienteSri.PRODUCCION))
                .isInstanceOf(SecuencialNoConfiguradoException.class);

        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void enElTopeDeLaSerieFallaSinIntentarElUpdate() {
        SecuencialEmision agotado = SecuencialEmision.builder()
                .ambiente(AmbienteSri.PRUEBAS)
                .ultimoSecuencial(SecuencialService.SECUENCIAL_MAXIMO)
                .build();
        when(repository.bloquearPorPuntoEmisionYAmbiente(PUNTO, AmbienteSri.PRUEBAS))
                .thenReturn(Optional.of(agotado));

        assertThatThrownBy(() -> service.reservar(PUNTO, AmbienteSri.PRUEBAS))
                .isInstanceOf(SecuencialAgotadoException.class);

        verify(repository, never()).saveAndFlush(any());
        // El objeto en memoria tampoco se toca: no hay 1000000000 transitorio
        // que pudiera llegar a persistirse por un flush posterior.
        assertThat(agotado.getUltimoSecuencial()).isEqualTo(SecuencialService.SECUENCIAL_MAXIMO);
    }

    @Test
    void elCaminoFelizIncrementaExactamenteUnoYUsaLaConsultaConBloqueo() {
        SecuencialEmision contador = SecuencialEmision.builder()
                .ambiente(AmbienteSri.PRUEBAS)
                .ultimoSecuencial(7L)
                .build();
        when(repository.bloquearPorPuntoEmisionYAmbiente(PUNTO, AmbienteSri.PRUEBAS))
                .thenReturn(Optional.of(contador));

        long reservado = service.reservar(PUNTO, AmbienteSri.PRUEBAS);

        assertThat(reservado).isEqualTo(8L);
        assertThat(contador.getUltimoSecuencial()).isEqualTo(8L);
        // La lectura pasa por el metodo con @Lock(PESSIMISTIC_WRITE), no por el
        // findBy... sin bloqueo que tambien expone el repository.
        verify(repository).bloquearPorPuntoEmisionYAmbiente(PUNTO, AmbienteSri.PRUEBAS);
        verify(repository, never()).findByPuntoEmision_IdAndAmbiente(any(), any());
        verify(repository).saveAndFlush(contador);
    }
}
