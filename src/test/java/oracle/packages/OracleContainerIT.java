package oracle.packages;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест: Oracle XE через Testcontainers и {@code SELECT 1 FROM DUAL}.
 * Требует доступный Docker. При отсутствии Docker тест пропускается.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleContainerIT {

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void selectOneFromDual() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                ORACLE.getJdbcUrl(),
                ORACLE.getUsername(),
                ORACLE.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM DUAL")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
            assertThat(rs.next()).isFalse();
        }
    }
}
