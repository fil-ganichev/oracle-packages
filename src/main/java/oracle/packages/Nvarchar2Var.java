package oracle.packages;

/** Обёртка Oracle NVARCHAR2. */
public class Nvarchar2Var extends AbstractVar<String> {

    public Nvarchar2Var() {
        super();
    }

    public Nvarchar2Var(String value) {
        super(value);
    }

    public static Nvarchar2Var of(String value) {
        return new Nvarchar2Var(value);
    }

    public static Nvarchar2Var nullValue() {
        return new Nvarchar2Var(null);
    }
}
