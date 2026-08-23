package com.biopet.security;

import com.biopet.service.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtCookieService jwtCookieService;
    private final AuthenticationAuditService authenticationAuditService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenBlacklistService blacklistService,
                                   UserDetailsServiceImpl userDetailsService,
                                   JwtCookieService jwtCookieService,
                                   AuthenticationAuditService authenticationAuditService) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.userDetailsService = userDetailsService;
        this.jwtCookieService = jwtCookieService;
        this.authenticationAuditService = authenticationAuditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> resolvedToken = resolveToken(request);
        if (resolvedToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolvedToken.get();
        try {
            String email = jwtService.extractEmail(token);
            String jti = jwtService.extractJti(token);
            boolean esAccessToken = jwtService.isAccessToken(token);
            boolean revocado = esAccessToken && blacklistService.isRevoked(jti);

            if (revocado) {
                authenticationAuditService.tokenRevocado(request.getRemoteAddr(), email);
            }

            boolean tokenValido = esAccessToken && !revocado;
            if (email != null && tokenValido && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            // UsernameNotFoundException: la cuenta paso a activo=false (o fue
            // eliminada) DESPUES de emitido el JWT -findByEmailAndActivoTrue,
            // vuelto a ejecutar en cada request via loadUserByUsername(), ya
            // no la encuentra- aunque el token siga siendo criptograficamente
            // valido y no haya expirado. Sin este catch, UsernameNotFoundException
            // (subclase de AuthenticationException) escapa de este filtro sin
            // capturar: ExceptionTranslationFilter solo atrapa excepciones de
            // filtros POSTERIORES en la cadena (este filtro corre antes,
            // via addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)),
            // asi que nunca llega a traducirla. Se trata igual que un JWT
            // invalido: se limpia el contexto y se deja que la cadena siga sin
            // autenticacion, para que ProblemAuthenticationEntryPoint responda
            // con el mismo 401 ProblemDetail que los demas casos "no autenticado".
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        Optional<String> cookieToken = jwtCookieService.readAccessToken(request);
        if (cookieToken.isPresent()) {
            return cookieToken;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }

        return Optional.empty();
    }
}
