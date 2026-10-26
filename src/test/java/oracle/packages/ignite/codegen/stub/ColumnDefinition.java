package oracle.packages.ignite.codegen.stub;

import java.util.Objects;

public class ColumnDefinition {

    private final String name;
    private final DataType type;
    private boolean notNull;

    public ColumnDefinition(String name, DataType type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public ColumnDefinition setNotNull() {
        this.notNull = true;
        return this;
    }

    public String name() {
        return name;
    }

    public DataType type() {
        return type;
    }

    public boolean notNull() {
        return notNull;
    }
}
