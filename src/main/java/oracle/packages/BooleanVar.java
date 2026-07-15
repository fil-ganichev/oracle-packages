package oracle.packages;

/** Обёртка Oracle BOOLEAN. */
public class BooleanVar extends AbstractVar<Boolean> {

    public BooleanVar() {
        super();
    }

    public BooleanVar(Boolean value) {
        super(value);
    }

    public static BooleanVar of(Boolean value) {
        return new BooleanVar(value);
    }

    public static BooleanVar nullValue() {
        return new BooleanVar(null);
    }
}
