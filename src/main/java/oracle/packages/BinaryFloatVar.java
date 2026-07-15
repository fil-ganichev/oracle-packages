package oracle.packages;

/** Обёртка Oracle BINARY_FLOAT. */
public class BinaryFloatVar extends AbstractVar<Float> {

    public BinaryFloatVar() {
        super();
    }

    public BinaryFloatVar(Float value) {
        super(value);
    }

    public static BinaryFloatVar of(Float value) {
        return new BinaryFloatVar(value);
    }

    public static BinaryFloatVar nullValue() {
        return new BinaryFloatVar(null);
    }
}
