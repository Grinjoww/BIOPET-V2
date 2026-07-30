package com.biopet.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthenticationAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationAuditService.class);

    private static final String DESCONOCIDO = "unknown";
    private static final int LONGITUD_MAXIMA = 200;

    private static final String EVENTO_LOGIN_EXITOSO = "LOGIN_SUCCESS";
    private static final String EVENTO_LOGIN_FALLIDO = "LOGIN_FAILURE";
    private static final String EVENTO_LOGIN_BLOQUEADO = "LOGIN_RATE_LIMITED";
    private static final String EVENTO_REFRESH_EXITOSO = "REFRESH_SUCCESS";
    private static final String EVENTO_REFRESH_FALLIDO = "REFRESH_FAILURE";
    private static final String EVENTO_LOGOUT_EXITOSO = "LOGOUT_SUCCESS";
    private static final String EVENTO_TOKEN_REVOCADO = "TOKEN_REVOKED";

    private static final String RESULTADO_EXITO = "SUCCESS";
    private static final String RESULTADO_FALLO = "FAILURE";
    private static final String RESULTADO_BLOQUEADO = "BLOCKED";

    public void loginExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_LOGIN_EXITOSO, RESULTADO_EXITO, ip, subject));
    }

    public void loginFallido(String ip, String subject) {
        logger.warn(formatear(EVENTO_LOGIN_FALLIDO, RESULTADO_FALLO, ip, subject));
    }

    public void loginBloqueado(String ip, String subject) {
        logger.warn(formatear(EVENTO_LOGIN_BLOQUEADO, RESULTADO_BLOQUEADO, ip, subject));
    }

    public void refreshExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_REFRESH_EXITOSO, RESULTADO_EXITO, ip, subject));
    }

    public void refreshFallido(String ip, String subject) {
        logger.warn(formatear(EVENTO_REFRESH_FALLIDO, RESULTADO_FALLO, ip, subject));
    }

    public void logoutExitoso(String ip, String subject) {
        logger.info(formatear(EVENTO_LOGOUT_EXITOSO, RESULTADO_EXITO, ip, subject));
    }

    public void tokenRevocado(String ip, String subject) {
        logger.warn(formatear(EVENTO_TOKEN_REVOCADO, RESULTADO_BLOQUEADO, ip, subject));
    }

    private String formatear(String evento, String resultado, String ip, String subject) {
        return "AUTH_AUDIT timestamp=" + Instant.now()
                + " event=" + evento
                + " result=" + resultado
                + " ip=" + normalizar(ip)
                + " subject=" + normalizar(subject);
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
