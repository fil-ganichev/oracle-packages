package oracle.packages;

/** Обёртка Oracle VARCHAR2. */
public class Varchar2Var extends AbstractVar<String> {

    public Varchar2Var() {
        super();
    }

    public Varchar2Var(String value) {
        super(value);
    }

    public static Varchar2Var of(String value) {
        return new Varchar2Var(value);
    }

    public static Varchar2Var nullValue() {
        return new Varchar2Var(null);
    }
}
