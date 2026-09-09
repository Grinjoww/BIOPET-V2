package com.biopet.audit.service;

import com.biopet.audit.dto.AuditoriaOpcionesResponse;
import com.biopet.audit.entity.AuditoriaEvento;
import com.biopet.audit.repository.AuditoriaEventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "La auditoria NO debe hacer fallar una operacion normal de BIOPET si el
 * registro del evento falla": el test mas importante de esta clase es
 * fallaAlGuardarNuncaPropagaLaExcepcion.
 */
class AuditoriaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-08T12:00:00Z");

    private AuditoriaEventoRepository repository;
    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditoriaEventoRepository.class);
        service = new AuditoriaService(repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registrarGuardaTodosLosCamposConLaFechaDelReloj() {
        service.registrar("admin@biopet.ec", "POST", "mascotas", "/api/mascotas", "POST", "SUCCESS", 201);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        AuditoriaEvento guardado = captor.getValue();
        assertThat(guardado.getFechaHora()).isEqualTo(AHORA);
        assertThat(guardado.getUsuarioEmail()).isEqualTo("admin@biopet.ec");
        assertThat(guardado.getAccion()).isEqualTo("POST");
        assertThat(guardado.getModulo()).isEqualTo("mascotas");
        assertThat(guardado.getRecurso()).isEqualTo("/api/mascotas");
        assertThat(guardado.getMetodoHttp()).isEqualTo("POST");
        assertThat(guardado.getResultado()).isEqualTo("SUCCESS");
        assertThat(guardado.getStatusHttp()).isEqualTo(201);
    }

    @Test
    void registrarConUsuarioNuloEsValido() {
        service.registrar(null, "BACKUP_AUTOMATIC_STARTED", "admin/respaldos", "respaldo #1", null, "SUCCESS", null);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioEmail()).isNull();
    }

    @Test
    void fallaAlGuardarNuncaPropagaLaExcepcion() {
        when(repository.save(any())).thenThrow(new RuntimeException("la base de datos no responde"));

        // No debe lanzar: es la regla critica del modulo de auditoria.
        service.registrar("admin@biopet.ec", "DELETE", "usuarios", "/api/usuarios/5", "DELETE", "SUCCESS", 204);
    }

    @Test
    void buscarTraduceCadenasVaciasAFiltroNuloIgualQueOmitido() {
        when(repository.buscar(isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.buscar("", "", "", null, null, PageRequest.of(0, 20));

        // usuarioEmail/accion/modulo: null tal cual (PostgreSQL SI infiere el
        // tipo de un VARCHAR nulo en ese patron). desde/hasta NUNCA se pasan
        // como null a la consulta -ver el javadoc de
        // AuditoriaEventoRepository.buscar: PostgreSQL no logra inferir el
        // tipo de un TIMESTAMPTZ nulo en el patron "is null or columna >=";
        // "sin filtro" se traduce a limites amplios pero siempre no-nulos.
        verify(repository).buscar(isNull(), isNull(), isNull(), eq(Instant.EPOCH),
                eq(Instant.parse("9999-12-31T23:59:59Z")), any());
    }

    @Test
    void buscarPropagaFiltrosNoVaciosTalCual() {
        when(repository.buscar(any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.buscar("admin@biopet.ec", "POST", "mascotas", AHORA.minusSeconds(3600), AHORA, PageRequest.of(0, 20));

        verify(repository).buscar(eq("admin@biopet.ec"), eq("POST"), eq("mascotas"),
                eq(AHORA.minusSeconds(3600)), eq(AHORA), any());
    }

    @Test
    void opcionesFiltroExponeLosTresListadosDistintos() {
        when(repository.usuariosDistintos()).thenReturn(List.of("admin@biopet.ec", "vet@biopet.ec"));
        when(repository.accionesDistintas()).thenReturn(List.of("LOGIN_SUCCESS", "POST"));
        when(repository.modulosDistintos()).thenReturn(List.of("auth", "mascotas"));

        AuditoriaOpcionesResponse opciones = service.opcionesFiltro();

        assertThat(opciones.usuarios()).containsExactly("admin@biopet.ec", "vet@biopet.ec");
        assertThat(opciones.acciones()).containsExactly("LOGIN_SUCCESS", "POST");
        assertThat(opciones.modulos()).containsExactly("auth", "mascotas");
    }

    @Test
    void registrarRecortaCamposDemasiadoLargosSinFallar() {
        String largo = "x".repeat(1000);

        service.registrar(largo, largo, largo, largo, "POST", "SUCCESS", 200);

        var captor = org.mockito.ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsuarioEmail()).hasSize(255);
        assertThat(captor.getValue().getAccion()).hasSize(60);
        assertThat(captor.getValue().getModulo()).hasSize(60);
        assertThat(captor.getValue().getRecurso()).hasSize(255);
    }

    @Test
    void registrarNuncaSeLlamaConTiposIncorrectosDesdeBuscarSinFiltros() {
        when(repository.buscar(any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.buscar(null, null, null, null, null, PageRequest.of(0, 20));
        verify(repository, never()).save(any());
    }
}
