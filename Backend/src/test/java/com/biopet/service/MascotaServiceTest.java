package com.biopet.service;

import com.biopet.dto.ResumenEspecieResponse;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.ProcedimientoBiopetRepository;
import com.biopet.repository.ResumenEspecie;
import com.biopet.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-3: MascotaService.resumenPorEspecie(...) trataba a cualquier rol distinto
 * de ADMIN como si fuera DUENO, forzando el usuarioId autenticado como
 * duenioId incluso para VETERINARIO/AUXILIAR (que no son dueños), dejando el
 * resumen clínico vacío/incorrecto para esos roles. Cobertura de la matriz de
 * comportamiento esperado por rol, sin tocar el stored procedure.
 */
class MascotaServiceTest {

    private static final String EMAIL = "usuario@biopet.ec";

    private UsuarioRepository usuarioRepository;
    private ProcedimientoBiopetRepository procedimientoBiopetRepository;
    private MascotaService mascotaService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        MascotaRepository mascotaRepository = mock(MascotaRepository.class);
        procedimientoBiopetRepository = mock(ProcedimientoBiopetRepository.class);
        mascotaService = new MascotaService(mascotaRepository, usuarioRepository, procedimientoBiopetRepository);

        when(procedimientoBiopetRepository.resumenPorEspecie(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(resumen("Perro", 3L)));
    }

    @Test
    void adminSinDuenioSolicitaResumenGlobal() {
        autenticarComo(Rol.ROLE_ADMIN, 1L);

        List<ResumenEspecieResponse> resultado = mascotaService.resumenPorEspecie(null, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(isNull());
        assertEquals(1, resultado.size());
    }

    @Test
    void adminConDuenioSolicitaResumenDeEseDuenio() {
        autenticarComo(Rol.ROLE_ADMIN, 1L);

        mascotaService.resumenPorEspecie(42L, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(eq(42L));
    }

    @Test
    void veterinarioSiempreObtieneResumenGlobalIgnorandoDuenioSolicitado() {
        autenticarComo(Rol.ROLE_VETERINARIO, 7L);

        mascotaService.resumenPorEspecie(99L, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(isNull());
    }

    @Test
    void auxiliarSiempreObtieneResumenGlobalIgnorandoDuenioSolicitado() {
        autenticarComo(Rol.ROLE_AUXILIAR, 8L);

        mascotaService.resumenPorEspecie(99L, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(isNull());
    }

    @Test
    void duenoSiempreObtieneSuPropioResumen() {
        autenticarComo(Rol.ROLE_DUENO, 5L);

        mascotaService.resumenPorEspecie(null, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(eq(5L));
    }

    @Test
    void duenoIntentandoSolicitarDuenioAjenoEsIgnoradoYUsaElSuyo() {
        autenticarComo(Rol.ROLE_DUENO, 5L);

        mascotaService.resumenPorEspecie(999L, EMAIL);

        verify(procedimientoBiopetRepository).resumenPorEspecie(eq(5L));
    }

    private void autenticarComo(Rol rol, Long id) {
        Usuario usuario = Usuario.builder()
                .id(id)
                .nombre("Usuario Prueba")
                .email(EMAIL)
                .passwordHash("hash")
                .rol(rol)
                .activo(true)
                .build();
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));
    }

    private ResumenEspecie resumen(String especie, Long total) {
        return new ResumenEspecie() {
            @Override
            public String getEspecie() {
                return especie;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }
}
