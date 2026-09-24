package oracle.packages;

import oracle.packages.util.OracleTableCollectionBind;
import oracle.packages.util.OracleTableCollectionSql;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Передача коллекций в {@code TABLE(...)} с системными типами ODCI по типу элементов.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleTableCollectionParamIT {

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    private static final String SQL_STRINGS =
            "SELECT VALUE(t) FROM TABLE(CAST(? AS SYS.ODCIVARCHAR2LIST)) t";

    private static final String SQL_NUMBERS =
            "SELECT VALUE(t) FROM TABLE(CAST(? AS SYS.ODCINUMBERLIST)) t ORDER BY 1";

    private static final String SQL_DATES =
            "SELECT VALUE(t) FROM TABLE(CAST(? AS SYS.ODCIDATELIST)) t ORDER BY 1";

    @Test
    void varchar2Collection_distinct() throws SQLException {
        Collection<String> keys = List.of("K001", "K002", "K001");

        try (Connection connection = openConnection()) {
            List<String> rows = queryStrings(connection, keys, String.class);
            assertThat(rows).containsExactly("K001", "K002", "K001");
        }
    }

    @Test
    void varchar2Collection_empty() throws SQLException {
        try (Connection connection = openConnection()) {
            List<String> rows = queryStrings(connection, List.of(), String.class);
            assertThat(rows).isEmpty();
        }
    }

    @Test
    void numberCollections_integerLongBigDecimal() throws SQLException {
        try (Connection connection = openConnection()) {
            assertThat(queryNumbers(connection, List.of(1, 2, 3), Integer.class))
                    .extracting(this::toBigDecimal)
                    .containsExactly(BigDecimal.valueOf(1), BigDecimal.valueOf(2), BigDecimal.valueOf(3));

            assertThat(queryNumbers(connection, List.of(10L, 20L), Long.class))
                    .extracting(this::toBigDecimal)
                    .containsExactly(BigDecimal.valueOf(10), BigDecimal.valueOf(20));

            assertThat(queryNumbers(connection, List.of(new BigDecimal("1.5")), BigDecimal.class))
                    .extracting(this::toBigDecimal)
                    .containsExactly(new BigDecimal("1.5"));
        }
    }

    @Test
    void numberCollection_empty() throws SQLException {
        try (Connection connection = openConnection()) {
            assertThat(queryNumbers(connection, Collections.emptyList(), Integer.class)).isEmpty();
        }
    }

    @Test
    void dateCollection_withTimestampElements() throws SQLException {
        Timestamp t1 = Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        Timestamp t2 = Timestamp.valueOf(LocalDateTime.of(2024, 6, 1, 0, 0, 1));

        try (Connection connection = openConnection()) {
            List<Timestamp> rows = queryDates(connection, List.of(t1, t2), Timestamp.class);
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getTime()).isEqualTo(t1.getTime());
            assertThat(rows.get(1).getTime()).isEqualTo(t2.getTime());
        }
    }

    @Test
    void dateCollection_emptyWithExplicitTimestampType() throws SQLException {
        try (Connection connection = openConnection()) {
            assertThat(queryDates(connection, List.of(), Timestamp.class)).isEmpty();
        }
    }

    @Test
    void sqlRewriteThenExecute_positionalBind() throws SQLException {
        String rawSql = "SELECT COUNT(*) FROM TABLE(?) t";
        String sql = OracleTableCollectionSql.rewritePositionalTableBind(rawSql, String.class);
        assertThat(sql).contains("CAST(? AS SYS.ODCIVARCHAR2LIST)");

        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            OracleTableCollectionBind.setCollection(connection, ps, 1, List.of("a", "b"), String.class);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    private List<String> queryStrings(
            Connection connection,
            Collection<String> values,
            Class<?> elementTypeIfEmpty) throws SQLException {
        return queryValues(connection, SQL_STRINGS, values, elementTypeIfEmpty, rs -> rs.getString(1));
    }

    private List<BigDecimal> queryNumbers(
            Connection connection,
            Collection<?> values,
            Class<?> elementTypeIfEmpty) throws SQLException {
        List<Object> rows = queryValues(connection, SQL_NUMBERS, values, elementTypeIfEmpty, rs -> rs.getObject(1));
        List<BigDecimal> result = new ArrayList<>();
        for (Object row : rows) {
            result.add(toBigDecimal(row));
        }
        return result;
    }

    private List<Timestamp> queryDates(
            Connection connection,
            Collection<?> values,
            Class<?> elementTypeIfEmpty) throws SQLException {
        return queryValues(connection, SQL_DATES, values, elementTypeIfEmpty, rs -> rs.getTimestamp(1));
    }

    private <T> List<T> queryValues(
            Connection connection,
            String sql,
            Collection<?> values,
            Class<?> elementTypeIfEmpty,
            SqlRowMapper<T> mapper) throws SQLException {
        List<T> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            OracleTableCollectionBind.setCollection(connection, ps, 1, values, elementTypeIfEmpty);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
            }
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    @FunctionalInterface
    private interface SqlRowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static Connection openConnection() throws SQLException {
        return OracleTestJdbc.openConnection(ORACLE);
    }
}
