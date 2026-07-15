package oracle.packages;

import java.math.BigDecimal;

/** Обёртка Oracle NUMBER. */
public class NumberVar extends AbstractVar<BigDecimal> {

    public NumberVar() {
        super();
    }

    public NumberVar(BigDecimal value) {
        super(value);
    }

    public NumberVar(Number value) {
        super(value == null ? null : new BigDecimal(value.toString()));
    }

    public static NumberVar of(Number value) {
        return new NumberVar(value);
    }

    public static NumberVar of(BigDecimal value) {
        return new NumberVar(value);
    }

    public static NumberVar nullValue() {
        return new NumberVar((BigDecimal) null);
    }
}
