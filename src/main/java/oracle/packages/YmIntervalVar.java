package oracle.packages;

import java.time.Period;

/** Обёртка Oracle INTERVAL YEAR TO MONTH. */
public class YmIntervalVar extends AbstractVar<Period> {

    public YmIntervalVar() {
        super();
    }

    public YmIntervalVar(Period value) {
        super(value);
    }

    public static YmIntervalVar of(Period value) {
        return new YmIntervalVar(value);
    }

    public static YmIntervalVar nullValue() {
        return new YmIntervalVar(null);
    }
}
