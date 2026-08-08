package oracle.packages.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обогащает SQL-{@code SELECT} и {@code INSERT} дополнительными полями из мапы
 * {@code имя_поля → выражение}.
 * <ul>
 *   <li>{@code SELECT} — ищет/добавляет псевдоним {@code new_<field>} (snake_case)</li>
 *   <li>{@code INSERT} — ищет/добавляет колонку {@code <field>} без префикса {@code new_}</li>
 * </ul>
 * <p>
 * Значения из мапы проходят через {@link #transformFieldExpression(String)}:
 * {@code seq.nextval} / {@code schema.seq.nextval} → {@code dbms_seq.nextval('seq')}.
 */
public final class SqlNewFieldsEnricher {

    public static final String NEW_PREFIX = "new_";

    private static final Pattern SELECT_HEAD = Pattern.compile(
            "^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern INSERT_HEAD = Pattern.compile(
            "^\\s*INSERT\\s+INTO\\s+([\\w.\"$]+)\\s*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SqlNewFieldsEnricher() {
    }

    /**
     * Извлекает имя таблицы из {@code INSERT INTO <table> ...}.
     * Поддерживает {@code schema.table} и закавыченные идентификаторы.
     *
     * @throws IllegalArgumentException если это не INSERT INTO
     */
    public static String extractInsertTableName(String sql) {
        Objects.requireNonNull(sql, "sql");
        Matcher insertMatcher = INSERT_HEAD.matcher(sql);
        if (!insertMatcher.find()) {
            throw new IllegalArgumentException("Not an INSERT statement");
        }
        return insertMatcher.group(1);
    }

    /**
     * Обогащает {@code SELECT}: для каждой пары {@code field → expression} добавляет
     * {@code expression new_field}, если псевдонима {@code new_field} ещё нет.
     */
    public static String enrichSelect(String sql, Map<String, String> fieldExpressions) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(fieldExpressions, "fieldExpressions");
        if (!SELECT_HEAD.matcher(sql).find()) {
            throw new IllegalArgumentException("Not a SELECT statement");
        }
        if (fieldExpressions.isEmpty()) {
            return sql;
        }

        SelectParts parts = splitSelect(sql);
        Set<String> existingAliases = extractSelectAliases(parts.selectList);
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<String, String> entry : fieldExpressions.entrySet()) {
            String alias = selectAlias(entry.getKey());
            if (existingAliases.contains(alias.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String expression = requireExpression(entry.getKey(), entry.getValue());
            extra.append(", ").append(expression).append(' ').append(alias);
            existingAliases.add(alias.toLowerCase(Locale.ROOT));
        }
        if (extra.isEmpty()) {
            return sql;
        }
        return parts.beforeSelectList
                + parts.selectList
                + extra
                + " "
                + parts.afterSelectList.stripLeading();
    }

    /**
     * Обогащает {@code INSERT} ({@code INSERT ... VALUES} или {@code INSERT ... SELECT}).
     * Список колонок обязателен; иначе — ошибка.
     * Добавляет колонки по имени поля <b>без</b> префикса {@code new_} и соответствующие
     * выражения/значения, если такой колонки ещё нет.
     */
    public static String enrichInsert(String sql, Map<String, String> fieldExpressions) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(fieldExpressions, "fieldExpressions");

        Matcher insertMatcher = INSERT_HEAD.matcher(sql);
        if (!insertMatcher.find()) {
            throw new IllegalArgumentException("Not an INSERT statement");
        }
        int afterTable = insertMatcher.end();
        String rest = sql.substring(afterTable).stripLeading();

        if (!rest.startsWith("(")) {
            throw new IllegalArgumentException(
                    "INSERT must explicitly list columns; column list is missing");
        }

        int columnsEnd = findMatchingParen(rest, 0);
        String columnsRaw = rest.substring(1, columnsEnd);
        String afterColumns = rest.substring(columnsEnd + 1).stripLeading();

        List<String> columns = splitCsvRespectingParens(columnsRaw);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "INSERT must explicitly list columns; column list is empty");
        }

        Set<String> existing = new LinkedHashSet<>();
        for (String column : columns) {
            existing.add(normalizeIdent(column));
        }

        List<String> addedColumns = new ArrayList<>();
        List<String> addedExpressions = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldExpressions.entrySet()) {
            String column = insertColumnName(entry.getKey());
            if (existing.contains(column.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String expression = requireExpression(entry.getKey(), entry.getValue());
            addedColumns.add(column);
            addedExpressions.add(expression);
            existing.add(column.toLowerCase(Locale.ROOT));
        }

        if (addedColumns.isEmpty()) {
            return sql;
        }

        String newColumnsSql = columnsRaw.stripTrailing()
                + ", "
                + String.join(", ", addedColumns);

        String prefix = sql.substring(0, afterTable) + "(" + newColumnsSql + ") ";

        if (startsWithKeyword(afterColumns, "VALUES")) {
            return prefix + enrichInsertValues(afterColumns, addedExpressions);
        }
        if (startsWithKeyword(afterColumns, "SELECT")) {
            return prefix + appendSelectItems(afterColumns, addedColumns, addedExpressions);
        }
        throw new IllegalArgumentException(
                "Unsupported INSERT form: expected VALUES or SELECT after column list");
    }

    /** Дописывает в SELECT элементы {@code expr col} без префикса {@code new_}. */
    private static String appendSelectItems(
            String selectSql,
            List<String> columns,
            List<String> expressions) {
        SelectParts parts = splitSelect(selectSql);
        StringBuilder extra = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            extra.append(", ").append(expressions.get(i)).append(' ').append(columns.get(i));
        }
        return parts.beforeSelectList
                + parts.selectList
                + extra
                + " "
                + parts.afterSelectList.stripLeading();
    }

    private static String enrichInsertValues(String valuesClause, List<String> addedExpressions) {
        // VALUES (...), (...), ...
        int valuesKwEnd = skipKeyword(valuesClause, "VALUES");
        String tuplesPart = valuesClause.substring(valuesKwEnd).stripLeading();
        List<String> tuples = splitTopLevelTuples(tuplesPart);
        List<String> enriched = new ArrayList<>();
        for (String tuple : tuples) {
            String stripped = tuple.strip();
            if (!stripped.startsWith("(") || !stripped.endsWith(")")) {
                throw new IllegalArgumentException("Invalid VALUES tuple: " + tuple);
            }
            String inner = stripped.substring(1, stripped.length() - 1);
            StringBuilder sb = new StringBuilder("(");
            sb.append(inner.stripTrailing());
            for (String expression : addedExpressions) {
                sb.append(", ").append(expression);
            }
            sb.append(')');
            enriched.add(sb.toString());
        }
        String trailing = extractTrailingAfterTuples(tuplesPart, tuples);
        return "VALUES " + String.join(", ", enriched) + trailing;
    }

    private static String extractTrailingAfterTuples(String tuplesPart, List<String> tuples) {
        int consumed = 0;
        String remaining = tuplesPart;
        for (int i = 0; i < tuples.size(); i++) {
            remaining = remaining.stripLeading();
            if (!remaining.startsWith("(")) {
                break;
            }
            int end = findMatchingParen(remaining, 0);
            consumed += tuplesPart.length() - remaining.length() + end + 1;
            remaining = remaining.substring(end + 1);
            if (i < tuples.size() - 1) {
                remaining = remaining.stripLeading();
                if (remaining.startsWith(",")) {
                    remaining = remaining.substring(1);
                    consumed = tuplesPart.length() - remaining.length();
                }
            }
        }
        // Проще: от исходной строки откусить распознанные кортежи
        String s = tuplesPart;
        for (int i = 0; i < tuples.size(); i++) {
            s = s.stripLeading();
            int end = findMatchingParen(s, 0);
            s = s.substring(end + 1);
            if (i < tuples.size() - 1) {
                s = s.stripLeading();
                if (s.startsWith(",")) {
                    s = s.substring(1);
                }
            }
        }
        return s;
    }

    private static List<String> splitTopLevelTuples(String tuplesPart) {
        List<String> result = new ArrayList<>();
        String s = tuplesPart.stripLeading();
        while (!s.isEmpty() && s.charAt(0) == '(') {
            int end = findMatchingParen(s, 0);
            result.add(s.substring(0, end + 1));
            s = s.substring(end + 1).stripLeading();
            if (s.startsWith(",")) {
                s = s.substring(1).stripLeading();
            } else {
                break;
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("INSERT VALUES has no value tuples");
        }
        return result;
    }

    private record SelectParts(String beforeSelectList, String selectList, String afterSelectList) {
    }

    private static SelectParts splitSelect(String sql) {
        Matcher m = SELECT_HEAD.matcher(sql);
        if (!m.find()) {
            throw new IllegalArgumentException("Not a SELECT statement");
        }
        int listStart = m.end();
        int fromPos = findTopLevelKeyword(sql, listStart, "FROM");
        if (fromPos < 0) {
            throw new IllegalArgumentException("SELECT without FROM clause");
        }
        return new SelectParts(
                sql.substring(0, listStart),
                sql.substring(listStart, fromPos).stripTrailing(),
                sql.substring(fromPos));
    }

    /**
     * Собирает алиасы элементов SELECT-списка (в нижнем регистре).
     * Поддерживает {@code expr alias}, {@code expr AS alias}.
     */
    static Set<String> extractSelectAliases(String selectList) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String item : splitCsvRespectingParens(selectList)) {
            String alias = extractAlias(item.strip());
            if (alias != null) {
                aliases.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        return aliases;
    }

    static String extractAlias(String selectItem) {
        String s = selectItem.strip();
        if (s.isEmpty()) {
            return null;
        }
        // AS alias
        Matcher as = Pattern.compile("\\s+AS\\s+([\\w\"$]+)\\s*$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (as.find()) {
            return normalizeIdent(as.group(1));
        }
        // trailing bare alias (not after operator-like token alone)
        Matcher bare = Pattern.compile("^(.*\\S)\\s+([A-Za-z_][\\w$]*)\\s*$").matcher(s);
        if (bare.find()) {
            String left = bare.group(1).stripTrailing();
            // не путать с одиночным идентификатором без алиаса
            if (!left.isEmpty() && !isSimpleIdent(left)) {
                return normalizeIdent(bare.group(2));
            }
            if (isSimpleIdent(left)) {
                // "col alias" или просто "col"? Если два идентификатора — второе алиас
                return normalizeIdent(bare.group(2));
            }
        }
        if (isSimpleIdent(s)) {
            return normalizeIdent(s);
        }
        return null;
    }

    private static boolean isSimpleIdent(String s) {
        return s.matches("[\\w\"$]+") || s.matches("\"[^\"]+\"");
    }

    /** Псевдоним для SELECT: {@code new_} + snake_case имени поля. */
    static String selectAlias(String fieldName) {
        String snake = fieldSnake(fieldName);
        return NEW_PREFIX + snake;
    }

    /** Имя колонки для INSERT: snake_case без префикса {@code new_}. */
    static String insertColumnName(String fieldName) {
        return fieldSnake(fieldName);
    }

    private static String fieldSnake(String fieldName) {
        String snake = toSnakeCase(fieldName.strip());
        if (snake.isEmpty()) {
            throw new IllegalArgumentException("Field name must not be blank");
        }
        if (snake.regionMatches(true, 0, NEW_PREFIX, 0, NEW_PREFIX.length())) {
            snake = snake.substring(NEW_PREFIX.length());
        }
        if (snake.isEmpty()) {
            throw new IllegalArgumentException("Field name must not be blank");
        }
        return snake.toLowerCase(Locale.ROOT);
    }

    static String toSnakeCase(String name) {
        if (name.indexOf('_') >= 0 || name.equals(name.toLowerCase(Locale.ROOT))) {
            return name;
        }
        // STATUS → status, а не S_T_A_T_U_S
        if (name.equals(name.toUpperCase(Locale.ROOT))) {
            return name.toLowerCase(Locale.ROOT);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Преобразует dot-выражение sequence.nextval в вызов {@code dbms_seq.nextval('...')}.
     * <ul>
     *   <li>{@code seq.nextval} → {@code dbms_seq.nextval('seq')}</li>
     *   <li>{@code schema.seq.nextval} → {@code dbms_seq.nextval('seq')}</li>
     * </ul>
     * Части могут быть в двойных кавычках — кавычки снимаются. Иначе строка не меняется.
     */
    public static String transformFieldExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String trimmed = expression.strip();
        List<String> parts = splitDotQualified(trimmed);
        if (parts.size() != 2 && parts.size() != 3) {
            return expression;
        }
        String lastPart = unquoteIdent(parts.get(parts.size() - 1));
        if (!"nextval".equalsIgnoreCase(lastPart)) {
            return expression;
        }
        String sequenceName = unquoteIdent(parts.get(parts.size() == 2 ? 0 : 1));
        if (sequenceName.isEmpty()) {
            return expression;
        }
        return "dbms_seq.nextval('" + escapeSqlStringLiteral(sequenceName) + "')";
    }

    private static String requireExpression(String field, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression for field '" + field + "' must not be blank");
        }
        return transformFieldExpression(expression.strip());
    }

    /** Делит идентификатор вида {@code a.b."c"} по точкам вне кавычек. */
    static List<String> splitDotQualified(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDouble = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                inDouble = !inDouble;
                current.append(c);
            } else if (c == '.' && !inDouble) {
                if (!current.isEmpty()) {
                    parts.add(current.toString().strip());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }

    static String unquoteIdent(String ident) {
        String s = ident.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String escapeSqlStringLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String normalizeIdent(String ident) {
        String s = ident.strip();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).toLowerCase(Locale.ROOT);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    static List<String> splitCsvRespectingParens(String csv) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
                continue;
            }
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parts.add(current.toString().strip());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }

    private static int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced parentheses in SQL");
    }

    private static int findTopLevelKeyword(String sql, int from, String keyword) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        Pattern kw = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                Matcher m = kw.matcher(sql);
                if (m.find(i) && m.start() == i) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean startsWithKeyword(String s, String keyword) {
        Matcher m = Pattern.compile("^" + keyword + "\\b", Pattern.CASE_INSENSITIVE).matcher(s.stripLeading());
        return m.find();
    }

    private static int skipKeyword(String s, String keyword) {
        Matcher m = Pattern.compile("^\\s*" + keyword + "\\b", Pattern.CASE_INSENSITIVE).matcher(s);
        if (!m.find()) {
            throw new IllegalArgumentException("Expected keyword " + keyword);
        }
        return m.end();
    }
}
