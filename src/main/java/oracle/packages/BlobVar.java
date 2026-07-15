package oracle.packages;

/** Обёртка Oracle BLOB. */
public class BlobVar extends AbstractVar<byte[]> {

    public BlobVar() {
        super();
    }

    public BlobVar(byte[] value) {
        super(value);
    }

    public static BlobVar of(byte[] value) {
        return new BlobVar(value);
    }

    public static BlobVar empty() {
        return new BlobVar(new byte[0]);
    }

    public static BlobVar nullValue() {
        return new BlobVar(null);
    }
}
