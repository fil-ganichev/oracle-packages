package oracle.packages.util;

import oracle.jdbc.OracleConnection;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * JDBC-привязка Java-коллекции к {@code CAST(? AS SYS.ODCI*LIST)} / именованному bind.
 */
public final class OracleTableCollectionBind {

    private OracleTableCollectionBind() {
    }

    public static void setCollection(
            Connection connection,
            PreparedStatement pstmt,
            int parameterIndex,
            Collection<?> values,
            Class<?> elementTypeIfEmpty) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(pstmt, "pstmt");
        Objects.requireNonNull(values, "values");
        OracleOdciCollectionType type = OracleOdciCollectionType.fromCollection(values, elementTypeIfEmpty);
        Object[] bindArray = toBindArray(values, type, elementTypeIfEmpty);
        Array sqlArray = createOracleArray(connection, type, bindArray);
        try {
            pstmt.setArray(parameterIndex, sqlArray);
        } finally {
            sqlArray.free();
        }
    }

    static Object[] toBindArray(
            Collection<?> values,
            OracleOdciCollectionType type,
            Class<?> elementTypeIfEmpty) {
        List<Object> converted = new ArrayList<>(values.size());
        for (Object value : values) {
            converted.add(convertElement(value, type));
        }
        if (converted.isEmpty() && elementTypeIfEmpty != null) {
            // пустая коллекция — валидный пустой массив нужного типа
            return new Object[0];
        }
        return converted.toArray();
    }

    static Object convertElement(Object value, OracleOdciCollectionType type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case VARCHAR2 -> value.toString();
            case NUMBER -> toNumberBind(value);
            case DATE -> toDateBind(value);
        };
    }

    private static Object toNumberBind(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        throw new IllegalArgumentException("Not a numeric value: " + value.getClass().getName());
    }

    /**
     * {@link Timestamp} в коллекции дат сохраняем как {@link Timestamp} (Oracle DATE list принимает).
     */
    private static Object toDateBind(Object value) {
        if (value instanceof Timestamp ts) {
            return ts;
        }
        if (value instanceof Date d) {
            return d;
        }
        if (value instanceof java.util.Date ud) {
            return new Timestamp(ud.getTime());
        }
        throw new IllegalArgumentException("Not a date value: " + value.getClass().getName());
    }

    private static Array createOracleArray(
            Connection connection,
            OracleOdciCollectionType type,
            Object[] elements) throws SQLException {
        String typeName = type.oracleTypeName();
        if (connection.isWrapperFor(OracleConnection.class)) {
            OracleConnection oracle = connection.unwrap(OracleConnection.class);
            return oracle.createOracleArray(typeName, elements);
        }
        return connection.createArrayOf(typeName, elements);
    }
}
