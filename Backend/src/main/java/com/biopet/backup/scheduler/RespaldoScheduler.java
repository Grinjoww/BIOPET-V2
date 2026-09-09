package com.biopet.backup.scheduler;

import com.biopet.backup.dto.RespaldoConfiguracionResponse;
import com.biopet.backup.service.RespaldoConfiguracionService;
import com.biopet.backup.service.RespaldoEjecucionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Scheduler LIVIANO: {@code @Scheduled} solo fija cada cuanto se REVISA la
 * configuracion (backup.scheduler-check-interval-ms, 30s por defecto),
 * jamas el intervalo real entre respaldos -ese lo decide el ADMIN en
 * minutos/horas/dias desde la pantalla, y se compara aqui contra
 * proximoRespaldoEn en cada revision. Por eso NO se usa un
 * {@code fixedRate}/{@code fixedDelay} igual al intervalo elegido por el
 * ADMIN: ese valor cambia en caliente (guardar configuracion) y
 * {@code @Scheduled} no puede releerlo sin reiniciar el backend.
 *
 * <p>Si {@code activo=false}, este metodo no hace nada -ni siquiera
 * consulta si "es hora": simplemente vuelve. Cualquier excepcion se atrapa
 * aqui mismo: un fallo de esta revision periodica jamas debe tumbar
 * BIOPET ni impedir que la siguiente revision se ejecute con normalidad.
 */
@Component
public class RespaldoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RespaldoScheduler.class);

    private final RespaldoConfiguracionService configuracionService;
    private final RespaldoEjecucionService ejecucionService;
    private final Clock clock;

    // @Autowired explicito: hay un segundo constructor (con Clock, para
    // tests) y sin esta anotacion Spring exige un constructor sin
    // argumentos en vez de elegir este entre los dos.
    @Autowired
    public RespaldoScheduler(RespaldoConfiguracionService configuracionService,
                              RespaldoEjecucionService ejecucionService) {
        this(configuracionService, ejecucionService, Clock.systemUTC());
    }

    RespaldoScheduler(RespaldoConfiguracionService configuracionService,
                       RespaldoEjecucionService ejecucionService,
                       Clock clock) {
        this.configuracionService = configuracionService;
        this.ejecucionService = ejecucionService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${backup.scheduler-check-interval-ms:30000}")
    public void revisarYEjecutarSiCorresponde() {
        try {
            RespaldoConfiguracionResponse configuracion = configuracionService.obtener();
            if (!configuracion.activo()) {
                return;
            }
            Instant ahora = clock.instant();
            Instant proximo = configuracion.proximoRespaldoEn();
            if (proximo == null || !ahora.isBefore(proximo)) {
                ejecucionService.intentarEjecutarAutomatico();
            }
        } catch (Exception e) {
            log.error("Fallo en la revision periodica de respaldos automaticos; "
                    + "BIOPET continua operando con normalidad.", e);
        }
    }
}
