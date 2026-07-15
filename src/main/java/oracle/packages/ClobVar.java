package oracle.packages;

/** Обёртка Oracle CLOB. */
public class ClobVar extends AbstractVar<String> {

    public ClobVar() {
        super();
    }

    public ClobVar(String value) {
        super(value);
    }

    public static ClobVar of(String value) {
        return new ClobVar(value);
    }

    public static ClobVar empty() {
        return new ClobVar("");
    }

    public static ClobVar nullValue() {
        return new ClobVar(null);
    }
}
