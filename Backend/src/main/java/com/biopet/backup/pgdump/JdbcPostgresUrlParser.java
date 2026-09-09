package com.biopet.backup.pgdump;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae host/puerto/base de la URL JDBC que ya usa Hibernate
 * ({@code spring.datasource.url}), para no duplicar esa configuracion con
 * variables nuevas solo para pg_dump. Nunca extrae credenciales: esas
 * llegan por separado (ver RespaldoEjecucionService, que usa el rol
 * propietario del esquema -spring.flyway.user/password- y no el de runtime).
 */
public final class JdbcPostgresUrlParser {

    // jdbc:postgresql://host:puerto/basededatos[?parametros]
    private static final Pattern PATRON = Pattern.compile(
            "^jdbc:postgresql://([^:/?]+):(\\d+)/([^?;]+)");

    private JdbcPostgresUrlParser() {
    }

    public static ConexionPostgres parse(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("La URL JDBC de PostgreSQL no puede ser nula.");
        }
        Matcher m = PATRON.matcher(jdbcUrl.trim());
        if (!m.find()) {
            throw new IllegalArgumentException(
                    "No se pudo interpretar la URL JDBC de PostgreSQL para el respaldo (formato inesperado).");
        }
        String host = m.group(1);
        int puerto = Integer.parseInt(m.group(2));
        String base = m.group(3);
        return new ConexionPostgres(host, puerto, base);
    }
}
