package oracle.packages;

import oracle.jdbc.pool.OracleDataSource;
import org.testcontainers.containers.OracleContainer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC к Oracle XE в Testcontainers без {@link java.sql.DriverManager}
 * (ignite-core регистрирует свой драйвер и ломает {@code DriverManager.getConnection}).
 */
public final class OracleTestJdbc {

    private OracleTestJdbc() {
    }

    public static Connection openConnection(OracleContainer oracle) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser(oracle.getUsername());
        dataSource.setPassword(oracle.getPassword());
        return dataSource.getConnection();
    }
}
