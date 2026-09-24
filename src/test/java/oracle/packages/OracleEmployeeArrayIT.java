package oracle.packages;

import oracle.packages.util.Employee;
import oracle.packages.util.OracleJsonTableQuery;
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
 * Демонстрация «таблицы на лету» через {@link OracleJsonTableQuery} (без CREATE TYPE).
 * Контейнер поднимается один раз на класс.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleEmployeeArrayIT {

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void selectAggregatesOverPojoList() throws SQLException {
        List<Employee> employees = List.of(
                new Employee(1, "Ivan", 30, 60_000),
                new Employee(2, "Anna", 25, 45_000)
        );

        try (Connection conn = OracleTestJdbc.openConnection(ORACLE)) {

            List<Map<String, Object>> rows = OracleJsonTableQuery.query(
                    conn,
                    Employee.class,
                    employees,
                    "max(age) AS max_age, min(salary) AS min_salary"
            );

            assertThat(rows).hasSize(1);
            assertThat(new BigDecimal(rows.get(0).get("MAX_AGE").toString())).isEqualByComparingTo("30");
            assertThat(new BigDecimal(rows.get(0).get("MIN_SALARY").toString())).isEqualByComparingTo("45000");
        }
    }
}
