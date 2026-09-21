package com.exame.ordem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TestcontainersConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void containerSobeEConecta() throws Exception {
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.getConnection().isValid(2)).isTrue();
    }

    @Test
    void flywayAplicouAsMigracoes() {
        Integer versao = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(versao).isGreaterThanOrEqualTo(10);
    }

    @Test
    void extensaoPgvectorHabilitada() {
        // A migração V3 faz CREATE EXTENSION vector
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}