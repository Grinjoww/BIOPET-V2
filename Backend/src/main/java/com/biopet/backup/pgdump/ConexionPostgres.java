package com.biopet.backup.pgdump;

/** host/puerto/nombre de base extraidos de una URL JDBC de PostgreSQL. Sin credenciales. */
public record ConexionPostgres(String host, int port, String database) {}
