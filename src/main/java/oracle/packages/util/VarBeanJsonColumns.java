package oracle.packages.util;

import oracle.packages.BinaryDoubleVar;
import oracle.packages.BinaryFloatVar;
import oracle.packages.BooleanVar;
import oracle.packages.DateVar;
import oracle.packages.NumberVar;
import oracle.packages.PlsIntegerVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Var;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Описание колонок JSON_TABLE для бинов, у которых свойства имеют тип {@link Var}.
 */
final class VarBeanJsonColumns {

    record Column(String name, String oracleType, Class<?> varType) {
    }

    private VarBeanJsonColumns() {
    }

    static List<Column> from(Class<?> beanType) {
        Objects.requireNonNull(beanType, "beanType");
        List<Column> columns = beanType.isRecord() ? fromRecord(beanType) : fromJavaBean(beanType);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bean has no readable Var properties: " + beanType.getName());
        }
        return columns;
    }

    private static List<Column> fromRecord(Class<?> beanType) {
        List<Column> columns = new ArrayList<>();
        for (RecordComponent component : beanType.getRecordComponents()) {
            Class<?> type = component.getType();
            if (!Var.class.isAssignableFrom(type)) {
                continue;
            }
            columns.add(new Column(component.getName(), oracleTypeForVar(type), type));
        }
        return columns;
    }

    private static List<Column> fromJavaBean(Class<?> beanType) {
        try {
            BeanInfo info = Introspector.getBeanInfo(beanType, Object.class);
            List<Column> columns = new ArrayList<>();
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if (pd.getReadMethod() == null) {
                    continue;
                }
                String name = pd.getName();
                if ("class".equals(name)) {
                    continue;
                }
                Class<?> type = pd.getPropertyType();
                if (!Var.class.isAssignableFrom(type)) {
                    continue;
                }
                columns.add(new Column(name, oracleTypeForVar(type), type));
            }
            return columns;
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException("Failed to introspect bean " + beanType.getName(), e);
        }
    }

    /**
     * Сопоставление класса-обёртки {@link Var} с типом колонки Oracle JSON_TABLE.
     * Даты/timestamp — {@code TIMESTAMP}, чтобы {@code ResultSet.getObject} возвращал {@link java.sql.Timestamp}.
     */
    static String oracleTypeForVar(Class<?> varType) {
        if (NumberVar.class.isAssignableFrom(varType)
                || PlsIntegerVar.class.isAssignableFrom(varType)
                || BinaryDoubleVar.class.isAssignableFrom(varType)
                || BinaryFloatVar.class.isAssignableFrom(varType)) {
            return "NUMBER";
        }
        if (SqlTimestampVar.class.isAssignableFrom(varType)
                || TimestampVar.class.isAssignableFrom(varType)) {
            // Instant/offset в JSON; WITH TIME ZONE сохраняет тот же getTime() при чтении
            return "TIMESTAMP WITH TIME ZONE";
        }
        if (DateVar.class.isAssignableFrom(varType)) {
            // ISO-8601 из JSON; колонка TIMESTAMP → JDBC Timestamp, не строка
            return "TIMESTAMP";
        }
        if (BooleanVar.class.isAssignableFrom(varType)) {
            // NUMBER 0/1 — в ResultSet приводим обратно к Boolean по имени колонки
            return "NUMBER";
        }
        // Строки, RAW (как hex/текст) и прочие Var — VARCHAR2
        return "VARCHAR2(4000)";
    }
}
