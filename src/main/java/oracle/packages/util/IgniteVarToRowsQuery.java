package oracle.packages.util;

import oracle.packages.BooleanVar;
import oracle.packages.DateVar;
import oracle.packages.RawVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Var;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Выполнение произвольного SQL-{@code SELECT} над списком бинов с полями {@link Var}
 * через кастомную Ignite-функцию {@code TO_ROWS}.
 * <p>
 * Список разворачивается в {@code Object[][]} (строки — {@code Object[]}) и передаётся
 * первым аргументом {@code TABLE(TO_ROWS(?, 'col', 'TYPE', ...))}.
 * Типы колонок выводятся из свойств бина (JavaBean getters или record-компоненты).
 * <p>
 * Требуется кастомная сборка Ignite с функцией {@code TO_ROWS} (в Apache Ignite 2.17 её нет).
 * Экземпляр embedded {@link Ignite} передаётся снаружи.
 * <p>
 * <b>Важно:</b> {@code selectClause} / {@code whereClause} подставляются в SQL как есть —
 * используйте только доверенные строки.
 *
 * <pre>{@code
 * List<EmployeeVarBean> employees = List.of(
 *     EmployeeVarBean.of(1, "Ivan", 30, 60_000, hiredAt, updatedAt, true),
 *     EmployeeVarBean.of(2, "Anna", 25, 45_000, hiredAt, updatedAt, false)
 * );
 * List<Map<String, Object>> rows = IgniteVarToRowsQuery.query(
 *     ignite,
 *     EmployeeVarBean.class,
 *     employees,
 *     "max(age) AS max_age, min(salary) AS min_salary"
 * );
 * }</pre>
 */
public final class IgniteVarToRowsQuery {

    private static final Logger log = LoggerFactory.getLogger(IgniteVarToRowsQuery.class);

    /** Кэш для выполнения SqlFieldsQuery (схема PUBLIC). */
    static final String SQL_CACHE_NAME = "oracle-packages-to-rows";

    private IgniteVarToRowsQuery() {
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM TABLE(TO_ROWS(...))}.
     * Тип бина берётся из первого элемента; для пустого списка используйте
     * {@link #query(Ignite, Class, List, String)}.
     */
    public static <T> List<Map<String, Object>> query(
            Ignite ignite,
            List<T> beans,
            String selectClause) {
        Objects.requireNonNull(beans, "beans");
        if (beans.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty list: pass bean type via query(ignite, beanType, beans, selectClause)");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) beans.get(0).getClass();
        return query(ignite, type, beans, selectClause, null);
    }

    /**
     * Выполняет {@code SELECT <selectClause> FROM TABLE(TO_ROWS(...))} над бинами с полями {@link Var}.
     */
    public static <T> List<Map<String, Object>> query(
            Ignite ignite,
            Class<T> beanType,
            List<T> beans,
            String selectClause) {
        return query(ignite, beanType, beans, selectClause, null);
    }

    /**
     * Как {@link #query(Ignite, Class, List, String)}, плюс опциональный {@code WHERE}.
     *
     * @param whereClause условие без слова WHERE, например {@code "salary > 50000"}; {@code null} — без фильтра
     */
    public static <T> List<Map<String, Object>> query(
            Ignite ignite,
            Class<T> beanType,
            List<T> beans,
            String selectClause,
            String whereClause) {
        Objects.requireNonNull(ignite, "ignite");
        Objects.requireNonNull(beanType, "beanType");
        Objects.requireNonNull(beans, "beans");
        String select = requireSqlFragment(selectClause, "selectClause");

        List<VarBeanIgniteColumns.Column> columns = VarBeanIgniteColumns.from(beanType);
        String sql = buildSql(columns, select, whereClause);
        Object[] rowsArg = toRowsArgument(beanType, beans, columns);

        log.debug("IgniteVarToRowsQuery SQL:\n{}\nrows={}", sql, rowsArg.length);

        SqlFieldsQuery query = new SqlFieldsQuery(sql).setArgs(rowsArg);
        IgniteCache<?, ?> cache = sqlCache(ignite);

        List<Map<String, Object>> result = new ArrayList<>();
        try (FieldsQueryCursor<List<?>> cursor = cache.query(query)) {
            int columnCount = cursor.getColumnsCount();
            for (List<?> row : cursor) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    map.put(cursor.getFieldName(i), row.get(i));
                }
                result.add(map);
            }
        }
        return result;
    }

    /** Собирает SQL без выполнения — для отладки и unit-тестов. */
    public static String buildSql(Class<?> beanType, String selectClause) {
        return buildSql(beanType, selectClause, null);
    }

    public static String buildSql(Class<?> beanType, String selectClause, String whereClause) {
        return buildSql(
                VarBeanIgniteColumns.from(beanType),
                requireSqlFragment(selectClause, "selectClause"),
                whereClause);
    }

    static String buildSql(
            List<VarBeanIgniteColumns.Column> columns,
            String selectClause,
            String whereClause) {
        StringBuilder toRowsArgs = new StringBuilder("?");
        for (VarBeanIgniteColumns.Column column : columns) {
            toRowsArgs.append(",\n         '")
                    .append(escapeSqlLiteral(column.name()))
                    .append("', '")
                    .append(escapeSqlLiteral(column.sqlType()))
                    .append('\'');
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(selectClause.trim()).append('\n');
        sql.append("  FROM TABLE(TO_ROWS(\n");
        sql.append("         ").append(toRowsArgs).append('\n');
        sql.append("       ))");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append("\n WHERE ").append(whereClause.trim());
        }
        return sql.toString();
    }

    /**
     * Строит аргумент {@code rows} для {@code TO_ROWS}: массив строк, каждая — {@code Object[]}.
     */
    static <T> Object[] toRowsArgument(
            Class<T> beanType,
            List<T> beans,
            List<VarBeanIgniteColumns.Column> columns) {
        Object[] rows = new Object[beans.size()];
        for (int i = 0; i < beans.size(); i++) {
            rows[i] = unwrapRow(beanType, beans.get(i), columns);
        }
        return rows;
    }

    private static <T> Object[] unwrapRow(
            Class<T> beanType,
            T bean,
            List<VarBeanIgniteColumns.Column> columns) {
        if (bean == null) {
            throw new IllegalArgumentException("Bean list must not contain null elements");
        }
        Object[] row = new Object[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            VarBeanIgniteColumns.Column column = columns.get(i);
            Object raw = readProperty(beanType, bean, column.name());
            row[i] = unwrapVarValue(raw, column.name());
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
                    return pd.getReadMethod().invoke(bean);
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
     * Извлекает Java-значение для {@code TO_ROWS}; {@code null}/{@link Var#isNull()} → null.
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
        if (raw instanceof RawVar rawVar) {
            return rawVar.get();
        }
        if (raw instanceof SqlTimestampVar && value instanceof Timestamp ts) {
            return new Timestamp(ts.getTime());
        }
        if (raw instanceof TimestampVar && value instanceof OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        if (raw instanceof DateVar && value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (raw instanceof BooleanVar && value instanceof Boolean b) {
            return b;
        }
        return value;
    }

    private static IgniteCache<?, ?> sqlCache(Ignite ignite) {
        CacheConfiguration<?, ?> cfg = new CacheConfiguration<>(SQL_CACHE_NAME);
        cfg.setCacheMode(CacheMode.PARTITIONED);
        cfg.setSqlSchema("PUBLIC");
        return ignite.getOrCreateCache(cfg);
    }

    private static String requireSqlFragment(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return value.trim();
    }

    private static String escapeSqlLiteral(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SQL literal must not be blank");
        }
        if (value.indexOf('\'') >= 0) {
            throw new IllegalArgumentException("SQL literal must not contain quotes: " + value);
        }
        return value;
    }
}
