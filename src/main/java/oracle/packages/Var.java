package oracle.packages;

/**
 * Базовый интерфейс обёртки значения Oracle.
 * @param <T> сырой Java-тип значения
 */
public interface Var<T> {

    /** Возвращает true, если значение отсутствует (NULL). */
    boolean isNull();

    /** Возвращает сырое Java-значение (может быть null). */
    T get();

    /** Устанавливает новое значение. */
    void set(T value);
}
