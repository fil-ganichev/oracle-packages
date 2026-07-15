package oracle.packages;

import java.time.Duration;

/** Обёртка Oracle INTERVAL DAY TO SECOND. */
public class DsIntervalVar extends AbstractVar<Duration> {

    public DsIntervalVar() {
        super();
    }

    public DsIntervalVar(Duration value) {
        super(value);
    }

    public static DsIntervalVar of(Duration value) {
        return new DsIntervalVar(value);
    }

    public static DsIntervalVar nullValue() {
        return new DsIntervalVar(null);
    }
}
