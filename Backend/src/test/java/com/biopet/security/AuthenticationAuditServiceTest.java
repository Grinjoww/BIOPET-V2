package com.biopet.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationAuditServiceTest {

    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;
    private AuthenticationAuditService service;

    @BeforeEach
    void setUp() {
        logbackLogger = (Logger) LoggerFactory.getLogger(AuthenticationAuditService.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        service = new AuthenticationAuditService();
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void loginExitosoRegistraEventoEstructurado() {
        service.loginExitoso("203.0.113.10", "usuario@biopet.com");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("AUTH_AUDIT"));
        assertTrue(mensaje.contains("timestamp="));
        assertTrue(mensaje.contains("event=LOGIN_SUCCESS"));
        assertTrue(mensaje.contains("result=SUCCESS"));
        assertTrue(mensaje.contains("ip=203.0.113.10"));
        assertTrue(mensaje.contains("subject=usuario@biopet.com"));
        assertEquals(Level.INFO, evento.getLevel());
    }

    @Test
    void loginFallidoRegistraEventoWarn() {
        service.loginFallido("203.0.113.11", "usuario@biopet.com");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("event=LOGIN_FAILURE"));
        assertTrue(mensaje.contains("result=FAILURE"));
        assertEquals(Level.WARN, evento.getLevel());
    }

    @Test
    void loginBloqueadoRegistraEventoWarn() {
        service.loginBloqueado("203.0.113.12", "usuario@biopet.com");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("event=LOGIN_RATE_LIMITED"));
        assertTrue(mensaje.contains("result=BLOCKED"));
        assertEquals(Level.WARN, evento.getLevel());
    }

    @Test
    void refreshExitosoRegistraEventoInfo() {
        service.refreshExitoso("203.0.113.20", "usuario@biopet.com");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("AUTH_AUDIT"));
        assertTrue(mensaje.contains("timestamp="));
        assertTrue(mensaje.contains("event=REFRESH_SUCCESS"));
        assertTrue(mensaje.contains("result=SUCCESS"));
        assertTrue(mensaje.contains("ip=203.0.113.20"));
        assertTrue(mensaje.contains("subject=usuario@biopet.com"));
        assertEquals(Level.INFO, evento.getLevel());
        assertFalse(mensaje.contains("access_token"));
        assertFalse(mensaje.contains("refresh_token"));
        assertFalse(mensaje.contains("Bearer"));
    }

    @Test
    void refreshFallidoRegistraEventoWarn() {
        service.refreshFallido("203.0.113.21", "unknown");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("AUTH_AUDIT"));
        assertTrue(mensaje.contains("timestamp="));
        assertTrue(mensaje.contains("event=REFRESH_FAILURE"));
        assertTrue(mensaje.contains("result=FAILURE"));
        assertTrue(mensaje.contains("ip=203.0.113.21"));
        assertTrue(mensaje.contains("subject=unknown"));
        assertEquals(Level.WARN, evento.getLevel());
        assertFalse(mensaje.contains("access_token"));
        assertFalse(mensaje.contains("refresh_token"));
        assertFalse(mensaje.contains("Bearer"));
    }

    @Test
    void logoutExitosoRegistraEventoInfo() {
        service.logoutExitoso("203.0.113.22", "usuario@biopet.com");

        ILoggingEvent evento = ultimoEvento();
        String mensaje = evento.getFormattedMessage();

        assertTrue(mensaje.contains("AUTH_AUDIT"));
        assertTrue(mensaje.contains("timestamp="));
        assertTrue(mensaje.contains("event=LOGOUT_SUCCESS"));
        assertTrue(mensaje.contains("result=SUCCESS"));
        assertTrue(mensaje.contains("ip=203.0.113.22"));
        assertTrue(mensaje.contains("subject=usuario@biopet.com"));
        assertEquals(Level.INFO, evento.getLevel());
        assertFalse(mensaje.contains("access_token"));
        assertFalse(mensaje.contains("refresh_token"));
        assertFalse(mensaje.contains("Bearer"));
    }

    @Test
    void valoresNulosSeNormalizanComoUnknown() {
        service.loginFallido(null, null);

        String mensaje = ultimoEvento().getFormattedMessage();

        assertTrue(mensaje.contains("ip=unknown"));
        assertTrue(mensaje.contains("subject=unknown"));
    }

    @Test
    void eliminaCaracteresDeControlParaEvitarLogForging() {
        String ipMaliciosa = "203.0.113.13\r\nAUTH_AUDIT event=LOGIN_SUCCESS result=SUCCESS";
        String subjectMalicioso = "ataque@biopet.com\ninyectado\tvalor";

        service.loginFallido(ipMaliciosa, subjectMalicioso);

        String mensaje = ultimoEvento().getFormattedMessage();

        assertFalse(mensaje.contains("\n"));
        assertFalse(mensaje.contains("\r"));
        assertFalse(mensaje.contains("\t"));
        assertEquals(1, mensaje.split("\\R", -1).length);
    }

    @Test
    void noRegistraDatosSensibles() {
        service.loginFallido("203.0.113.14", "usuario@biopet.com");

        String mensaje = ultimoEvento().getFormattedMessage();

        assertFalse(mensaje.contains("ClaveSecreta123*"));
        assertFalse(mensaje.contains("access_token"));
        assertFalse(mensaje.contains("refresh_token"));
        assertFalse(mensaje.contains("Bearer"));
    }

    private ILoggingEvent ultimoEvento() {
        List<ILoggingEvent> eventos = appender.list;
        assertFalse(eventos.isEmpty(), "No se registró ningún evento en el logger");
        return eventos.get(eventos.size() - 1);
    }
}
