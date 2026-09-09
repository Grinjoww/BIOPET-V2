package com.biopet.audit.service;

import com.biopet.audit.dto.AuditoriaEventoResponse;
import com.biopet.audit.dto.AuditoriaOpcionesResponse;
import com.biopet.audit.entity.AuditoriaEvento;
import com.biopet.audit.repository.AuditoriaEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * UNICO punto de escritura y lectura de auditoria_evento (V10). Reemplaza
 * la "auditoria" que hasta ahora solo eran lineas de log de
 * AuthenticationAuditService (com.biopet.security) -esa clase se mantiene
 * (sigue logueando, otros tests dependen de ese formato) pero ahora
 * TAMBIEN llama aqui para persistir el mismo evento, sin duplicar logica:
 * ver AuthenticationAuditService.formatearYRegistrar.
 *
 * <p>Regla que gobierna toda la clase: {@link #registrar} JAMAS propaga una
 * excepcion. Un fallo al guardar un evento de auditoria no debe tumbar la
 * operacion real de BIOPET que lo origino (la respuesta HTTP, el respaldo,
 * el login...) -mismo criterio que RespaldoEjecucionService aplica a los
 * fallos de pg_dump.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private static final int LONGITUD_MAXIMA_CORTA = 60;
    private static final int LONGITUD_MAXIMA_EMAIL = 255;
    private static final int LONGITUD_MAXIMA_RECURSO = 255;
    private static final int LONGITUD_MAXIMA_RESULTADO = 20;

    /**
     * Limites que reemplazan un "sin filtro" de fecha. Ver el javadoc de
     * AuditoriaEventoRepository.buscar: PostgreSQL no logra inferir el tipo
     * de un parametro TIMESTAMPTZ ligado como NULL en el patron
     * "(:param is null or columna >= :param)" -a diferencia de un VARCHAR,
     * donde si lo infiere sin problema-, asi que "sin filtro" nunca se
     * traduce a un NULL real hacia la consulta: se traduce a un rango
     * amplio pero siempre no-nulo. 9999 es un limite arbitrario muy lejano
     * -jamas se alcanza con datos reales- y dentro del rango valido de
     * TIMESTAMPTZ (hasta el ano 294276).
     */
    private static final Instant DESDE_POR_DEFECTO = Instant.EPOCH;
    private static final Instant HASTA_POR_DEFECTO = Instant.parse("9999-12-31T23:59:59Z");

    private final AuditoriaEventoRepository repository;
    private final Clock clock;

    @Autowired
    public AuditoriaService(AuditoriaEventoRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AuditoriaService(AuditoriaEventoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Registra un evento. Nunca lanza: cualquier fallo (base de datos caida,
     * violacion de una restriccion inesperada...) queda solo en el log del
     * servidor.
     *
     * @param usuarioEmail email del usuario autenticado, o {@code null} si el
     *                      evento no tiene un usuario identificable (p.ej.
     *                      un respaldo automatico, o un intento de login con
     *                      una cuenta que no existe).
     * @param accion        metodo HTTP (POST/PUT/PATCH/DELETE) para eventos
     *                      genericos, o el nombre del evento de negocio
     *                      (LOGIN_SUCCESS, BACKUP_COMPLETED...).
     * @param modulo        primer segmento de la ruta, o "auth"/"admin/respaldos".
     * @param recurso       ruta completa u otro detalle corto NO sensible.
     *                      Nunca el cuerpo de la peticion, nunca un secreto.
     * @param metodoHttp    metodo HTTP real si aplica, o {@code null}.
     * @param resultado     SUCCESS / FAILURE / BLOCKED.
     * @param statusHttp    codigo de estado HTTP real si aplica, o {@code null}.
     */
    public void registrar(String usuarioEmail, String accion, String modulo, String recurso,
                           String metodoHttp, String resultado, Integer statusHttp) {
        try {
            AuditoriaEvento evento = AuditoriaEvento.builder()
                    .fechaHora(clock.instant())
                    .usuarioEmail(recortar(usuarioEmail, LONGITUD_MAXIMA_EMAIL))
                    .accion(recortar(accion, LONGITUD_MAXIMA_CORTA))
                    .modulo(recortar(modulo, LONGITUD_MAXIMA_CORTA))
                    .recurso(recortar(recurso, LONGITUD_MAXIMA_RECURSO))
                    .metodoHttp(metodoHttp)
                    .resultado(recortar(resultado, LONGITUD_MAXIMA_RESULTADO))
                    .statusHttp(statusHttp)
                    .build();
            repository.save(evento);
        } catch (Exception e) {
            log.error("No se pudo registrar el evento de auditoria (accion={}, modulo={}); "
                    + "la operacion original continua sin verse afectada.", accion, modulo, e);
        }
    }

    /** Siempre paginado: nunca se carga el historial completo en memoria. */
    @Transactional(readOnly = true)
    public Page<AuditoriaEventoResponse> buscar(String usuarioEmail, String accion, String modulo,
                                                 Instant desde, Instant hasta, Pageable pageable) {
        Instant desdeEfectivo = desde != null ? desde : DESDE_POR_DEFECTO;
        Instant hastaEfectiva = hasta != null ? hasta : HASTA_POR_DEFECTO;
        return repository.buscar(vacioComoNulo(usuarioEmail), vacioComoNulo(accion), vacioComoNulo(modulo),
                        desdeEfectivo, hastaEfectiva, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditoriaOpcionesResponse opcionesFiltro() {
        return new AuditoriaOpcionesResponse(
                repository.usuariosDistintos(), repository.accionesDistintas(), repository.modulosDistintos());
    }

    /** El frontend envia "" (opcion "Todos") en vez de omitir el parametro; se trata igual que ausente. */
    private String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private String recortar(String valor, int longitudMaxima) {
        if (valor == null) return null;
        String sinControl = valor.replaceAll("\\p{Cntrl}", "");
        return sinControl.length() > longitudMaxima ? sinControl.substring(0, longitudMaxima) : sinControl;
    }

    private AuditoriaEventoResponse toResponse(AuditoriaEvento e) {
        return new AuditoriaEventoResponse(
                e.getId(), e.getFechaHora(), e.getUsuarioEmail(), e.getAccion(), e.getModulo(),
                e.getRecurso(), e.getMetodoHttp(), e.getResultado(), e.getStatusHttp()
        );
    }
}
