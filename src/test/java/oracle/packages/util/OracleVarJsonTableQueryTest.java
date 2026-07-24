package oracle.packages.util;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты сборки SQL и валидации для {@link OracleVarJsonTableQuery}.
 */
class OracleVarJsonTableQueryTest {

    @Test
    void buildSql_includesVarColumnsAndSelect() {
        String sql = OracleVarJsonTableQuery.buildSql(EmployeeVarBean.class, "max(age), min(salary)");

        assertThat(sql).contains("SELECT max(age), min(salary)");
        assertThat(sql).contains("JSON_TABLE(");
        assertThat(sql).contains("age NUMBER PATH '$.age'");
        assertThat(sql).contains("salary NUMBER PATH '$.salary'");
        assertThat(sql).contains("name VARCHAR2(4000) PATH '$.name'");
        assertThat(sql).contains("id NUMBER PATH '$.id'");
        assertThat(sql).contains("hiredAt TIMESTAMP WITH TIME ZONE PATH '$.hiredAt'");
        assertThat(sql).contains("updatedAt TIMESTAMP WITH TIME ZONE PATH '$.updatedAt'");
        assertThat(sql).contains("active NUMBER PATH '$.active'");
        assertThat(sql).doesNotContain("WHERE");
    }

    @Test
    void buildSql_withWhere() {
        String sql = OracleVarJsonTableQuery.buildSql(EmployeeVarBean.class, "name", "salary > 50000");

        assertThat(sql).contains("SELECT name");
        assertThat(sql).contains("WHERE salary > 50000");
    }

    @Test
    void buildSql_rejectsBlankSelect() {
        assertThatThrownBy(() -> OracleVarJsonTableQuery.buildSql(EmployeeVarBean.class, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selectClause must not be blank");
    }

    @Test
    void buildSql_rejectsBeanWithoutVarProperties() {
        assertThatThrownBy(() -> OracleVarJsonTableQuery.buildSql(Employee.class, "age"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no readable Var properties");
    }

    @Test
    void query_rejectsEmptyListWithoutType() {
        assertThatThrownBy(() -> OracleVarJsonTableQuery.query(null, java.util.List.of(), "max(age)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty list");
    }

    /** Record с Var-компонентами — колонки тоже должны строиться. */
    public record EmployeeVarRecord(NumberVar id, Varchar2Var name, NumberVar age, NumberVar salary) {
    }

    @Test
    void buildSql_supportsRecordWithVarComponents() {
        String sql = OracleVarJsonTableQuery.buildSql(
                EmployeeVarRecord.class,
                "max(age) AS max_age");

        assertThat(sql).contains("age NUMBER PATH '$.age'");
        assertThat(sql).contains("name VARCHAR2(4000) PATH '$.name'");
    }
}
