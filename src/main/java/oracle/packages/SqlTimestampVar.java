package oracle.packages;

import java.sql.Timestamp;

/**
 * Обёртка над {@link java.sql.Timestamp} (JDBC), в отличие от {@link TimestampVar} с {@code OffsetDateTime}.
 * Хранит instant (epoch millis); таймзона в самом значении не кодируется.
 */
public class SqlTimestampVar extends AbstractVar<Timestamp> {

    public SqlTimestampVar() {
        super();
    }

    public SqlTimestampVar(Timestamp value) {
        super(value == null ? null : new Timestamp(value.getTime()));
    }

    public static SqlTimestampVar of(Timestamp value) {
        return new SqlTimestampVar(value);
    }

    public static SqlTimestampVar nullValue() {
        return new SqlTimestampVar(null);
    }
}
