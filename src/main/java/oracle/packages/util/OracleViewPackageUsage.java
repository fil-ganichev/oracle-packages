package oracle.packages.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Зависимости представления от пакетов ({@code USER_DEPENDENCIES}) и поиск вызовов
 * функций пакета в тексте {@code USER_VIEWS}.
 */
public final class OracleViewPackageUsage {

    private static final String SQL_VIEW_PACKAGE_DEPS = """
            SELECT REFERENCED_OWNER, REFERENCED_NAME
            FROM USER_DEPENDENCIES
            WHERE NAME = ?
              AND TYPE = 'VIEW'
              AND REFERENCED_TYPE = 'PACKAGE'
            ORDER BY REFERENCED_OWNER, REFERENCED_NAME
            """;

    private static final String SQL_USER_VIEW_TEXT = """
            SELECT TEXT
            FROM USER_VIEWS
            WHERE VIEW_NAME = ?
            """;

    private OracleViewPackageUsage() {
    }

    /**
     * Первый запрос: пакеты, от которых зависит представление текущего пользователя.
     */
    public static List<PackageDependency> listViewPackageDependencies(
            Connection connection,
            String viewName) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(viewName, "viewName");
        String normalizedView = viewName.toUpperCase(Locale.ROOT);

        List<PackageDependency> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SQL_VIEW_PACKAGE_DEPS)) {
            ps.setString(1, normalizedView);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PackageDependency(
                            rs.getString("REFERENCED_OWNER"),
                            rs.getString("REFERENCED_NAME")));
                }
            }
        }
        return result;
    }

    /**
     * Второй запрос: исходный текст представления ({@code USER_VIEWS.TEXT}).
     */
    public static String loadUserViewText(Connection connection, String viewName) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(viewName, "viewName");
        String normalizedView = viewName.toUpperCase(Locale.ROOT);

        try (PreparedStatement ps = connection.prepareStatement(SQL_USER_VIEW_TEXT)) {
            ps.setString(1, normalizedView);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("View not found in USER_VIEWS: " + normalizedView);
                }
                return rs.getString("TEXT");
            }
        }
    }

    /**
     * Ищет в тексте VIEW обращения {@code [owner.]package.function} (регистронезависимо).
     * Имена функций возвращаются в верхнем регистре, без дубликатов, в порядке первого появления.
     */
    public static Set<String> findPackageFunctionsInViewText(String viewText, String packageName) {
        Objects.requireNonNull(viewText, "viewText");
        Objects.requireNonNull(packageName, "packageName");

        String pkg = packageName.toUpperCase(Locale.ROOT);
        Pattern pattern = Pattern.compile(
                "(?:[\\w$#]+\\.)?" + Pattern.quote(pkg) + "\\.([\\w$#]+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(viewText);

        Set<String> functions = new LinkedHashSet<>();
        while (matcher.find()) {
            functions.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return functions;
    }

    /**
     * Для каждой PACKAGE-зависимости VIEW загружает текст и находит используемые функции пакета.
     */
    public static List<ResolvedPackageUsage> resolvePackageFunctionsUsedByView(
            Connection connection,
            String viewName) throws SQLException {
        List<PackageDependency> dependencies = listViewPackageDependencies(connection, viewName);
        if (dependencies.isEmpty()) {
            return List.of();
        }
        String viewText = loadUserViewText(connection, viewName);
        List<ResolvedPackageUsage> resolved = new ArrayList<>(dependencies.size());
        for (PackageDependency dependency : dependencies) {
            Set<String> functions = findPackageFunctionsInViewText(viewText, dependency.packageName());
            resolved.add(new ResolvedPackageUsage(dependency, functions));
        }
        return resolved;
    }

    public record PackageDependency(String owner, String packageName) {
        public PackageDependency {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(packageName, "packageName");
        }
    }

    public record ResolvedPackageUsage(PackageDependency dependency, Set<String> functionNames) {
        public ResolvedPackageUsage {
            Objects.requireNonNull(dependency, "dependency");
            Objects.requireNonNull(functionNames, "functionNames");
            functionNames = Set.copyOf(functionNames);
        }
    }
}
