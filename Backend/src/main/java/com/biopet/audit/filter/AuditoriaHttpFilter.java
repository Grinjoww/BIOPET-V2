package com.biopet.audit.filter;

import com.biopet.audit.service.AuditoriaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Auditoria GENERICA de operaciones autenticadas que cambian estado
 * (POST/PUT/PATCH/DELETE), para TODOS los modulos de BIOPET -incluido
 * facturacion- SIN modificar un solo controller: se registra una vez aqui,
 * en la frontera HTTP, en vez de en cada endpoint.
 *
 * <p>Exclusiones deliberadas (ver el porque de cada una):
 * <ul>
 *   <li>{@code /api/auth/**}: los eventos de autenticacion ya los emite
 *       AuthenticationAuditService (login/logout/refresh/token revocado),
 *       reutilizado tal cual -no se duplican con otro nombre aqui. Ademas,
 *       login/registro no llegan autenticados (no hay JWT todavia), asi que
 *       este filtro no los veria de todos modos.</li>
 *   <li>{@code /api/admin/respaldos/**}: sus eventos de negocio
 *       (BACKUP_CONFIG_UPDATED, BACKUP_MANUAL_REQUESTED...) los emite el
 *       propio modulo de respaldos con mas detalle del que este filtro
 *       generico podria inferir (p.ej. BACKUP_COMPLETED ocurre en segundo
 *       plano, despues de que la peticion HTTP ya respondio 202 -este
 *       filtro nunca podria verlo).</li>
 * </ul>
 *
 * <p>Solo audita peticiones REALMENTE autenticadas -no anonimas-, tal como
 * pide el requisito ("operaciones autenticadas"). Se coloca DESPUES de
 * JwtAuthenticationFilter en la cadena (SecurityConfig) para que el
 * SecurityContext ya este resuelto cuando este filtro lee el usuario.
 *
 * <p>Se ejecuta DESPUES de {@code filterChain.doFilter(...)} a proposito:
 * asi conoce el status HTTP final (200/403/404/422/500...), sin importar en
 * que punto de la cadena -incluida la comprobacion de rol de
 * {@code @PreAuthorize}- se haya decidido.
 */
@Component
public class AuditoriaHttpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaHttpFilter.class);

    private static final Set<String> METODOS_AUDITADOS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String PREFIJO_API = "/api/";
    private static final String PREFIJO_AUTH = "/api/auth/";
    private static final String PREFIJO_RESPALDOS = "/api/admin/respaldos/";

    private final AuditoriaService auditoriaService;

    public AuditoriaHttpFilter(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String metodo = request.getMethod();
        String ruta = request.getRequestURI();
        boolean aplica = METODOS_AUDITADOS.contains(metodo)
                && ruta.startsWith(PREFIJO_API)
                && !ruta.startsWith(PREFIJO_AUTH)
                && !ruta.startsWith(PREFIJO_RESPALDOS);

        filterChain.doFilter(request, response);

        if (!aplica) {
            return;
        }
        try {
            registrarSiCorresponde(metodo, ruta, response.getStatus());
        } catch (Exception e) {
            // Red de seguridad final: un fallo AQUI jamas debe afectar una
            // respuesta que el cliente ya recibio.
            log.error("No se pudo registrar la auditoria generica de {} {}", metodo, ruta, e);
        }
    }

    private void registrarSiCorresponde(String metodo, String ruta, int status) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        boolean autenticado = autenticacion != null
                && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken);
        if (!autenticado) {
            return;
        }
        String modulo = extraerModulo(ruta);
        String resultado = status >= 200 && status < 300 ? "SUCCESS" : "FAILURE";
        auditoriaService.registrar(autenticacion.getName(), metodo, modulo, ruta, metodo, resultado, status);
    }

    /** "/api/mascotas/5" -> "mascotas". "/api/facturacion/conceptos" -> "facturacion". */
    private String extraerModulo(String ruta) {
        String sinPrefijo = ruta.substring(PREFIJO_API.length());
        int barra = sinPrefijo.indexOf('/');
        String primerSegmento = barra >= 0 ? sinPrefijo.substring(0, barra) : sinPrefijo;
        return primerSegmento.isBlank() ? "general" : primerSegmento;
    }
}
