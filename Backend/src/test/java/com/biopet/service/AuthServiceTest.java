package com.biopet.service;

import com.biopet.dto.RegistroRequest;
import com.biopet.dto.UsuarioResponse;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.EmailDuplicadoException;
import com.biopet.exception.RateLimitExcedidoException;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.AuthenticationAuditService;
import com.biopet.security.JwtService;
import com.biopet.security.LoginRateLimiterService;
import com.biopet.security.RegistroRateLimiterService;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-1: cobertura aislada (sin Spring, sin BCrypt real) de que
 * AuthService.registrar(...) consulta el rate limit de registro -con su
 * propio bucket, separado del de login- antes de tocar la base de datos, y
 * de que un bloqueo por rate limit se audita como tal.
 */
class AuthServiceTest {

    private static final String IP = "203.0.113.50";

    private UsuarioRepository usuarioRepository;
    private RegistroRateLimiterService registroRateLimiterService;
    private AuthenticationAuditService authenticationAuditService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        LoginRateLimiterService loginRateLimiterService = mock(LoginRateLimiterService.class);
        registroRateLimiterService = mock(RegistroRateLimiterService.class);
        authenticationAuditService = mock(AuthenticationAuditService.class);

        authService = new AuthService(usuarioRepository, passwordEncoder, authenticationManager, jwtService,
                blacklistService, loginRateLimiterService, registroRateLimiterService, authenticationAuditService);

        when(passwordEncoder.encode(anyString())).thenReturn("hash-simulado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(1L);
            return usuario;
        });
    }

    @Test
    void ipBloqueadaPorRateLimitDeRegistroLanza429YAuditaBloqueoSinTocarLaBd() {
        RegistroRequest request = new RegistroRequest("Dueño Prueba", "dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        doThrow(new RateLimitExcedidoException(60, RateLimitExcedidoException.Recurso.REGISTRO))
                .when(registroRateLimiterService).verificarPermitidoYRegistrarIntento(IP);

        assertThrows(RateLimitExcedidoException.class, () -> authService.registrar(request, IP));

        verify(authenticationAuditService).registroBloqueado(IP, request.email());
        verify(usuarioRepository, never()).existsByEmail(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void ipPermitidaConsultaElRateLimitDeRegistroYCreaElUsuario() {
        RegistroRequest request = new RegistroRequest("Dueño Prueba", "dueno@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        when(usuarioRepository.existsByEmail("dueno@biopet.com")).thenReturn(false);

        UsuarioResponse creado = authService.registrar(request, IP);

        verify(registroRateLimiterService).verificarPermitidoYRegistrarIntento(IP);
        verify(usuarioRepository).save(any(Usuario.class));
        assertEquals("dueno@biopet.com", creado.email());
        assertEquals(Rol.ROLE_DUENO, creado.rol());
    }

    @Test
    void emailDuplicadoIgualmenteConsumeElRateLimitDeRegistro() {
        RegistroRequest request = new RegistroRequest("Dueño Prueba", "existente@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        when(usuarioRepository.existsByEmail("existente@biopet.com")).thenReturn(true);

        assertThrows(EmailDuplicadoException.class, () -> authService.registrar(request, IP));

        verify(registroRateLimiterService).verificarPermitidoYRegistrarIntento(IP);
        verify(usuarioRepository, never()).save(any());
    }
}
