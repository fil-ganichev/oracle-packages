package oracle.packages.util;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты сборки SQL и аргумента rows для {@link IgniteVarToRowsQuery}.
 * Ignite не поднимается — в стандартной сборке нет {@code TO_ROWS}.
 */
class IgniteVarToRowsQueryTest {

    @Test
    void buildSql_includesToRowsColumnsAndSelect() {
        String sql = IgniteVarToRowsQuery.buildSql(EmployeeVarBean.class, "max(age), min(salary)");

        assertThat(sql).contains("SELECT max(age), min(salary)");
        assertThat(sql).contains("FROM TABLE(TO_ROWS(");
        assertThat(sql).contains("?");
        assertThat(sql).contains("'id', 'DECIMAL'");
        assertThat(sql).contains("'name', 'VARCHAR'");
        assertThat(sql).contains("'age', 'DECIMAL'");
        assertThat(sql).contains("'salary', 'DECIMAL'");
        assertThat(sql).contains("'hiredAt', 'TIMESTAMP'");
        assertThat(sql).contains("'updatedAt', 'TIMESTAMP'");
        assertThat(sql).contains("'active', 'BOOLEAN'");
        assertThat(sql).doesNotContain("WHERE");
    }

    @Test
    void buildSql_withWhere() {
        String sql = IgniteVarToRowsQuery.buildSql(
                EmployeeVarBean.class,
                "name",
                "active = TRUE");

        assertThat(sql).contains("SELECT name");
        assertThat(sql).contains("WHERE active = TRUE");
    }

    @Test
    void buildSql_rejectsBlankSelect() {
        assertThatThrownBy(() -> IgniteVarToRowsQuery.buildSql(EmployeeVarBean.class, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selectClause must not be blank");
    }

    @Test
    void buildSql_rejectsBeanWithoutVarProperties() {
        assertThatThrownBy(() -> IgniteVarToRowsQuery.buildSql(Employee.class, "age"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no readable Var properties");
    }

    @Test
    void query_rejectsEmptyListWithoutType() {
        assertThatThrownBy(() -> IgniteVarToRowsQuery.query(null, List.of(), "max(age)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty list");
    }

    /** Record с Var-компонентами — колонки тоже должны строиться. */
    public record EmployeeVarRecord(NumberVar id, Varchar2Var name, NumberVar age, NumberVar salary) {
    }

    @Test
    void buildSql_supportsRecordWithVarComponents() {
        String sql = IgniteVarToRowsQuery.buildSql(
                EmployeeVarRecord.class,
                "max(age) AS max_age");

        assertThat(sql).contains("'age', 'DECIMAL'");
        assertThat(sql).contains("'name', 'VARCHAR'");
        assertThat(sql).contains("FROM TABLE(TO_ROWS(");
    }

    @Test
    void toRowsArgument_unwrapsVarValuesInColumnOrder() {
        OffsetDateTime hired = OffsetDateTime.of(2020, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        Timestamp updated = Timestamp.from(hired.toInstant());
        EmployeeVarBean bean = EmployeeVarBean.of(1, "Alice", 30, 60_000, hired, updated, true);

        List<VarBeanIgniteColumns.Column> columns = VarBeanIgniteColumns.from(EmployeeVarBean.class);
        Object[] rows = IgniteVarToRowsQuery.toRowsArgument(
                EmployeeVarBean.class,
                List.of(bean),
                columns);

        assertThat(rows).hasSize(1);
        Object[] row = (Object[]) rows[0];
        assertThat(row).hasSize(columns.size());

        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).name();
            Object value = row[i];
            switch (name) {
                case "id" -> assertThat(value).isEqualTo(BigDecimal.valueOf(1));
                case "name" -> assertThat(value).isEqualTo("Alice");
                case "age" -> assertThat(value).isEqualTo(BigDecimal.valueOf(30));
                case "salary" -> assertThat(value).isEqualTo(BigDecimal.valueOf(60_000));
                case "hiredAt" -> assertThat(value)
                        .isInstanceOf(Timestamp.class)
                        .isEqualTo(Timestamp.from(hired.toInstant()));
                case "updatedAt" -> assertThat(value)
                        .isInstanceOf(Timestamp.class)
                        .extracting(v -> ((Timestamp) v).getTime())
                        .isEqualTo(updated.getTime());
                case "active" -> assertThat(value).isEqualTo(true);
                default -> throw new AssertionError("Unexpected column: " + name);
            }
        }
    }
}
