package oracle.packages;

/**
 * Абстрактная реализация {@link Var} с хранением nullable-значения.
 */
public abstract class AbstractVar<T> implements Var<T> {

    private T value;

    protected AbstractVar() {
        this.value = null;
    }

    protected AbstractVar(T value) {
        this.value = value;
    }

    @Override
    public boolean isNull() {
        return value == null;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void set(T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return isNull() ? "NULL" : String.valueOf(value);
    }
}
