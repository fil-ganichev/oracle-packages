package oracle.packages;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты иерархии обёрток {@link Var}.
 */
class VarTest {

    @Test
    void varchar2_nullAndValue() {
        Varchar2Var v = Varchar2Var.of("test");
        assertThat(v.isNull()).isFalse();
        assertThat(v.get()).isEqualTo("test");
        v.set(null);
        assertThat(v.isNull()).isTrue();
    }

    @Test
    void numberVar_fromNumber() {
        NumberVar n = NumberVar.of(42);
        assertThat(n.get()).isEqualByComparingTo("42");
        n.set(null);
        assertThat(n.isNull()).isTrue();
    }

    @Test
    void rawVar_ofHex_and_defensiveCopy() {
        RawVar r = RawVar.ofHex("0A0B");
        assertThat(r.toHex()).isEqualTo("0A0B");
        byte[] copy = r.get();
        copy[0] = 0;
        assertThat(r.get()[0]).isEqualTo((byte) 0x0A);
    }

    @Test
    void blob_and_clob_empty() {
        assertThat(BlobVar.empty().get()).isEmpty();
        assertThat(ClobVar.empty().get()).isEmpty();
        assertThat(BlobVar.nullValue().isNull()).isTrue();
    }

    @Test
    void boolean_and_plsInteger() {
        assertThat(BooleanVar.of(true).get()).isTrue();
        assertThat(PlsIntegerVar.of(7).get()).isEqualTo(7);
        assertThat(BooleanVar.nullValue().isNull()).isTrue();
    }
}
