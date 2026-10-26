package oracle.packages.ignite.codegen;

import oracle.packages.ignite.IgniteSqlScriptRunner;
import oracle.packages.ignite.codegen.stub.ColumnDefinition;
import oracle.packages.ignite.codegen.stub.DataProvider;
import oracle.packages.ignite.codegen.stub.DataType;
import oracle.packages.ignite.codegen.stub.DataTypes;
import oracle.packages.ignite.codegen.stub.TableDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Разбор {@code CREATE TABLE} из Ignite SQL-скрипта и генерация вызовов
 * {@link TableDefinition} / {@link DataProvider} (прикладное API).
 */
public final class IgniteSqlCreateTableCodegen {

    private static final Pattern CREATE_TABLE_HEADER =
            Pattern.compile("(?is)CREATE\\s+TABLE\\s+(\\w+)\\s*\\(");

    private static final Pattern PRIMARY_KEY =
            Pattern.compile("(?is)PRIMARY\\s+KEY\\s*\\(([^)]+)\\)");

    private static final Pattern COLUMN =
            Pattern.compile(
                    "^\\s*(\\w+)\\s+(.+?)(?:\\s+NOT\\s+NULL)?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private IgniteSqlCreateTableCodegen() {
    }

    public record ParsedTable(String tableName, List<ParsedColumn> columns, List<String> primaryKeyColumns) {
        public ParsedTable {
            Objects.requireNonNull(tableName, "tableName");
            columns = List.copyOf(columns);
            primaryKeyColumns = List.copyOf(primaryKeyColumns);
        }
    }

    public record ParsedColumn(String name, DataType dataType, boolean notNull) {
    }

    public static List<ParsedTable> parseCreateTablesFromScript(String script) {
        List<ParsedTable> tables = new ArrayList<>();
        for (String statement : IgniteSqlScriptRunner.splitStatements(script)) {
            if (statement.regionMatches(true, 0, "CREATE TABLE", 0, "CREATE TABLE".length())) {
                tables.add(parseCreateTable(statement));
            }
        }
        return tables;
    }

    public static List<ParsedTable> parseCreateTablesFromFile(Path sqlFile) {
        Objects.requireNonNull(sqlFile, "sqlFile");
        try {
            return parseCreateTablesFromScript(Files.readString(sqlFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + sqlFile, e);
        }
    }

    public static List<ParsedTable> parseCreateTablesFromClasspath(String classpathResource) {
        String path = classpathResource.startsWith("/") ? classpathResource : "/" + classpathResource;
        try (InputStream in = IgniteSqlCreateTableCodegen.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + path);
            }
            return parseCreateTablesFromScript(new String(in.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    public static ParsedTable parseCreateTable(String ddl) {
        String normalized = ddl.trim();
        Matcher header = CREATE_TABLE_HEADER.matcher(normalized);
        if (!header.find()) {
            throw new IllegalArgumentException("Not a CREATE TABLE statement: " + ddl);
        }
        String tableName = header.group(1).toUpperCase(Locale.ROOT);
        int openParenIndex = header.end() - 1;
        int closeParenIndex = indexOfMatchingParen(normalized, openParenIndex);
        if (closeParenIndex < 0) {
            throw new IllegalArgumentException("Unclosed column list in CREATE TABLE: " + ddl);
        }
        String body = normalized.substring(openParenIndex + 1, closeParenIndex);

        Set<String> pkColumns = new LinkedHashSet<>();
        Matcher pkMatcher = PRIMARY_KEY.matcher(body);
        if (pkMatcher.find()) {
            for (String part : pkMatcher.group(1).split(",")) {
                pkColumns.add(part.trim().toUpperCase(Locale.ROOT));
            }
            body = pkMatcher.replaceFirst("");
        }

        List<ParsedColumn> columns = new ArrayList<>();
        for (String fragment : splitTopLevelCommas(body)) {
            String trimmed = fragment.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("PRIMARY KEY")) {
                continue;
            }
            Matcher columnMatcher = COLUMN.matcher(trimmed);
            if (!columnMatcher.matches()) {
                throw new IllegalArgumentException("Unsupported column definition: " + trimmed);
            }
            String columnName = columnMatcher.group(1).toUpperCase(Locale.ROOT);
            String sqlType = columnMatcher.group(2).trim();
            boolean explicitNotNull = trimmed.toUpperCase(Locale.ROOT).contains("NOT NULL");
            boolean notNull = explicitNotNull || pkColumns.contains(columnName);
            columns.add(new ParsedColumn(columnName, mapSqlType(sqlType), notNull));
        }

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No columns parsed in CREATE TABLE: " + ddl);
        }
        return new ParsedTable(tableName, columns, List.copyOf(pkColumns));
    }

    private static int indexOfMatchingParen(String text, int openIndex) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != '(') {
            return -1;
        }
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevelCommas(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
            if (ch == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    public static TableDefinition toTableDefinition(ParsedTable parsed, String schema) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(schema, "schema");

        TableDefinition table = new TableDefinition(schema, parsed.tableName());
        for (ParsedColumn column : parsed.columns()) {
            ColumnDefinition def = new ColumnDefinition(column.name(), column.dataType());
            if (column.notNull()) {
                def.setNotNull();
            }
            table.addColumn(def);
        }
        if (!parsed.primaryKeyColumns().isEmpty()) {
            table.setPrimaryKey(parsed.primaryKeyColumns().toArray(String[]::new));
        }
        return table;
    }

    public static void applyCreateTablesFromClasspath(
            String classpathResource,
            DataProvider provider,
            String schema,
            boolean replaceIfExists) {
        for (ParsedTable parsed : parseCreateTablesFromClasspath(classpathResource)) {
            provider.createTable(toTableDefinition(parsed, schema), replaceIfExists);
        }
    }

    public static String generateCreateTableMethod(
            ParsedTable parsed,
            String schema,
            String methodName,
            String providerParameterName) {
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(providerParameterName, "providerParameterName");

        StringBuilder out = new StringBuilder();
        out.append("private void ").append(methodName).append('(').append("DataProvider ")
                .append(providerParameterName).append(") {\n");
        out.append("    TableDefinition tabDef = new TableDefinition(\"")
                .append(escapeJava(schema)).append("\", \"")
                .append(escapeJava(parsed.tableName())).append("\")\n");

        for (ParsedColumn column : parsed.columns()) {
            out.append("            .addColumn(new ColumnDefinition(\"")
                    .append(escapeJava(column.name())).append("\", ")
                    .append(formatDataTypesCall(column.dataType()));
            if (column.notNull()) {
                out.append(").setNotNull()");
            } else {
                out.append(')');
            }
            out.append(")\n");
        }
        if (!parsed.primaryKeyColumns().isEmpty()) {
            out.append("            .setPrimaryKey(")
                    .append(parsed.primaryKeyColumns().stream()
                            .map(c -> "\"" + escapeJava(c) + "\"")
                            .collect(Collectors.joining(", ")))
                    .append(");\n\n");
        } else {
            out.setLength(out.length() - 1);
            out.append(";\n\n");
        }
        out.append("    ").append(providerParameterName)
                .append(".createTable(tabDef, true);\n");
        out.append("}\n");
        return out.toString();
    }

    static DataType mapSqlType(String sqlType) {
        String normalized = sqlType.trim().toUpperCase(Locale.ROOT);
        Matcher varchar = Pattern.compile("VARCHAR\\((\\d+)\\)").matcher(normalized);
        if (varchar.matches()) {
            return DataTypes.Varchar(Integer.parseInt(varchar.group(1)));
        }
        Matcher charType = Pattern.compile("CHAR\\((\\d+)\\)").matcher(normalized);
        if (charType.matches()) {
            return DataTypes.Char(Integer.parseInt(charType.group(1)));
        }
        Matcher decimal = Pattern.compile("(?:DECIMAL|NUMERIC)(?:\\((\\d+)(?:,(\\d+))?\\))?").matcher(normalized);
        if (decimal.matches()) {
            if (decimal.group(1) != null) {
                int scale = decimal.group(2) != null ? Integer.parseInt(decimal.group(2)) : 0;
                return DataTypes.Decimal(Integer.parseInt(decimal.group(1)), scale);
            }
            return DataTypes.Decimal();
        }
        Matcher binary = Pattern.compile("BINARY\\((\\d+)\\)").matcher(normalized);
        if (binary.matches()) {
            return DataTypes.Binary(Integer.parseInt(binary.group(1)));
        }
        Matcher varbinary = Pattern.compile("VARBINARY\\((\\d+)\\)").matcher(normalized);
        if (varbinary.matches()) {
            return DataTypes.Varbinary(Integer.parseInt(varbinary.group(1)));
        }

        return switch (normalized) {
            case "INT", "INTEGER" -> DataTypes.Integer();
            case "BIGINT" -> DataTypes.Long();
            case "SMALLINT" -> DataTypes.Short();
            case "TINYINT" -> DataTypes.Byte();
            case "FLOAT", "REAL" -> DataTypes.Float();
            case "DOUBLE", "DOUBLE PRECISION" -> DataTypes.Double();
            case "BOOLEAN" -> DataTypes.Boolean();
            case "DATE" -> DataTypes.Date();
            case "TIME" -> DataTypes.Time();
            case "TIMESTAMP" -> DataTypes.Timestamp();
            case "UUID" -> DataTypes.Uuid();
            default -> throw new IllegalArgumentException("Unsupported SQL type: " + sqlType);
        };
    }

    private static String formatDataTypesCall(DataType type) {
        return switch (type.kind()) {
            case "Decimal" -> type.precision() != null
                    ? "DataTypes.Decimal(" + type.precision() + ", " + type.scale() + ")"
                    : "DataTypes.Decimal()";
            case "Varchar" -> "DataTypes.Varchar(" + type.length() + ")";
            case "Char" -> "DataTypes.Char(" + type.length() + ")";
            case "Binary" -> "DataTypes.Binary(" + type.length() + ")";
            case "Varbinary" -> "DataTypes.Varbinary(" + type.length() + ")";
            default -> "DataTypes." + type.kind() + "()";
        };
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
