package oracle.packages.ignite.codegen.stub;

/**
 * Заглушка фабрики типов Ignite SQL (полный набор для маппинга из DDL).
 */
public final class DataTypes {

    private DataTypes() {
    }

    public static DataType Decimal() {
        return DataType.ofKind("Decimal");
    }

    public static DataType Decimal(int precision, int scale) {
        return DataType.ofPrecision("Decimal", precision, scale);
    }

    public static DataType Integer() {
        return DataType.ofKind("Integer");
    }

    public static DataType Long() {
        return DataType.ofKind("Long");
    }

    public static DataType Short() {
        return DataType.ofKind("Short");
    }

    public static DataType Byte() {
        return DataType.ofKind("Byte");
    }

    public static DataType Float() {
        return DataType.ofKind("Float");
    }

    public static DataType Double() {
        return DataType.ofKind("Double");
    }

    public static DataType Boolean() {
        return DataType.ofKind("Boolean");
    }

    public static DataType Varchar(int length) {
        return DataType.ofLength("Varchar", length);
    }

    public static DataType Char(int length) {
        return DataType.ofLength("Char", length);
    }

    public static DataType Date() {
        return DataType.ofKind("Date");
    }

    public static DataType Time() {
        return DataType.ofKind("Time");
    }

    public static DataType Timestamp() {
        return DataType.ofKind("Timestamp");
    }

    public static DataType Uuid() {
        return DataType.ofKind("Uuid");
    }

    public static DataType Binary(int length) {
        return DataType.ofLength("Binary", length);
    }

    public static DataType Varbinary(int length) {
        return DataType.ofLength("Varbinary", length);
    }
}
