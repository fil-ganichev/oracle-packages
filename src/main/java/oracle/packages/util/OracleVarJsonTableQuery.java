package oracle.packages.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import oracle.packages.BooleanVar;
import oracle.packages.DateVar;
import oracle.packages.RawVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Выполнение произвольного SQL-{@code SELECT} над списком бинов с полями {@link Var}
 * через Oracle {@code JSON_TABLE}.
 * <p>
 * В отличие от {@link OracleJsonTableQuery}, свойства бина должны быть обёртками {@code Var}.
 * При сериализации в JSON берётся {@link Var#get()} (для {@code null}/{@link Var#isNull()} — JSON null).
 * Поля {@link TimestampVar}/{@link DateVar}/{@link SqlTimestampVar} уходят в JSON как ISO-8601;
 * в {@code JSON_TABLE} даты — {@code TIMESTAMP} / {@code TIMESTAMP WITH TIME ZONE},
 * поэтому результат нормализуется к {@link java.sql.Timestamp}.
 * Пользовательские Oracle-типы не создаются.
 * <p>
 * Требуется Oracle 12.1.0.2+.
 * <p>
 * <b>Важно:</b> {@code selectClause} / {@code whereClause} подставляются в SQL как есть —
 * используйте только доверенные строки.
 *
 * <pre>{@code
 * List<EmployeeVarBean> employees = List.of(
 *     EmployeeVarBean.of(1, "Ivan", 30, 60_000),
 *     EmployeeVarBean.of(2, "Anna", 25, 45_000)
 * );
 * List<Map<String, Object>> rows = OracleVarJsonTableQuery.query(
 *     connection,
 *     EmployeeVarBean.class,
 *     employees,
 *     "max(age) AS max_age, min(salary) AS min_salary"
 * );
 * }</pre>
 */
public final class OracleVarJsonTableQuery {

    private static final Logger log = LoggerFactory.getLogger(OracleVarJsonTableQuery.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private OracleVarJsonTableQuery() {
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM JSON_TABLE(...)}.
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
                    "Empty list: pass bean type via query(connection, beanType, beans, selectClause)");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) beans.get(0).getClass();
        return query(connection, type, beans, selectClause, null);
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM JSON_TABLE(...)} над бинами с полями {@link Var}.
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

        List<VarBeanJsonColumns.Column> columns = VarBeanJsonColumns.from(beanType);
        Set<String> booleanColumns = columns.stream()
                .filter(c -> BooleanVar.class.isAssignableFrom(c.varType()))
                .map(c -> c.name().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String sql = buildSql(columns, select, whereClause);
        String json = toJsonArray(beanType, beans, columns);

        log.debug("OracleVarJsonTableQuery SQL:\n{}\nJSON length={}", sql, json.length());

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
                        row.put(label, normalizeResultValue(rs.getObject(i), label, booleanColumns));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Собирает SQL без выполнения — для отладки и unit-тестов. */
    public static String buildSql(Class<?> beanType, String selectClause) {
        return buildSql(beanType, selectClause, null);
    }

    public static String buildSql(Class<?> beanType, String selectClause, String whereClause) {
        return buildSql(
                VarBeanJsonColumns.from(beanType),
                requireSqlFragment(selectClause, "selectClause"),
                whereClause);
    }

    static String buildSql(List<VarBeanJsonColumns.Column> columns, String selectClause, String whereClause) {
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

    /**
     * Разворачивает {@link Var}-свойства бинов в список Map и сериализует в JSON-массив.
     */
    private static <T> String toJsonArray(
            Class<T> beanType,
            List<T> beans,
            List<VarBeanJsonColumns.Column> columns) {
        List<Map<String, Object>> rows = new ArrayList<>(beans.size());
        for (T bean : beans) {
            rows.add(unwrapBean(beanType, bean, columns));
        }
        try {
            return MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize Var beans to JSON", e);
        }
    }

    private static <T> Map<String, Object> unwrapBean(
            Class<T> beanType,
            T bean,
            List<VarBeanJsonColumns.Column> columns) {
        if (bean == null) {
            throw new IllegalArgumentException("Bean list must not contain null elements");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (VarBeanJsonColumns.Column column : columns) {
            Object raw = readProperty(beanType, bean, column.name());
            row.put(column.name(), unwrapVarValue(raw, column.name()));
        }
        return row;
    }

    private static Object readProperty(Class<?> beanType, Object bean, String propertyName) {
        try {
            if (beanType.isRecord()) {
                for (RecordComponent component : beanType.getRecordComponents()) {
                    if (component.getName().equals(propertyName)) {
                        return component.getAccessor().invoke(bean);
                    }
                }
                throw new IllegalArgumentException(
                        "Record component not found: " + propertyName + " on " + beanType.getName());
            }
            BeanInfo info = Introspector.getBeanInfo(beanType, Object.class);
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if (propertyName.equals(pd.getName()) && pd.getReadMethod() != null) {
                    Method getter = pd.getReadMethod();
                    return getter.invoke(bean);
                }
            }
            throw new IllegalArgumentException(
                    "Readable property not found: " + propertyName + " on " + beanType.getName());
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "Failed to read property '" + propertyName + "' from " + beanType.getName(), e);
        }
    }

    /**
     * Извлекает сырое значение из {@link Var}; {@code null} и {@link Var#isNull()} дают JSON null.
     */
    @SuppressWarnings("rawtypes")
    private static Object unwrapVarValue(Object raw, String propertyName) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Var<?> var)) {
            throw new IllegalArgumentException(
                    "Property '" + propertyName + "' must be a Var, but was " + raw.getClass().getName());
        }
        if (var.isNull()) {
            return null;
        }
        Object value = var.get();
        // RAW удобнее отдавать в JSON как hex-строку
        if (raw instanceof RawVar rawVar) {
            return rawVar.toHex();
        }
        // Даты — ISO-8601, чтобы JSON_TABLE разобрал значение
        if (raw instanceof SqlTimestampVar && value instanceof Timestamp ts) {
            // Instant с Z — round-trip getTime() через TIMESTAMP WITH TIME ZONE
            return DateTimeFormatter.ISO_INSTANT.format(ts.toInstant());
        }
        if (raw instanceof TimestampVar && value instanceof OffsetDateTime odt) {
            return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (raw instanceof DateVar && value instanceof LocalDateTime ldt) {
            return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        // Boolean → 0/1 для колонки NUMBER; в результате снова Boolean
        if (raw instanceof BooleanVar && value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return value;
    }

    /**
     * Нормализация JDBC-значения; свойства {@link BooleanVar} отдаём как {@link Boolean}.
     */
    private static Object normalizeResultValue(
            Object value,
            String columnLabel,
            Set<String> booleanColumnsUpper) {
        Object normalized = normalizeJdbcValue(value);
        if (columnLabel != null
                && booleanColumnsUpper.contains(columnLabel.toUpperCase(Locale.ROOT))) {
            return toBoolean(normalized);
        }
        return normalized;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = value.toString().trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return false;
        }
        throw new IllegalArgumentException("Cannot convert value to Boolean: " + value);
    }

    /**
     * Приводит JDBC-значения даты/времени к {@link Timestamp} (в т.ч. oracle.sql.TIMESTAMP / TIMESTAMPTZ).
     */
    private static Object normalizeJdbcValue(Object value) {
        if (value == null || value instanceof Timestamp) {
            return value;
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        String typeName = value.getClass().getName();
        if (typeName.equals("oracle.sql.TIMESTAMPTZ")) {
            // timestampValue() требует Connection; offsetDateTimeValue() — нет
            try {
                Method method = value.getClass().getMethod("offsetDateTimeValue");
                Object odt = method.invoke(value);
                if (odt instanceof OffsetDateTime offsetDateTime) {
                    return Timestamp.from(offsetDateTime.toInstant());
                }
            } catch (ReflectiveOperationException ignored) {
                return value;
            }
            return value;
        }
        if (typeName.equals("oracle.sql.TIMESTAMP")) {
            try {
                Method method = value.getClass().getMethod("timestampValue");
                Object ts = method.invoke(value);
                return ts instanceof Timestamp ? ts : value;
            } catch (ReflectiveOperationException ignored) {
                return value;
            }
        }
        return value;
    }

    private static String requireSqlFragment(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return value.trim();
    }

    private static String quoteIdent(String name) {
        if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Property name is not a valid Oracle JSON_TABLE identifier: " + name);
        }
        return name;
    }
}
