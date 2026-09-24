package oracle.packages.util;

import oracle.packages.OracleTestJdbc;
import oracle.packages.BooleanVar;
import oracle.packages.NumberVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Varchar2Var;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест {@link OracleVarJsonTableQuery} на Oracle XE.
 * Контейнер поднимается один раз на класс.
 */
@Testcontainers//(disabledWithoutDocker = true)
class OracleVarJsonTableQueryIT {

    private static final OffsetDateTime HIRED_IVAN =
            OffsetDateTime.of(2020, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime HIRED_ANNA =
            OffsetDateTime.of(2021, 6, 1, 9, 30, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime HIRED_PETR =
            OffsetDateTime.of(2019, 3, 20, 14, 0, 0, 0, ZoneOffset.UTC);

    private static final Timestamp UPDATED_IVAN = Timestamp.from(HIRED_IVAN.toInstant());
    private static final Timestamp UPDATED_ANNA = Timestamp.from(HIRED_ANNA.toInstant());
    private static final Timestamp UPDATED_PETR = Timestamp.from(HIRED_PETR.toInstant());

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void query_maxAge_minSalary_overVarBeans() throws SQLException {
        List<EmployeeVarBean> employees = List.of(
                EmployeeVarBean.of(1, "Ivan", 30, 60_000, HIRED_IVAN, UPDATED_IVAN, true),
                EmployeeVarBean.of(2, "Anna", 25, 45_000, HIRED_ANNA, UPDATED_ANNA, false),
                EmployeeVarBean.of(3, "Petr", 40, 70_000, HIRED_PETR, UPDATED_PETR, true)
        );

        try (Connection conn = openConnection()) {
            List<Map<String, Object>> rows = OracleVarJsonTableQuery.query(
                    conn,
                    EmployeeVarBean.class,
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
    void query_withWhere_andNullVar() throws SQLException {
        EmployeeVarBean ivan = EmployeeVarBean.of(1, "Ivan", 30, 60_000, HIRED_IVAN, UPDATED_IVAN, true);
        EmployeeVarBean anna = new EmployeeVarBean(
                NumberVar.of(2),
                Varchar2Var.of("Anna"),
                NumberVar.of(25),
                NumberVar.nullValue(),
                TimestampVar.of(HIRED_ANNA),
                SqlTimestampVar.of(UPDATED_ANNA),
                BooleanVar.of(false)
        );

        try (Connection conn = openConnection()) {
            List<Map<String, Object>> rows = OracleVarJsonTableQuery.query(
                    conn,
                    EmployeeVarBean.class,
                    List.of(ivan, anna),
                    "name, salary",
                    "salary > 50000"
            );

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("NAME")).isEqualTo("Ivan");
        }
    }

    @Test
    void query_timestampAndBooleanVars() throws SQLException {
        List<EmployeeVarBean> employees = List.of(
                EmployeeVarBean.of(1, "Ivan", 30, 60_000, HIRED_IVAN, UPDATED_IVAN, true),
                EmployeeVarBean.of(2, "Anna", 25, 45_000, HIRED_ANNA, UPDATED_ANNA, false),
                EmployeeVarBean.of(3, "Petr", 40, 70_000, HIRED_PETR, UPDATED_PETR, true)
        );

        try (Connection conn = openConnection()) {
            List<Map<String, Object>> rows = OracleVarJsonTableQuery.query(
                    conn,
                    EmployeeVarBean.class,
                    employees,
                    "name, hiredAt, updatedAt, active",
                    "active = 1"
            );

            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(r -> r.get("NAME")).containsExactlyInAnyOrder("Ivan", "Petr");
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("ACTIVE")).isInstanceOf(Boolean.class).isEqualTo(true);
            });
            assertThat(rows).extracting(r -> r.get("HIREDAT"))
                    .allMatch(Timestamp.class::isInstance)
                    .extracting(v -> ((Timestamp) v).getTime())
                    .containsExactlyInAnyOrder(
                            HIRED_IVAN.toInstant().toEpochMilli(),
                            HIRED_PETR.toInstant().toEpochMilli());
            assertThat(rows).extracting(r -> r.get("UPDATEDAT"))
                    .allMatch(Timestamp.class::isInstance)
                    .extracting(v -> ((Timestamp) v).getTime())
                    .containsExactlyInAnyOrder(UPDATED_IVAN.getTime(), UPDATED_PETR.getTime());
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
