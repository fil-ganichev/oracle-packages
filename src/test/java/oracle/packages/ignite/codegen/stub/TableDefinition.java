package oracle.packages.ignite.codegen.stub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TableDefinition {

    private final String schema;
    private final String tableName;
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private List<String> primaryKey = List.of();

    public TableDefinition(String schema, String tableName) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.tableName = Objects.requireNonNull(tableName, "tableName");
    }

    public TableDefinition addColumn(ColumnDefinition column) {
        columns.add(Objects.requireNonNull(column, "column"));
        return this;
    }

    public TableDefinition setPrimaryKey(String... columnNames) {
        primaryKey = List.of(columnNames);
        return this;
    }

    public String schema() {
        return schema;
    }

    public String tableName() {
        return tableName;
    }

    public List<ColumnDefinition> columns() {
        return Collections.unmodifiableList(columns);
    }

    public List<String> primaryKey() {
        return primaryKey;
    }
}
