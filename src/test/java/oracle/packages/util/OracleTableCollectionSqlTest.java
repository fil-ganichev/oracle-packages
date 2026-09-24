package oracle.packages.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleTableCollectionSqlTest {

    @Test
    void rewriteNamedTableBind_varchar2() {
        String sql = "SELECT VALUE(t) FROM TABLE(:p_keys) t";
        String rewritten = OracleTableCollectionSql.rewriteNamedTableBind(sql, "p_keys", String.class);

        assertThat(rewritten).isEqualTo(
                "SELECT VALUE(t) FROM TABLE(CAST(:p_keys AS SYS.ODCIVARCHAR2LIST)) t");
    }

    @Test
    void rewriteNamedTableBind_numberTypes() {
        assertThat(OracleTableCollectionSql.rewriteNamedTableBind(
                "FROM TABLE(:p_n) x", "p_n", Integer.class))
                .contains("CAST(:p_n AS SYS.ODCINUMBERLIST)");
        assertThat(OracleTableCollectionSql.rewriteNamedTableBind(
                "FROM TABLE(:p_n) x", "p_n", Long.class))
                .contains("SYS.ODCINUMBERLIST");
        assertThat(OracleTableCollectionSql.rewriteNamedTableBind(
                "FROM TABLE(:p_n) x", "p_n", BigDecimal.class))
                .contains("SYS.ODCINUMBERLIST");
    }

    @Test
    void rewriteNamedTableBind_dateAndTimestampElementType() {
        assertThat(OracleTableCollectionSql.rewriteNamedTableBind(
                "FROM TABLE(:p_d) x", "p_d", java.sql.Date.class))
                .contains("CAST(:p_d AS SYS.ODCIDATELIST)");
        assertThat(OracleTableCollectionSql.rewriteNamedTableBind(
                "FROM TABLE(:p_d) x", "p_d", Timestamp.class))
                .contains("SYS.ODCIDATELIST");
    }

    @Test
    void rewritePositionalTableBind() {
        String sql = "SELECT VALUE(t) FROM TABLE(?) t";
        String rewritten = OracleTableCollectionSql.rewritePositionalTableBind(sql, String.class);

        assertThat(rewritten).isEqualTo(
                "SELECT VALUE(t) FROM TABLE(CAST(? AS SYS.ODCIVARCHAR2LIST)) t");
    }

    @Test
    void rewriteNamedTableBinds_multipleParams() {
        String sql = "SELECT * FROM TABLE(:p_a) a, TABLE(:p_b) b";
        String rewritten = OracleTableCollectionSql.rewriteNamedTableBinds(
                sql,
                Map.of("p_a", String.class, "p_b", Integer.class));

        assertThat(rewritten).contains("CAST(:p_a AS SYS.ODCIVARCHAR2LIST)");
        assertThat(rewritten).contains("CAST(:p_b AS SYS.ODCINUMBERLIST)");
    }

    @Test
    void rewriteNamedTableBind_rejectsMissingTableBind() {
        assertThatThrownBy(() ->
                OracleTableCollectionSql.rewriteNamedTableBind("SELECT 1 FROM dual", "p_x", String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TABLE(:p_x)");
    }

    @Test
    void fromCollection_emptyRequiresExplicitType() {
        assertThat(OracleOdciCollectionType.fromCollection(java.util.List.of(), Integer.class))
                .isEqualTo(OracleOdciCollectionType.NUMBER);
        assertThatThrownBy(() -> OracleOdciCollectionType.fromCollection(java.util.List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
