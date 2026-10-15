package oracle.packages.object;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Создание экземпляров Oracle-подобных OBJECT TYPE по классу и значениям полей.
 * <p>
 * Алгоритм:
 * <ol>
 *   <li>Собрать {@code public} нестатические поля от конкретного класса вверх до {@link ObjType}</li>
 *   <li>Найти публичный конструктор с тем же числом параметров</li>
 *   <li>Вызвать конструктор с переданными значениями (по порядку полей или по имени)</li>
 * </ol>
 */
public final class OracleObjectInstances {

    private static final String TYPE_SUFFIX = "ObjType";
    private static final String IMPL_SUFFIX = "ObjImpl";

    private OracleObjectInstances() {
    }

    /**
     * По экземпляру {@link ObjType} создаёт парный {@code *ObjImpl} в том же пакете:
     * {@code AddressObjType} → {@code AddressObjImpl}, фабрика {@code addressObjImpl()}.
     * Совпадающие {@code public} поля копируются из {@code instance} в impl.
     */
    public static Object createImpl(ObjType instance) {
        Objects.requireNonNull(instance, "instance");
        Class<?> typeClass = instance.getClass();
        Class<?> implClass = resolveImplClass(typeClass);
        Object impl = invokeImplStaticFactory(implClass);
        copyMatchingPublicFields(instance, impl);
        return impl;
    }

    /** FQN impl-класса: префикс до {@code ObjType} + {@code ObjImpl}, тот же package. */
    static String resolveImplClassName(Class<?> objTypeClass) {
        Objects.requireNonNull(objTypeClass, "objTypeClass");
        String simpleName = objTypeClass.getSimpleName();
        if (!simpleName.endsWith(TYPE_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Class name must end with '" + TYPE_SUFFIX + "': " + simpleName);
        }
        String prefix = simpleName.substring(0, simpleName.length() - TYPE_SUFFIX.length());
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid ObjType class name (empty prefix): " + simpleName);
        }
        String implSimpleName = prefix + IMPL_SUFFIX;
        String pkg = objTypeClass.getPackageName();
        return pkg.isEmpty() ? implSimpleName : pkg + "." + implSimpleName;
    }

    static Class<?> resolveImplClass(Class<?> objTypeClass) {
        return resolveClass(resolveImplClassName(objTypeClass));
    }

    static String staticFactoryMethodName(Class<?> implClass) {
        return decapitalize(implClass.getSimpleName());
    }

    static Object invokeImplStaticFactory(Class<?> implClass) {
        String methodName = staticFactoryMethodName(implClass);
        try {
            Method factory = implClass.getMethod(methodName);
            if (!Modifier.isStatic(factory.getModifiers())) {
                throw new IllegalArgumentException(
                        "Factory method must be static: " + implClass.getName() + "." + methodName);
            }
            if (factory.getParameterCount() != 0) {
                throw new IllegalArgumentException(
                        "Factory method must have no parameters: " + implClass.getName() + "." + methodName);
            }
            return factory.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Static factory not found: " + implClass.getName() + "." + methodName + "()", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Failed to invoke factory on " + implClass.getName() + ": " + e.getMessage(), e);
        }
    }

    private static void copyMatchingPublicFields(Object source, Object target) {
        for (Field sourceField : source.getClass().getFields()) {
            if (!isPublicInstanceField(sourceField)) {
                continue;
            }
            try {
                Field targetField = target.getClass().getField(sourceField.getName());
                if (!isPublicInstanceField(targetField)) {
                    continue;
                }
                targetField.set(target, sourceField.get(source));
            } catch (NoSuchFieldException ignored) {
                // поля нет в impl — пропускаем
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(
                        "Failed to copy field '" + sourceField.getName() + "'", e);
            }
        }
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name.toLowerCase();
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Создаёт объект по {@code Class} и значениям полей в порядке объявления (подкласс → предки).
     */
    public static <T extends ObjType> T create(Class<T> type, Object... fieldValues) {
        Objects.requireNonNull(type, "type");
        List<Field> fields = collectPublicFields(type);
        if (fieldValues == null) {
            fieldValues = new Object[0];
        }
        if (fields.size() != fieldValues.length) {
            throw new IllegalArgumentException(
                    "Field count mismatch for " + type.getName()
                            + ": expected " + fields.size() + ", got " + fieldValues.length);
        }
        Object[] args = new Object[fieldValues.length];
        for (int i = 0; i < fields.size(); i++) {
            args[i] = coerceValue(fieldValues[i], fields.get(i).getType());
        }
        return invokeMatchingConstructor(type, fields.size(), args);
    }

    /**
     * Создаёт объект по имени класса (FQN), например {@code oracle.packages.object.EmployeeObjType}.
     */
    public static ObjType create(String className, Object... fieldValues) {
        Objects.requireNonNull(className, "className");
        Class<?> raw = resolveClass(className.strip());
        if (!ObjType.class.isAssignableFrom(raw)) {
            throw new IllegalArgumentException(
                    "Class does not extend ObjType: " + className);
        }
        @SuppressWarnings("unchecked")
        Class<? extends ObjType> type = (Class<? extends ObjType>) raw;
        return create(type, fieldValues);
    }

    /**
     * Создаёт объект по карте {@code имя_поля → значение}; порядок аргументов конструктора — как у полей.
     */
    public static <T extends ObjType> T create(Class<T> type, Map<String, Object> valuesByFieldName) {
        Objects.requireNonNull(valuesByFieldName, "valuesByFieldName");
        List<Field> fields = collectPublicFields(type);
        Object[] args = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            if (!valuesByFieldName.containsKey(field.getName())) {
                throw new IllegalArgumentException(
                        "Missing value for field '" + field.getName() + "' on " + type.getName());
            }
            args[i] = coerceValue(valuesByFieldName.get(field.getName()), field.getType());
        }
        return invokeMatchingConstructor(type, fields.size(), args);
    }

    /**
     * Публичные нестатические поля: от {@code type} вверх до (не включая) {@link ObjType}.
     * На каждом уровне — в порядке объявления в исходнике.
     */
    public static List<Field> collectPublicFields(Class<? extends ObjType> type) {
        Objects.requireNonNull(type, "type");
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type;
                current != null
                        && current != ObjType.class
                        && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (isPublicInstanceField(field)) {
                    fields.add(field);
                }
            }
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("No public instance fields on " + type.getName());
        }
        return fields;
    }

    /** Имена полей в порядке конструктора — для отладки и тестов. */
    public static List<String> fieldNamesInConstructorOrder(Class<? extends ObjType> type) {
        return collectPublicFields(type).stream().map(Field::getName).toList();
    }

    private static boolean isPublicInstanceField(Field field) {
        int mod = field.getModifiers();
        return Modifier.isPublic(mod) && !Modifier.isStatic(mod);
    }

    private static Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "Cannot assign " + value.getClass().getName() + " to field type " + targetType.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends ObjType> T invokeMatchingConstructor(
            Class<T> type,
            int parameterCount,
            Object[] args) {
        Constructor<?> match = null;
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == parameterCount) {
                match = constructor;
                break;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException(
                    "No public constructor with " + parameterCount + " parameters on " + type.getName());
        }
        try {
            return (T) match.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Failed to invoke constructor on " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }
}
