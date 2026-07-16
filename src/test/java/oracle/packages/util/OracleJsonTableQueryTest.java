package oracle.packages.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты сборки SQL для {@link OracleJsonTableQuery} (без Oracle).
 */
class OracleJsonTableQueryTest {

    @Test
    void buildSql_includesBeanColumnsAndSelect() {
        String sql = OracleJsonTableQuery.buildSql(Employee.class, "max(age), min(salary)");

        assertThat(sql).contains("SELECT max(age), min(salary)");
        assertThat(sql).contains("JSON_TABLE(");
        assertThat(sql).contains("age NUMBER PATH '$.age'");
        assertThat(sql).contains("salary NUMBER PATH '$.salary'");
        assertThat(sql).contains("name VARCHAR2(4000) PATH '$.name'");
        assertThat(sql).doesNotContain("WHERE");
    }

    @Test
    void buildSql_withWhere() {
        String sql = OracleJsonTableQuery.buildSql(Employee.class, "name", "salary > 50000");

        assertThat(sql).contains("SELECT name");
        assertThat(sql).contains("WHERE salary > 50000");
    }

    @Test
    void buildSql_rejectsBlankSelect() {
        assertThatThrownBy(() -> OracleJsonTableQuery.buildSql(Employee.class, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
