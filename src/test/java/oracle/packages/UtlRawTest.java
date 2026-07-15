package oracle.packages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты пакета {@link UtlRaw}.
 */
@ExtendWith(MockitoExtension.class)
class UtlRawTest {

    @Nested
    @DisplayName("Конкатенация и длина")
    class ConcatAndLength {

        @Test
        void concat_joinsSeveralRaws() {
            RawVar a = RawVar.ofHex("01");
            RawVar b = RawVar.ofHex("02");
            RawVar c = RawVar.ofHex("03");
            assertThat(UtlRaw.concat$2(a, b, c).toHex()).isEqualTo("010203");
        }

        @Test
        void concat_singleArgument() {
            RawVar a = RawVar.ofHex("ABCD");
            assertThat(UtlRaw.concat(a).toHex()).isEqualTo("ABCD");
        }

        @Test
        void length_returnsByteCount() {
            assertThat(UtlRaw.length(RawVar.ofHex("AABBCC")).get())
                    .isEqualByComparingTo("3");
        }

        @Test
        void length_returnsNullForNullInput() {
            assertThat(UtlRaw.length(RawVar.nullValue()).isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Преобразования")
    class Casts {

        @Test
        void castToRaw_and_back() {
            RawVar raw = UtlRaw.castToRaw(Varchar2Var.of("Hi"));
            assertThat(raw.get()).isEqualTo("Hi".getBytes(StandardCharsets.UTF_8));
            assertThat(UtlRaw.castToVarchar2(raw).get()).isEqualTo("Hi");
        }

        @Test
        void castFromBinaryInteger_and_back() {
            RawVar raw = UtlRaw.castFromBinaryInteger(PlsIntegerVar.of(0x01020304));
            assertThat(UtlRaw.castToBinaryInteger(raw).get()).isEqualTo(0x01020304);
        }

        @Test
        void castFromBinaryInteger$1_littleEndian() {
            RawVar raw = UtlRaw.castFromBinaryInteger$1(
                    PlsIntegerVar.of(0x01020304), PlsIntegerVar.of(UtlRaw.LITTLE_ENDIAN));
            assertThat(raw.toHex()).isEqualTo("04030201");
        }

        @Test
        void castFromBinaryFloat_withOptional() {
            RawVar raw = UtlRaw.castFromBinaryFloat(BinaryFloatVar.of(1.0f), Optional.empty());
            BinaryFloatVar back = UtlRaw.castToBinaryFloat(raw, Optional.empty());
            assertThat(back.get()).isEqualTo(1.0f);
        }

        @Test
        void castFromBinaryDouble_withOptional() {
            RawVar raw = UtlRaw.castFromBinaryDouble(BinaryDoubleVar.of(2.5), Optional.of(PlsIntegerVar.of(UtlRaw.BIG_ENDIAN)));
            assertThat(UtlRaw.castToBinaryDouble(raw, Optional.empty()).get()).isEqualTo(2.5);
        }
    }

    @Nested
    @DisplayName("Подстроки и манипуляции")
    class SubstrAndManip {

        @Test
        void substr_extractsBytes() {
            RawVar r = RawVar.ofHex("0102030405");
            assertThat(UtlRaw.substr(r, PlsIntegerVar.of(2)).toHex()).isEqualTo("02030405");
            assertThat(UtlRaw.substr$1(r, PlsIntegerVar.of(2), PlsIntegerVar.of(2)).toHex())
                    .isEqualTo("0203");
        }

        @Test
        void substr_negativePosition() {
            RawVar r = RawVar.ofHex("01020304");
            assertThat(UtlRaw.substr(r, PlsIntegerVar.of(-2)).toHex()).isEqualTo("0304");
        }

        @Test
        void reverse_reversesBytes() {
            assertThat(UtlRaw.reverse(RawVar.ofHex("010203")).toHex()).isEqualTo("030201");
        }

        @Test
        void copies_repeatsRaw() {
            assertThat(UtlRaw.copies(RawVar.ofHex("AB"), NumberVar.of(3)).toHex())
                    .isEqualTo("ABABAB");
        }

        @Test
        void xrange_buildsRange() {
            RawVar range = UtlRaw.xrange(
                    Optional.of(RawVar.ofHex("01")),
                    Optional.of(RawVar.ofHex("03")));
            assertThat(range.toHex()).isEqualTo("010203");
        }

        @Test
        void xrange_withEmptyOptionals() {
            RawVar range = UtlRaw.xrange(Optional.empty(), Optional.empty());
            assertThat(range.get()).hasSize(256);
        }

        @Test
        void overlay_overlaysBytes() {
            RawVar result = UtlRaw.overlay(
                    RawVar.ofHex("FF"),
                    RawVar.ofHex("010203"),
                    Optional.of(PlsIntegerVar.of(2)),
                    Optional.empty(),
                    Optional.empty());
            assertThat(result.toHex()).isEqualTo("01FF03");
        }

        @Test
        void translate_and_transliterate() {
            RawVar translated = UtlRaw.translate(
                    RawVar.ofHex("010203"),
                    RawVar.ofHex("02"),
                    RawVar.ofHex("FF"));
            assertThat(translated.toHex()).isEqualTo("01FF03");

            RawVar translit = UtlRaw.transliterate(
                    RawVar.ofHex("0102"),
                    Optional.of(RawVar.ofHex("AA")),
                    Optional.of(RawVar.ofHex("01")),
                    Optional.empty());
            assertThat(translit.toHex()).isEqualTo("AA02");
        }

        @Test
        void compare_comparesRaws() {
            assertThat(UtlRaw.compare(RawVar.ofHex("01"), RawVar.ofHex("01"), Optional.empty()).get())
                    .isEqualByComparingTo("0");
            assertThat(UtlRaw.compare(RawVar.ofHex("01"), RawVar.ofHex("02"), Optional.empty()).get())
                    .isEqualByComparingTo("1");
        }
    }

    @Nested
    @DisplayName("Побитовые операции")
    class Bitwise {

        @Test
        void bitAnd_Or_Xor_Complement() {
            RawVar a = RawVar.ofHex("0F");
            RawVar b = RawVar.ofHex("33");
            assertThat(UtlRaw.bitAnd(a, b).toHex()).isEqualTo("03");
            assertThat(UtlRaw.bitOr(a, b).toHex()).isEqualTo("3F");
            assertThat(UtlRaw.bitXor(a, b).toHex()).isEqualTo("3C");
            assertThat(UtlRaw.bitComplement(a).toHex()).isEqualTo("F0");
        }

        @Test
        void bitAnd_returnsNullForNullInput() {
            assertThat(UtlRaw.bitAnd(RawVar.nullValue(), RawVar.ofHex("01")).isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Mockito изоляция")
    class MockitoIsolation {

        @Mock
        RawVar mockRaw;

        @Test
        void length_usesMock() {
            when(mockRaw.isNull()).thenReturn(false);
            when(mockRaw.getInternal()).thenReturn(new byte[]{1, 2, 3, 4});
            assertThat(UtlRaw.length(mockRaw).get()).isEqualByComparingTo("4");
        }
    }

    @Test
    void convert_changesCharset() {
        RawVar utf8 = RawVar.of("A".getBytes(StandardCharsets.UTF_8));
        RawVar converted = UtlRaw.convert(
                utf8,
                Varchar2Var.of("ISO-8859-1"),
                Varchar2Var.of("UTF-8"));
        assertThat(converted.get()).isEqualTo("A".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void substr_zeroPos_throws() {
        assertThatThrownBy(() -> UtlRaw.substr(RawVar.ofHex("01"), PlsIntegerVar.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
