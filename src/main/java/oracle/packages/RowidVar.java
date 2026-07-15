package oracle.packages;

/** Обёртка Oracle ROWID. */
public class RowidVar extends AbstractVar<String> {

    public RowidVar() {
        super();
    }

    public RowidVar(String value) {
        super(value);
    }

    public static RowidVar of(String value) {
        return new RowidVar(value);
    }

    public static RowidVar nullValue() {
        return new RowidVar(null);
    }
}
