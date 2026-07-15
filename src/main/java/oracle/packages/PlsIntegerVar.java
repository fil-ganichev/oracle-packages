package oracle.packages;

/** Обёртка Oracle PLS_INTEGER / BINARY_INTEGER / INTEGER / NATURAL / POSITIVE / SIGNTYPE. */
public class PlsIntegerVar extends AbstractVar<Integer> {

    public PlsIntegerVar() {
        super();
    }

    public PlsIntegerVar(Integer value) {
        super(value);
    }

    public static PlsIntegerVar of(Integer value) {
        return new PlsIntegerVar(value);
    }

    public static PlsIntegerVar nullValue() {
        return new PlsIntegerVar(null);
    }
}
