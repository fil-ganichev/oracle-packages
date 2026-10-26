package oracle.packages.ignite;

import org.apache.ignite.Ignite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Embedded server Ignite + накат {@code person-demo.sql} через {@link IgniteSqlScriptRunner}.
 */
class IgniteSqlScriptRunnerTest {

    private static Ignite ignite;

    @BeforeAll
    static void startIgniteServer() {
        ignite = IgniteTestServer.start();
    }

    @AfterAll
    static void stopIgniteServer() {
        IgniteTestServer.stop();
    }

    @Test
    void splitStatements_skipsCommentsAndBlankLines() {
        assertThat(IgniteSqlScriptRunner.splitStatements("""
                -- comment
                CREATE TABLE T (id INT);

                INSERT INTO T VALUES (1);
                """))
                .containsExactly(
                        "CREATE TABLE T (id INT)",
                        "INSERT INTO T VALUES (1)");
    }

    @Test
    void applyClasspathScript_loadsPersonTableAndRows() {
        IgniteSqlScriptRunner.applyClasspathScript(ignite, "ignite/person-demo.sql");

        List<List<?>> rows = IgniteSqlScriptRunner.executeQuery(
                ignite,
                "SELECT id, name, age, dep_id FROM PERSON ORDER BY id");

        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).get(0)).isEqualTo(new BigDecimal("1"));
        assertThat(rows.get(0).get(1)).isEqualTo("name1");
        assertThat(rows.get(4).get(0)).isEqualTo(new BigDecimal("5"));
        assertThat(rows.get(4).get(3)).isEqualTo(new BigDecimal("6"));
    }
}
