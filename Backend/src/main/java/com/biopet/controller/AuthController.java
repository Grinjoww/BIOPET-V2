package com.biopet.controller;

import com.biopet.dto.*;
import com.biopet.security.JwtCookieService;
import com.biopet.service.AuthService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    public AuthController(AuthService authService, JwtCookieService jwtCookieService) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
    }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponse> registro(@Valid @RequestBody RegistroRequest request,
                                                       HttpServletRequest servletRequest) {
        UsuarioResponse creado = authService.registrar(request, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest servletRequest,
                                                       HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, servletRequest.getRemoteAddr());
        jwtCookieService.addAccessCookie(response, authResponse.accessToken());
        jwtCookieService.addRefreshCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(new AuthSessionResponse(authResponse.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(HttpServletRequest servletRequest,
                                                         HttpServletResponse servletResponse) {
        String refreshToken = jwtCookieService.readRefreshToken(servletRequest).orElse(null);

        AuthResponse authResponse;
        try {
            authResponse = authService.refresh(new RefreshRequest(refreshToken), servletRequest.getRemoteAddr());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Refresh token ausente o inválido");
        }

        jwtCookieService.addAccessCookie(servletResponse, authResponse.accessToken());
        return ResponseEntity.ok(new AuthSessionResponse(authResponse.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest,
                                        HttpServletResponse servletResponse) {
        String accessToken = jwtCookieService.readAccessToken(servletRequest).orElse(null);
        String refreshToken = jwtCookieService.readRefreshToken(servletRequest).orElse(null);
        authService.logout(accessToken, refreshToken, servletRequest.getRemoteAddr());

        jwtCookieService.clearAccessCookie(servletResponse);
        jwtCookieService.clearRefreshCookie(servletResponse);

        return ResponseEntity.noContent().build();
    }
}
