package oracle.packages.util;

import oracle.packages.BinaryDoubleVar;
import oracle.packages.BinaryFloatVar;
import oracle.packages.BooleanVar;
import oracle.packages.DateVar;
import oracle.packages.NumberVar;
import oracle.packages.PlsIntegerVar;
import oracle.packages.RawVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Var;
import oracle.packages.Varchar2Var;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Описание колонок Ignite {@code TO_ROWS} для бинов со свойствами {@link Var}.
 */
final class VarBeanIgniteColumns {

    record Column(String name, String sqlType, Class<?> varType) {
    }

    private VarBeanIgniteColumns() {
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
            columns.add(new Column(component.getName(), sqlTypeForVar(type), type));
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
                columns.add(new Column(name, sqlTypeForVar(type), type));
            }
            return columns;
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException("Failed to introspect bean " + beanType.getName(), e);
        }
    }

    /**
     * Сопоставление {@link Var} с SQL-типом колонки {@code TO_ROWS}
     * (кастомная функция Ignite).
     */
    static String sqlTypeForVar(Class<?> varType) {
        if (PlsIntegerVar.class.isAssignableFrom(varType)) {
            return "INTEGER";
        }
        if (BinaryDoubleVar.class.isAssignableFrom(varType)) {
            return "DOUBLE";
        }
        if (BinaryFloatVar.class.isAssignableFrom(varType)) {
            return "FLOAT";
        }
        if (NumberVar.class.isAssignableFrom(varType)) {
            return "DECIMAL";
        }
        if (BooleanVar.class.isAssignableFrom(varType)) {
            return "BOOLEAN";
        }
        if (SqlTimestampVar.class.isAssignableFrom(varType)
                || TimestampVar.class.isAssignableFrom(varType)
                || DateVar.class.isAssignableFrom(varType)) {
            return "TIMESTAMP";
        }
        if (RawVar.class.isAssignableFrom(varType)) {
            return "VARBINARY";
        }
        if (Varchar2Var.class.isAssignableFrom(varType)) {
            return "VARCHAR";
        }
        // Прочие строковые Var
        return "VARCHAR";
    }
}
