package com.biopet.service;

import com.biopet.entity.Rol;
import com.biopet.dto.*;
import com.biopet.entity.Usuario;
import com.biopet.exception.EmailDuplicadoException;
import com.biopet.exception.RateLimitExcedidoException;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.AuthenticationAuditService;
import com.biopet.security.JwtService;
import com.biopet.security.LoginRateLimiterService;
import com.biopet.security.RegistroRateLimiterService;
import com.biopet.security.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final RegistroRateLimiterService registroRateLimiterService;
    private final AuthenticationAuditService authenticationAuditService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenBlacklistService blacklistService,
                       LoginRateLimiterService loginRateLimiterService,
                       RegistroRateLimiterService registroRateLimiterService,
                       AuthenticationAuditService authenticationAuditService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.loginRateLimiterService = loginRateLimiterService;
        this.registroRateLimiterService = registroRateLimiterService;
        this.authenticationAuditService = authenticationAuditService;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request, String ip) {
        try {
            registroRateLimiterService.verificarPermitidoYRegistrarIntento(ip);
        } catch (RateLimitExcedidoException ex) {
            authenticationAuditService.registroBloqueado(ip, request.email());
            throw ex;
        }

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailDuplicadoException(request.email());
        }
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();
        Usuario guardado = usuarioRepository.save(usuario);
        return toResponse(guardado);
    }

    public AuthResponse login(LoginRequest request, String ip) {
        String emailSolicitado = request.email().toLowerCase();

        try {
            loginRateLimiterService.verificarPermitido(ip);
        } catch (RateLimitExcedidoException ex) {
            authenticationAuditService.loginBloqueado(ip, emailSolicitado);
            throw ex;
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(emailSolicitado, request.password())
            );
        } catch (BadCredentialsException ex) {
            try {
                loginRateLimiterService.registrarFallo(ip);
            } catch (RateLimitExcedidoException limiteExcedido) {
                authenticationAuditService.loginBloqueado(ip, emailSolicitado);
                throw limiteExcedido;
            }
            authenticationAuditService.loginFallido(ip, emailSolicitado);
            throw ex;
        }

        loginRateLimiterService.reiniciar(ip);
        authenticationAuditService.loginExitoso(ip, authentication.getName());

        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(authentication.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no existe"));
        return new AuthResponse(
                jwtService.generateAccessToken(usuario),
                jwtService.generateRefreshToken(usuario),
                jwtService.getExpirationMs() / 1000
        );
    }

    public AuthResponse refresh(RefreshRequest request, String ip) {
        String refreshToken = request.refreshToken();
        String emailVerificado = null;
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new IllegalArgumentException("Refresh token ausente");
            }
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("El token enviado no es un refresh token");
            }
            emailVerificado = jwtService.extractEmail(refreshToken);
            String jti = jwtService.extractJti(refreshToken);
            if (blacklistService.isRevoked(jti)) {
                throw new IllegalArgumentException("Refresh token revocado");
            }
            Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(emailVerificado)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

            authenticationAuditService.refreshExitoso(ip, emailVerificado);
            return new AuthResponse(jwtService.generateAccessToken(usuario), refreshToken, jwtService.getExpirationMs() / 1000);
        } catch (RuntimeException ex) {
            authenticationAuditService.refreshFallido(ip, emailVerificado);
            throw ex;
        }
    }

    public void logout(String accessToken, String refreshToken, String ip) {
        String subjectAccess = revocarYObtenerSubject(accessToken);
        String subjectRefresh = revocarYObtenerSubject(refreshToken);
        String subject = (subjectAccess != null) ? subjectAccess : subjectRefresh;
        authenticationAuditService.logoutExitoso(ip, subject);
    }

    private String revocarYObtenerSubject(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            String jti = jwtService.extractJti(token);
            Instant expiresAt = jwtService.extractExpiration(token);
            blacklistService.revoke(jti, expiresAt);
            return jwtService.extractEmail(token);
        } catch (JwtException | IllegalArgumentException ex) {
            // Token inválido, expirado o no procesable: no se revoca, no se propaga el error.
            return null;
        }
    }

    public UsuarioResponse perfil(String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return toResponse(usuario);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }
}
