package oracle.packages.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC-проверка {@code MEMBER OF} в кастомной сборке Apache Ignite 2 (Oracle-подобный SQL).
 * <p>
 * Стандартный Ignite 2.17 и публичный JDBC-драйвер этот синтаксис не поддерживают;
 * URL фиктивный — тест только документирует ожидаемое поведение на кастомном кластере.
 */
@Disabled("Requires custom Ignite 2 JDBC with MEMBER OF; connection URL is not a real cluster")
class IgniteMemberOfJdbcTest {

    /**
     * Плейсхолдер: подставьте URL embedded/тонкого клиента кастомной сборки.
     */
    private static final String IGNITE_JDBC_URL =
            "jdbc:ignite:thin://127.0.0.1:11999/ORACLE_PACKAGES_MEMBER_OF";

    private static final String IGNITE_USER = "ignite";
    private static final String IGNITE_PASSWORD = "ignite";

    /**
     * Предполагается, что {@code ig_demo_number_set()} на стороне Ignite возвращает {@code {1, 2, 3}}.
     */
    private static final String SQL_MEMBER_OF_FUNCTION =
            """
                    SELECT CASE WHEN ? MEMBER OF ig_demo_number_set() THEN 1 ELSE 0 END
                    FROM SYS.DUAL
                    """;

    /**
     * Коллекция целиком во втором параметре ({@code List<BigDecimal>} через {@link PreparedStatement#setObject}).
     */
    private static final String SQL_MEMBER_OF_COLLECTION_PARAM =
            """
                    SELECT CASE WHEN ? MEMBER OF ? THEN 1 ELSE 0 END
                    FROM SYS.DUAL
                    """;

    /**
     * Фиксированные литералы и коллекция-параметр в середине (строковый тип элементов).
     */
    private static final String SQL_MEMBER_OF_FIXED_AND_PARAM =
            """
                    SELECT CASE WHEN ? MEMBER OF ('1', ?, '5') THEN 1 ELSE 0 END
                    FROM SYS.DUAL
                    """;

    private static final String SQL_NOT_MEMBER_OF_COLLECTION_PARAM =
            """
                    SELECT CASE WHEN ? NOT MEMBER OF ? THEN 1 ELSE 0 END
                    FROM SYS.DUAL
                    """;

    @Test
    void memberOf_functionReturningCollection() throws SQLException {
        try (Connection connection = openIgniteConnection()) {
            assertThat(queryFlag(connection, SQL_MEMBER_OF_FUNCTION, new BigDecimal("2"))).isOne();
            assertThat(queryFlag(connection, SQL_MEMBER_OF_FUNCTION, new BigDecimal("9"))).isZero();
        }
    }

    @Test
    void memberOf_collectionBindParameter() throws SQLException {
        List<BigDecimal> allowed = List.of(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("30"));

        try (Connection connection = openIgniteConnection()) {
            assertThat(queryFlag(connection, SQL_MEMBER_OF_COLLECTION_PARAM, new BigDecimal("20"), allowed))
                    .isOne();
            assertThat(queryFlag(connection, SQL_MEMBER_OF_COLLECTION_PARAM, new BigDecimal("25"), allowed))
                    .isZero();
        }
    }

    @Test
    void memberOf_fixedLiteralsWithCollectionParameter() throws SQLException {
        List<String> middle = List.of("2", "3", "4");

        try (Connection connection = openIgniteConnection()) {
            assertThat(queryFlag(connection, SQL_MEMBER_OF_FIXED_AND_PARAM, "3", middle)).isOne();
            assertThat(queryFlag(connection, SQL_MEMBER_OF_FIXED_AND_PARAM, "9", middle)).isZero();
        }
    }

    @Test
    void notMemberOf_collectionBindParameter() throws SQLException {
        List<BigDecimal> blocked = List.of(
                new BigDecimal("100"),
                new BigDecimal("200"));

        try (Connection connection = openIgniteConnection()) {
            assertThat(queryFlag(connection, SQL_NOT_MEMBER_OF_COLLECTION_PARAM, new BigDecimal("50"), blocked))
                    .isOne();
            assertThat(queryFlag(connection, SQL_NOT_MEMBER_OF_COLLECTION_PARAM, new BigDecimal("100"), blocked))
                    .isZero();
        }
    }

    private static Connection openIgniteConnection() throws SQLException {
        return DriverManager.getConnection(IGNITE_JDBC_URL, IGNITE_USER, IGNITE_PASSWORD);
    }

    /**
     * @return {@code 1} если предикат истинен, иначе {@code 0}
     */
    private static int queryFlag(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                int flag = resultSet.getInt(1);
                assertThat(resultSet.next()).isFalse();
                return flag;
            }
        }
    }

    private static void bindParameters(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object value = parameters[i];
            if (value instanceof List<?> list) {
                statement.setObject(i + 1, list);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }
}
