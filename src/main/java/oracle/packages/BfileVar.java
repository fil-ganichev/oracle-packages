package oracle.packages;

/** Обёртка Oracle BFILE (имя файла в каталоге). */
public class BfileVar extends AbstractVar<String> {

    private String directory;
    private String filename;

    public BfileVar() {
        super();
    }

    public BfileVar(String directory, String filename) {
        super(directory == null && filename == null ? null : directory + "/" + filename);
        this.directory = directory;
        this.filename = filename;
    }

    public String getDirectory() {
        return directory;
    }

    public String getFilename() {
        return filename;
    }

    public static BfileVar of(String directory, String filename) {
        return new BfileVar(directory, filename);
    }

    public static BfileVar nullValue() {
        return new BfileVar(null, null);
    }
}
