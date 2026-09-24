package oracle.packages.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подстановка {@code CAST(... AS SYS.ODCI*LIST)} в {@code TABLE(...)} по типу элементов коллекции.
 */
public final class OracleTableCollectionSql {

    private static final Pattern TABLE_POSITIONAL_BIND = Pattern.compile(
            "TABLE\\s*\\(\\s*\\?\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private OracleTableCollectionSql() {
    }

    /**
     * Заменяет {@code TABLE(:param)} на {@code TABLE(CAST(:param AS SYS.ODCI...LIST))}
     * в зависимости от типа элементов.
     *
     * @param paramName имя без двоеточия или с ним, например {@code p_ids} или {@code :p_ids}
     */
    public static String rewriteNamedTableBind(String sql, String paramName, Class<?> elementType) {
        Objects.requireNonNull(sql, "sql");
        OracleOdciCollectionType collectionType = OracleOdciCollectionType.fromElementType(elementType);
        String normalized = paramName.strip();
        if (!normalized.startsWith(":")) {
            normalized = ":" + normalized;
        }
        String replacement = "TABLE(" + collectionType.castNamed(normalized) + ")";
        Pattern pattern = Pattern.compile(
                "TABLE\\s*\\(\\s*" + Pattern.quote(normalized) + "\\s*\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "SQL does not contain TABLE(" + normalized + "): " + sql);
        }
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Заменяет все {@code TABLE(:bind)} на типизированный {@code CAST} по карте
     * {@code paramName → elementType}.
     */
    public static String rewriteNamedTableBinds(
            String sql,
            java.util.Map<String, Class<?>> paramElementTypes) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(paramElementTypes, "paramElementTypes");
        String result = sql;
        for (var entry : paramElementTypes.entrySet()) {
            result = rewriteNamedTableBind(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Заменяет {@code TABLE(?)} на {@code TABLE(CAST(? AS SYS.ODCI...LIST))}.
     */
    public static String rewritePositionalTableBind(String sql, Class<?> elementType) {
        Objects.requireNonNull(sql, "sql");
        OracleOdciCollectionType collectionType = OracleOdciCollectionType.fromElementType(elementType);
        String replacement = "TABLE(" + collectionType.castPositional() + ")";
        Matcher matcher = TABLE_POSITIONAL_BIND.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalArgumentException("SQL does not contain TABLE(?): " + sql);
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }
}
