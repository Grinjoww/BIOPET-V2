package com.biopet.security;

import com.biopet.audit.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Auditoria de eventos de autenticacion. Sigue logueando exactamente igual
 * que antes (AuthenticationAuditServiceTest verifica ese formato tal cual)
 * y AHORA ADEMAS persiste el mismo evento en auditoria_evento (V10) a
 * traves de AuditoriaService -reutilizando estos 8 metodos y sus 2 sitios
 * de llamada existentes (AuthService, JwtAuthenticationFilter) sin
 * modificarlos ni duplicar la logica de "que es un evento de auth" en otro
 * lugar. Esto es literalmente la reutilizacion pedida: "revisa que
 * mecanismo de auditoria ya existe... y reutilizalo".
 *
 * <p>{@code auditoriaService} puede ser {@code null} (constructor sin
 * argumentos, usado por AuthenticationAuditServiceTest y por cualquier
 * codigo que construya esta clase fuera de Spring): en ese caso solo se
 * loguea, igual que se comportaba esta clase antes de esta fase.
 */
@Component
public class AuthenticationAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationAuditService.class);

    private static final String DESCONOCIDO = "unknown";
    private static final int LONGITUD_MAXIMA = 200;
    private static final String MODULO_AUTH = "auth";

    private static final String EVENTO_LOGIN_EXITOSO = "LOGIN_SUCCESS";
    private static final String EVENTO_LOGIN_FALLIDO = "LOGIN_FAILURE";
    private static final String EVENTO_LOGIN_BLOQUEADO = "LOGIN_RATE_LIMITED";
    private static final String EVENTO_REGISTRO_BLOQUEADO = "REGISTRO_RATE_LIMITED";
    private static final String EVENTO_REFRESH_EXITOSO = "REFRESH_SUCCESS";
    private static final String EVENTO_REFRESH_FALLIDO = "REFRESH_FAILURE";
    private static final String EVENTO_LOGOUT_EXITOSO = "LOGOUT_SUCCESS";
    private static final String EVENTO_TOKEN_REVOCADO = "TOKEN_REVOKED";

    private static final String RESULTADO_EXITO = "SUCCESS";
    private static final String RESULTADO_FALLO = "FAILURE";
    private static final String RESULTADO_BLOQUEADO = "BLOCKED";

    private final AuditoriaService auditoriaService;

    /** Sin persistencia -solo log-, para AuthenticationAuditServiceTest y cualquier uso fuera de Spring. */
    public AuthenticationAuditService() {
        this(null);
    }

    // @Autowired explicito: hay un segundo constructor (sin argumentos) y
    // sin esta anotacion Spring usaria ese en vez de este, dejando la
    // persistencia siempre desactivada en produccion.
    @Autowired
    public AuthenticationAuditService(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    public void loginExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_LOGIN_EXITOSO, RESULTADO_EXITO, ip, subject));
        persistir(EVENTO_LOGIN_EXITOSO, RESULTADO_EXITO, subject);
    }

    public void loginFallido(String ip, String subject) {
        logger.warn(formatear(EVENTO_LOGIN_FALLIDO, RESULTADO_FALLO, ip, subject));
        persistir(EVENTO_LOGIN_FALLIDO, RESULTADO_FALLO, subject);
    }

    public void loginBloqueado(String ip, String subject) {
        logger.warn(formatear(EVENTO_LOGIN_BLOQUEADO, RESULTADO_BLOQUEADO, ip, subject));
        persistir(EVENTO_LOGIN_BLOQUEADO, RESULTADO_BLOQUEADO, subject);
    }

    public void registroBloqueado(String ip, String subject) {
        logger.warn(formatear(EVENTO_REGISTRO_BLOQUEADO, RESULTADO_BLOQUEADO, ip, subject));
        persistir(EVENTO_REGISTRO_BLOQUEADO, RESULTADO_BLOQUEADO, subject);
    }

    public void refreshExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_REFRESH_EXITOSO, RESULTADO_EXITO, ip, subject));
        persistir(EVENTO_REFRESH_EXITOSO, RESULTADO_EXITO, subject);
    }

    public void refreshFallido(String ip, String subject) {
        logger.warn(formatear(EVENTO_REFRESH_FALLIDO, RESULTADO_FALLO, ip, subject));
        persistir(EVENTO_REFRESH_FALLIDO, RESULTADO_FALLO, subject);
    }

    public void logoutExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_LOGOUT_EXITOSO, RESULTADO_EXITO, ip, subject));
        persistir(EVENTO_LOGOUT_EXITOSO, RESULTADO_EXITO, subject);
    }

    public void tokenRevocado(String ip, String subject) {
        logger.warn(formatear(EVENTO_TOKEN_REVOCADO, RESULTADO_BLOQUEADO, ip, subject));
        persistir(EVENTO_TOKEN_REVOCADO, RESULTADO_BLOQUEADO, subject);
    }

    private String formatear(String evento, String resultado, String ip, String subject) {
        return "AUTH_AUDIT timestamp=" + Instant.now()
                + " event=" + evento
                + " result=" + resultado
                + " ip=" + normalizar(ip)
                + " subject=" + normalizar(subject);
    }

    /** AuditoriaService.registrar ya nunca lanza; este metodo solo evita la llamada cuando no hay persistencia configurada (tests, uso fuera de Spring). */
    private void persistir(String evento, String resultado, String subject) {
        if (auditoriaService == null) {
            return;
        }
        String email = (subject == null || subject.isBlank() || DESCONOCIDO.equals(subject)) ? null : subject;
        auditoriaService.registrar(email, evento, MODULO_AUTH, null, null, resultado, null);
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return DESCONOCIDO;
        }
        String sinCaracteresDeControl = valor.replaceAll("\\p{Cntrl}", "");
        if (sinCaracteresDeControl.isBlank()) {
            return DESCONOCIDO;
        }
        return sinCaracteresDeControl.length() > LONGITUD_MAXIMA
                ? sinCaracteresDeControl.substring(0, LONGITUD_MAXIMA)
                : sinCaracteresDeControl;
    }
}
