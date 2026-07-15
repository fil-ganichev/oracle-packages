package oracle.packages;

import java.time.LocalDateTime;

/** Обёртка Oracle DATE. */
public class DateVar extends AbstractVar<LocalDateTime> {

    public DateVar() {
        super();
    }

    public DateVar(LocalDateTime value) {
        super(value);
    }

    public static DateVar of(LocalDateTime value) {
        return new DateVar(value);
    }

    public static DateVar nullValue() {
        return new DateVar(null);
    }
}
