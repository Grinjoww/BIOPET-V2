package com.biopet.config;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-1: cobertura del bootstrap opcional del ADMIN semilla (ver
 * DataInitializer). Invoca directamente el CommandLineRunner devuelto por el
 * @Bean -no requiere levantar el ApplicationContext completo, porque los
 * CommandLineRunner solo se auto-ejecutan vía SpringApplication.run, no al
 * refrescar un contexto de test.
 */
class DataInitializerTest {

    private static final String EMAIL = "admin@biopet-v2.ec";
    private static final String PASSWORD = "UnaClaveFuerteDeVerdad*2026";
    private static final String NOMBRE = "Administrador BIOPET";

    private final DataInitializer dataInitializer = new DataInitializer();

    @Test
    void seedDeshabilitado_noCreaAdmin() throws Exception {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);

        CommandLineRunner runner = dataInitializer.seedAdmin(repo, enc, false, EMAIL, PASSWORD, NOMBRE);
        runner.run();

        verify(repo, never()).existsByEmail(any());
        verify(repo, never()).save(any());
    }

    @Test
    void seedHabilitadoSinPassword_falla() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);

        CommandLineRunner runner = dataInitializer.seedAdmin(repo, enc, true, EMAIL, "", NOMBRE);

        assertThrows(IllegalStateException.class, runner::run);
        verify(repo, never()).save(any());
    }

    @Test
    void seedHabilitadoSinEmail_falla() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);

        CommandLineRunner runner = dataInitializer.seedAdmin(repo, enc, true, "  ", PASSWORD, NOMBRE);

        assertThrows(IllegalStateException.class, runner::run);
        verify(repo, never()).save(any());
    }

    @Test
    void seedHabilitadoConCredenciales_creaAdmin() throws Exception {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(repo.existsByEmail(EMAIL)).thenReturn(false);
        when(enc.encode(PASSWORD)).thenReturn("hash-bcrypt-simulado");

        CommandLineRunner runner = dataInitializer.seedAdmin(repo, enc, true, EMAIL, PASSWORD, NOMBRE);

        assertDoesNotThrow(() -> runner.run());

        verify(repo).save(argThatUsuarioAdminValido());
    }

    @Test
    void seedConAdminExistente_noReseteaPassword() throws Exception {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(repo.existsByEmail(EMAIL)).thenReturn(true);

        CommandLineRunner runner = dataInitializer.seedAdmin(repo, enc, true, EMAIL, PASSWORD, NOMBRE);
        runner.run();

        verify(repo, never()).save(any());
        verify(enc, never()).encode(any());
    }

    private Usuario argThatUsuarioAdminValido() {
        return org.mockito.ArgumentMatchers.argThat(usuario ->
                usuario != null
                        && EMAIL.equals(usuario.getEmail())
                        && "hash-bcrypt-simulado".equals(usuario.getPasswordHash())
                        && usuario.getRol() == Rol.ROLE_ADMIN
                        && usuario.isActivo()
        );
    }
}
