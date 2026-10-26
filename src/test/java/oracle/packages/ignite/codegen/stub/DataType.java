package oracle.packages.ignite.codegen.stub;

import java.util.Objects;

/**
 * Заглушка типа колонки (аналог {@code DataTypes.*} прикладного API).
 */
public final class DataType {

    private final String kind;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;

    private DataType(String kind, Integer length, Integer precision, Integer scale) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.length = length;
        this.precision = precision;
        this.scale = scale;
    }

    static DataType ofKind(String kind) {
        return new DataType(kind, null, null, null);
    }

    static DataType ofLength(String kind, int length) {
        return new DataType(kind, length, null, null);
    }

    static DataType ofPrecision(String kind, int precision, int scale) {
        return new DataType(kind, null, precision, scale);
    }

    public String kind() {
        return kind;
    }

    public Integer length() {
        return length;
    }

    public Integer precision() {
        return precision;
    }

    public Integer scale() {
        return scale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataType other)) {
            return false;
        }
        return kind.equals(other.kind)
                && Objects.equals(length, other.length)
                && Objects.equals(precision, other.precision)
                && Objects.equals(scale, other.scale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, length, precision, scale);
    }

    @Override
    public String toString() {
        if (length != null) {
            return kind + "(" + length + ")";
        }
        if (precision != null) {
            return kind + "(" + precision + "," + scale + ")";
        }
        return kind;
    }
}
