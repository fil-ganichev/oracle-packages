package oracle.packages;

import java.time.OffsetDateTime;

/**
 * Обёртка Oracle TIMESTAMP / TIMESTAMP WITH TIME ZONE / TIMESTAMP WITH LOCAL TIME ZONE.
 */
public class TimestampVar extends AbstractVar<OffsetDateTime> {

    public TimestampVar() {
        super();
    }

    public TimestampVar(OffsetDateTime value) {
        super(value);
    }

    public static TimestampVar of(OffsetDateTime value) {
        return new TimestampVar(value);
    }

    public static TimestampVar nullValue() {
        return new TimestampVar(null);
    }
}
