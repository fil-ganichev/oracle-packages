package oracle.packages.util;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

/**
 * Системные ODCI-коллекции Oracle для {@code TABLE(CAST(:bind AS ...))}.
 */
public enum OracleOdciCollectionType {

    VARCHAR2("SYS.ODCIVARCHAR2LIST", String.class),
    NUMBER("SYS.ODCINUMBERLIST", Integer.class, Long.class, BigDecimal.class),
    DATE("SYS.ODCIDATELIST", java.sql.Date.class, Date.class, Timestamp.class);

    private final String oracleTypeName;
    private final Class<?>[] supportedClasses;

    OracleOdciCollectionType(String oracleTypeName, Class<?>... supportedClasses) {
        this.oracleTypeName = oracleTypeName;
        this.supportedClasses = supportedClasses;
    }

    public String oracleTypeName() {
        return oracleTypeName;
    }

    /** SQL-фрагмент {@code CAST(:p AS SYS.ODCI...LIST)} для именованного bind. */
    public String castNamed(String bindName) {
        return "CAST(" + normalizeBindName(bindName) + " AS " + oracleTypeName + ")";
    }

    /** SQL-фрагмент {@code CAST(? AS SYS.ODCI...LIST)} для positional bind. */
    public String castPositional() {
        return "CAST(? AS " + oracleTypeName + ")";
    }

    /**
     * Определяет тип коллекции по классу элемента.
     * Для пустой коллекции класс элемента нужно передать явно.
     */
    public static OracleOdciCollectionType fromElementType(Class<?> elementType) {
        Objects.requireNonNull(elementType, "elementType");
        Class<?> normalized = normalizeClass(elementType);
        for (OracleOdciCollectionType type : values()) {
            if (type.supports(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported collection element type: " + elementType.getName());
    }

    /**
     * Тип элемента: из первого значения коллекции или явный {@code elementTypeIfEmpty}.
     */
    public static OracleOdciCollectionType fromCollection(
            Iterable<?> values,
            Class<?> elementTypeIfEmpty) {
        Objects.requireNonNull(values, "values");
        Class<?> elementType = elementTypeIfEmpty;
        for (Object value : values) {
            if (value != null) {
                elementType = value.getClass();
                break;
            }
        }
        if (elementType == null) {
            throw new IllegalArgumentException(
                    "Empty collection requires explicit elementTypeIfEmpty");
        }
        return fromElementType(elementType);
    }

    private boolean supports(Class<?> clazz) {
        for (Class<?> supported : supportedClasses) {
            if (supported.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> normalizeClass(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    private static String normalizeBindName(String bindName) {
        String name = Objects.requireNonNull(bindName, "bindName").strip();
        if (!name.startsWith(":")) {
            name = ":" + name;
        }
        if (name.length() < 2) {
            throw new IllegalArgumentException("Invalid bind name: " + bindName);
        }
        return name;
    }
}
