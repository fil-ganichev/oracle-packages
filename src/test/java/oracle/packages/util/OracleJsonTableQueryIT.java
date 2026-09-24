package oracle.packages.util;

import oracle.packages.OracleTestJdbc;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест {@link OracleJsonTableQuery} на Oracle XE.
 * Контейнер поднимается один раз на класс.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleJsonTableQueryIT {

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void query_maxAge_minSalary() throws SQLException {
        List<Employee> employees = List.of(
                new Employee(1, "Ivan", 30, 60_000),
                new Employee(2, "Anna", 25, 45_000),
                new Employee(3, "Petr", 40, 70_000)
        );

        try (Connection conn = openConnection()) {
            List<Map<String, Object>> rows = OracleJsonTableQuery.query(
                    conn,
                    Employee.class,
                    employees,
                    "max(age) AS max_age, min(salary) AS min_salary"
            );

            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(toBigDecimal(row.get("MAX_AGE"))).isEqualByComparingTo("40");
            assertThat(toBigDecimal(row.get("MIN_SALARY"))).isEqualByComparingTo("45000");
        }
    }

    @Test
    void query_withWhere_filtersRows() throws SQLException {
        List<Employee> employees = List.of(
                new Employee(1, "Ivan", 30, 60_000),
                new Employee(2, "Anna", 25, 45_000)
        );

        try (Connection conn = openConnection()) {
            List<Map<String, Object>> rows = OracleJsonTableQuery.query(
                    conn,
                    Employee.class,
                    employees,
                    "name, salary",
                    "salary > 50000"
            );

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("NAME")).isEqualTo("Ivan");
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private static Connection openConnection() throws SQLException {
        return OracleTestJdbc.openConnection(ORACLE);
    }
}
