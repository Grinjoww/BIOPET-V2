package com.biopet.controller;

import com.biopet.dto.*;
import com.biopet.security.JwtCookieService;
import com.biopet.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<UsuarioResponse> registro(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        jwtCookieService.addAccessCookie(response, authResponse.accessToken());
        jwtCookieService.addRefreshCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(new AuthSessionResponse(authResponse.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return ResponseEntity.noContent().build();
    }
}
