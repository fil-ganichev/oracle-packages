package oracle.packages.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Описание колонок JSON_TABLE, выведенное из POJO / record через рефлексию.
 */
final class BeanJsonColumns {

    record Column(String name, String oracleType) {
    }

    private BeanJsonColumns() {
    }

    static List<Column> from(Class<?> beanType) {
        Objects.requireNonNull(beanType, "beanType");
        if (beanType.isRecord()) {
            return fromRecord(beanType);
        }
        return fromJavaBean(beanType);
    }

    private static List<Column> fromRecord(Class<?> beanType) {
        List<Column> columns = new ArrayList<>();
        for (RecordComponent component : beanType.getRecordComponents()) {
            columns.add(new Column(component.getName(), oracleType(component.getType())));
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("У record нет компонентов: " + beanType.getName());
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
                columns.add(new Column(name, oracleType(pd.getPropertyType())));
            }
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("У бина нет читаемых свойств: " + beanType.getName());
            }
            return columns;
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException("Не удалось разобрать бин " + beanType.getName(), e);
        }
    }

    static String oracleType(Class<?> javaType) {
        Class<?> type = wrap(javaType);
        if (Number.class.isAssignableFrom(type)) {
            return "NUMBER";
        }
        if (CharSequence.class.isAssignableFrom(type)
                || type == Character.class
                || type == Boolean.class
                || type.isEnum()
                || Temporal.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || type.isArray()) {
            return "VARCHAR2(4000)";
        }
        return "VARCHAR2(4000)";
    }

    private static Class<?> wrap(Class<?> type) {
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
}
