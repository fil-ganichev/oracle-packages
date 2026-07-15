package oracle.packages;

/** Обёртка Oracle MLSLABEL (используется редко, для совместимости сигнатур). */
public class MlsLabelVar extends AbstractVar<String> {

    public MlsLabelVar() {
        super();
    }

    public MlsLabelVar(String value) {
        super(value);
    }

    public static MlsLabelVar of(String value) {
        return new MlsLabelVar(value);
    }

    public static MlsLabelVar nullValue() {
        return new MlsLabelVar(null);
    }
}
