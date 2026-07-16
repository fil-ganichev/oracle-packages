package oracle.packages.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Выполнение произвольного SQL-{@code SELECT} над списком POJO через Oracle {@code JSON_TABLE}.
 * <p>
 * Список бинов сериализуется в JSON-массив; колонки {@code JSON_TABLE} выводятся из свойств бина
 * (JavaBean getters или record-компоненты). Пользовательские Oracle-типы не создаются.
 * <p>
 * Требуется Oracle 12.1.0.2+.
 * <p>
 * <b>Важно:</b> фрагменты {@code selectClause} / {@code whereClause} подставляются в SQL как есть —
 * передавайте только доверенные строки (не пользовательский ввод).
 *
 * <pre>{@code
 * List<Employee> employees = List.of(
 *     new Employee(1, "Ivan", 30, 60_000),
 *     new Employee(2, "Anna", 25, 45_000)
 * );
 * List<Map<String, Object>> rows = OracleJsonTableQuery.query(
 *     connection,
 *     Employee.class,
 *     employees,
 *     "max(age), min(salary)"
 * );
 * }</pre>
 */
public final class OracleJsonTableQuery {

    private static final Logger log = LoggerFactory.getLogger(OracleJsonTableQuery.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private OracleJsonTableQuery() {
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM JSON_TABLE(...)} над списком бинов.
     * Тип бина берётся из первого элемента; для пустого списка используйте
     * {@link #query(Connection, Class, List, String)}.
     */
    public static <T> List<Map<String, Object>> query(
            Connection connection,
            List<T> beans,
            String selectClause) throws SQLException {
        Objects.requireNonNull(beans, "beans");
        if (beans.isEmpty()) {
            throw new IllegalArgumentException(
                    "Пустой список: укажите Class через query(connection, beanType, beans, selectClause)");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) beans.get(0).getClass();
        return query(connection, type, beans, selectClause, null);
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM JSON_TABLE(...)} над списком бинов типа {@code beanType}.
     */
    public static <T> List<Map<String, Object>> query(
            Connection connection,
            Class<T> beanType,
            List<T> beans,
            String selectClause) throws SQLException {
        return query(connection, beanType, beans, selectClause, null);
    }

    /**
     * Как {@link #query(Connection, Class, List, String)}, плюс опциональный {@code WHERE}.
     *
     * @param whereClause условие без слова WHERE, например {@code "salary > 50000"}; {@code null} — без фильтра
     */
    public static <T> List<Map<String, Object>> query(
            Connection connection,
            Class<T> beanType,
            List<T> beans,
            String selectClause,
            String whereClause) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(beanType, "beanType");
        Objects.requireNonNull(beans, "beans");
        String select = requireSqlFragment(selectClause, "selectClause");

        List<BeanJsonColumns.Column> columns = BeanJsonColumns.from(beanType);
        String sql = buildSql(columns, select, whereClause);
        String json = toJsonArray(beans);

        log.debug("OracleJsonTableQuery SQL:\n{}\nJSON length={}", sql, json.length());

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, json);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        row.put(label, rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Собирает SQL без выполнения — удобно для отладки и unit-тестов.
     */
    public static String buildSql(Class<?> beanType, String selectClause) {
        return buildSql(beanType, selectClause, null);
    }

    public static String buildSql(Class<?> beanType, String selectClause, String whereClause) {
        return buildSql(BeanJsonColumns.from(beanType), requireSqlFragment(selectClause, "selectClause"), whereClause);
    }

    static String buildSql(List<BeanJsonColumns.Column> columns, String selectClause, String whereClause) {
        String columnsSql = columns.stream()
                .map(c -> "  " + quoteIdent(c.name()) + " " + c.oracleType() + " PATH '$." + c.name() + "'")
                .collect(Collectors.joining(",\n"));

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(selectClause.trim()).append('\n');
        sql.append("  FROM JSON_TABLE(\n");
        sql.append("         ?,\n");
        sql.append("         '$[*]'\n");
        sql.append("         COLUMNS (\n");
        sql.append(columnsSql).append('\n');
        sql.append("         )\n");
        sql.append("       )");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append("\n WHERE ").append(whereClause.trim());
        }
        return sql.toString();
    }

    private static String toJsonArray(List<?> beans) {
        try {
            return MAPPER.writeValueAsString(beans);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать бины в JSON", e);
        }
    }

    private static String requireSqlFragment(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " не должен быть пустым");
        }
        return value.trim();
    }

    /** Имя колонки JSON_TABLE без кавычек (Oracle приводит к UPPER). */
    private static String quoteIdent(String name) {
        if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Имя свойства непригодно как идентификатор Oracle JSON_TABLE: " + name);
        }
        return name;
    }
}
