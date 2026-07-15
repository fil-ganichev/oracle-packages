package oracle.packages;

/** Обёртка Oracle NCLOB. */
public class NclobVar extends AbstractVar<String> {

    public NclobVar() {
        super();
    }

    public NclobVar(String value) {
        super(value);
    }

    public static NclobVar of(String value) {
        return new NclobVar(value);
    }

    public static NclobVar nullValue() {
        return new NclobVar(null);
    }
}
