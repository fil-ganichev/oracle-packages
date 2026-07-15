package oracle.packages;

/** Обёртка Oracle BINARY_DOUBLE. */
public class BinaryDoubleVar extends AbstractVar<Double> {

    public BinaryDoubleVar() {
        super();
    }

    public BinaryDoubleVar(Double value) {
        super(value);
    }

    public static BinaryDoubleVar of(Double value) {
        return new BinaryDoubleVar(value);
    }

    public static BinaryDoubleVar nullValue() {
        return new BinaryDoubleVar(null);
    }
}
