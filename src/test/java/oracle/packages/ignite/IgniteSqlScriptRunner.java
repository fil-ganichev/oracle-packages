package oracle.packages.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Накат SQL-скрипта на серверный (embedded) {@link Ignite} через {@link SqlFieldsQuery}
 * (движок Calcite, см. {@link IgniteTestServer}).
 */
public final class IgniteSqlScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(IgniteSqlScriptRunner.class);

    private static final String DEFAULT_SCHEMA = "PUBLIC";

    private IgniteSqlScriptRunner() {
    }

    public static void applyClasspathScript(Ignite ignite, String classpathResource) {
        Objects.requireNonNull(classpathResource, "classpathResource");
        String path = classpathResource.startsWith("/") ? classpathResource : "/" + classpathResource;
        InputStream stream = IgniteSqlScriptRunner.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + path);
        }
        try (InputStream in = stream) {
            applyScript(ignite, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read script " + path, e);
        }
    }

    public static void applyScript(Ignite ignite, InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            applyScript(ignite, reader);
        }
    }

    public static void applyScript(Ignite ignite, Reader reader) throws IOException {
        Objects.requireNonNull(ignite, "ignite");
        Objects.requireNonNull(reader, "reader");
        StringBuilder script = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            script.append(buffer, 0, read);
        }
        for (String statement : splitStatements(script.toString())) {
            executeUpdate(ignite, statement);
        }
    }

    public static void executeUpdate(Ignite ignite, String sql) {
        try (FieldsQueryCursor<List<?>> ignored = runQuery(ignite, sql)) {
            ignored.getAll();
        }
    }

    public static List<List<?>> executeQuery(Ignite ignite, String sql) {
        try (FieldsQueryCursor<List<?>> cursor = runQuery(ignite, sql)) {
            return cursor.getAll();
        }
    }

    private static FieldsQueryCursor<List<?>> runQuery(Ignite ignite, String sql) {
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        log.debug("Ignite SQL:\n{}", trimmed);
        SqlFieldsQuery query = new SqlFieldsQuery(trimmed).setSchema(DEFAULT_SCHEMA);
        IgniteCache<Object, Object> cache = IgniteTestServer.sqlCache(ignite);
        return cache.query(query);
    }

    public static List<String> splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String chunk : cleaned.toString().split(";")) {
            String statement = chunk.trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }
}
