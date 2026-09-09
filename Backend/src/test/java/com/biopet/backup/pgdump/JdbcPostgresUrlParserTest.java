package com.biopet.backup.pgdump;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPostgresUrlParserTest {

    @Test
    void parseaUrlSimple() {
        ConexionPostgres c = JdbcPostgresUrlParser.parse("jdbc:postgresql://localhost:5432/biopet_db");
        assertThat(c.host()).isEqualTo("localhost");
        assertThat(c.port()).isEqualTo(5432);
        assertThat(c.database()).isEqualTo("biopet_db");
    }

    @Test
    void parseaUrlConNombreDeServicioDocker() {
        ConexionPostgres c = JdbcPostgresUrlParser.parse("jdbc:postgresql://postgres:5432/biopet_db");
        assertThat(c.host()).isEqualTo("postgres");
        assertThat(c.port()).isEqualTo(5432);
        assertThat(c.database()).isEqualTo("biopet_db");
    }

    @Test
    void parseaUrlConParametrosAdicionalesIgnorandolos() {
        ConexionPostgres c = JdbcPostgresUrlParser.parse(
                "jdbc:postgresql://localhost:5432/biopet_db_1m_v2?sslmode=disable&currentSchema=public");
        assertThat(c.host()).isEqualTo("localhost");
        assertThat(c.port()).isEqualTo(5432);
        assertThat(c.database()).isEqualTo("biopet_db_1m_v2");
    }

    @Test
    void urlNoPostgresqlLanzaExcepcion() {
        assertThatThrownBy(() -> JdbcPostgresUrlParser.parse("jdbc:h2:mem:biopet_test;MODE=PostgreSQL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlNulaLanzaExcepcion() {
        assertThatThrownBy(() -> JdbcPostgresUrlParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlVaciaLanzaExcepcion() {
        assertThatThrownBy(() -> JdbcPostgresUrlParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
