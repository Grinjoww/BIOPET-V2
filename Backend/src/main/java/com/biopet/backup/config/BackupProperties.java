package com.biopet.backup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion del modulo de respaldos ({@code backup.*}).
 *
 * <p>{@code storagePath} es la UNICA clave realmente sensible al entorno: el
 * archivo .dump nunca se guarda en PostgreSQL (regla explicita del modulo),
 * asi que necesita un directorio real en disco. Llega por
 * {@code BACKUP_STORAGE_PATH} y NO tiene un default pensado para produccion
 * -"./backups" alcanza para desarrollo/demo, pero un despliegue real debe
 * fijar una ruta persistente explicitamente (ver docker-compose.yml, volumen
 * dedicado del servicio backend).
 *
 * <p>El resto de claves tienen default seguro porque no son secretas y un
 * despliegue de demostracion no deberia tener que configurarlas para que el
 * modulo funcione.
 */
@Component
@ConfigurationProperties(prefix = "backup")
public class BackupProperties {

    /** Directorio donde se escriben los .dump. Se crea si no existe. */
    private String storagePath = "./backups";

    /** Ejecutable de pg_dump. Por defecto se resuelve contra el PATH del sistema. */
    private String pgDumpPath = "pg_dump";

    /** Tiempo maximo que se espera a que pg_dump termine antes de forzar su cancelacion. */
    private long timeoutSeconds = 300;

    /**
     * Cada cuanto RespaldoScheduler revisa la configuracion vigente -NO es
     * el intervalo real entre respaldos (ese lo decide el ADMIN desde la
     * pantalla, en minutos/horas/dias, y se compara contra
     * proximoRespaldoEn en cada revision). Ver RespaldoScheduler.
     */
    private long schedulerCheckIntervalMs = 30_000;

    /**
     * URL JDBC que pg_dump usa para localizar host/puerto/base (se le
     * extrae con JdbcPostgresUrlParser; pg_dump no acepta una URL JDBC
     * como tal). Por defecto la MISMA base que usa el resto de la
     * aplicacion (application.yml: {@code ${spring.datasource.url}}) -no
     * se duplica esa configuracion en un despliegue real. Los tests de
     * integracion que necesitan desacoplarla del datasource JPA real
     * (p.ej. H2 en el perfil "test") la sobrescriben de forma explicita.
     */
    private String pgdumpSourceUrl = "";

    /**
     * Usuario para la conexion de pg_dump. Por defecto el rol PROPIETARIO
     * del esquema (application.yml: {@code ${DB_USER:biopet_user}} -la
     * MISMA variable que ya usa Flyway para migrar), NO
     * {@code spring.datasource.username} (biopet_app, de privilegio
     * minimo): un volcado completo del esquema necesita los privilegios
     * del propietario que corrio las migraciones, no los del runtime de la
     * aplicacion.
     *
     * <p>application.yml lee {@code DB_USER} directamente y no
     * {@code spring.flyway.user}: ver el porque en el comentario de esa
     * clave (mismo motivo que pgdumpPassword).
     */
    private String pgdumpUsuario = "";

    /**
     * Contrasena para la conexion de pg_dump. Por defecto
     * {@code ${DB_PASSWORD:}} -la MISMA variable que ya usa Flyway para
     * migrar-: NINGUN secreto nuevo. Nunca se expone en argumentos ni logs
     * -ver PgDumpExecutorImpl, que la coloca unicamente en el entorno del
     * proceso hijo.
     *
     * <p>application.yml lee {@code DB_PASSWORD} directamente y NO
     * {@code spring.flyway.password}: esa propiedad no tiene default
     * ("Sin fallback: un secreto con fallback no es un secreto") y, al ser
     * esta clase un {@code @ConfigurationProperties} que Spring instancia
     * SIEMPRE al arrancar -incluso con Flyway deshabilitado-, encadenar
     * "${spring.flyway.password:}" igual intenta resolver el valor CRUDO
     * de esa propiedad primero y falla ANTES de llegar al default de
     * afuera, tumbando el arranque de cualquier entorno que no exporte
     * DB_PASSWORD (p.ej. los tests, con Flyway deshabilitado). Leer
     * DB_PASSWORD como variable de entorno de nivel raiz, con su propio
     * default vacio explicito, no tiene ese problema.
     */
    private String pgdumpPassword = "";

    public String getPgdumpSourceUrl() {
        return pgdumpSourceUrl;
    }

    public void setPgdumpSourceUrl(String pgdumpSourceUrl) {
        this.pgdumpSourceUrl = pgdumpSourceUrl;
    }

    public String getPgdumpUsuario() {
        return pgdumpUsuario;
    }

    public void setPgdumpUsuario(String pgdumpUsuario) {
        this.pgdumpUsuario = pgdumpUsuario;
    }

    public String getPgdumpPassword() {
        return pgdumpPassword;
    }

    public void setPgdumpPassword(String pgdumpPassword) {
        this.pgdumpPassword = pgdumpPassword;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getPgDumpPath() {
        return pgDumpPath;
    }

    public void setPgDumpPath(String pgDumpPath) {
        this.pgDumpPath = pgDumpPath;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getSchedulerCheckIntervalMs() {
        return schedulerCheckIntervalMs;
    }

    public void setSchedulerCheckIntervalMs(long schedulerCheckIntervalMs) {
        this.schedulerCheckIntervalMs = schedulerCheckIntervalMs;
    }

    /** Nunca imprime pgdumpPassword. */
    @Override
    public String toString() {
        return "BackupProperties[storagePath=" + storagePath + ", pgDumpPath=" + pgDumpPath
                + ", timeoutSeconds=" + timeoutSeconds
                + ", schedulerCheckIntervalMs=" + schedulerCheckIntervalMs
                + ", pgdumpSourceUrl=" + pgdumpSourceUrl + ", pgdumpUsuario=" + pgdumpUsuario
                + ", pgdumpPasswordConfigurada=" + (pgdumpPassword != null && !pgdumpPassword.isBlank()) + "]";
    }
}
