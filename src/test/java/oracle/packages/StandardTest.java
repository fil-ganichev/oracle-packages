package oracle.packages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты пакета {@link Standard}.
 */
@ExtendWith(MockitoExtension.class)
class StandardTest {

    @Nested
    @DisplayName("Математические функции")
    class MathFunctions {

        @Test
        void abs_returnsAbsoluteValue() {
            assertThat(Standard.abs(NumberVar.of(-5)).get()).isEqualByComparingTo("5");
            assertThat(Standard.abs(NumberVar.of(3.5)).get()).isEqualByComparingTo("3.5");
        }

        @Test
        void abs_returnsNullForNullInput() {
            assertThat(Standard.abs(NumberVar.nullValue()).isNull()).isTrue();
        }

        @Test
        void bitand_performsBitwiseAnd() {
            assertThat(Standard.bitand(NumberVar.of(12), NumberVar.of(10)).get())
                    .isEqualByComparingTo("8");
        }

        @Test
        void ceil_floor_round_trunc() {
            assertThat(Standard.ceil$2(NumberVar.of(1.2)).get()).isEqualByComparingTo("2");
            assertThat(Standard.floor$2(NumberVar.of(1.8)).get()).isEqualByComparingTo("1");
            assertThat(Standard.round(NumberVar.of(1.5)).get()).isEqualByComparingTo("2");
            assertThat(Standard.trunc$2(NumberVar.of(1.9)).get()).isEqualByComparingTo("1");
        }

        @Test
        void mod_and_power() {
            assertThat(Standard.mod(NumberVar.of(10), NumberVar.of(3)).get())
                    .isEqualByComparingTo("1");
            assertThat(Standard.power$1(NumberVar.of(2), NumberVar.of(3)).get())
                    .isEqualByComparingTo("8");
        }

        @Test
        void sign_returnsSignValue() {
            assertThat(Standard.sign$2(NumberVar.of(-10)).get()).isEqualTo(-1);
            assertThat(Standard.sign$2(NumberVar.of(0)).get()).isEqualTo(0);
            assertThat(Standard.sign$2(NumberVar.of(7)).get()).isEqualTo(1);
        }

        @Test
        void sqrt_and_trig() {
            assertThat(Standard.sqrt$2(NumberVar.of(9)).get()).isEqualByComparingTo("3");
            assertThat(Standard.cos$1(NumberVar.of(0)).get().doubleValue())
                    .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-10));
        }
    }

    @Nested
    @DisplayName("Строковые функции")
    class StringFunctions {

        @Test
        void concat_joinsStrings() {
            assertThat(Standard.concat(Varchar2Var.of("Hello"), Varchar2Var.of(" World")).get())
                    .isEqualTo("Hello World");
        }

        @Test
        void upper_lower_initcap() {
            assertThat(Standard.upper(Varchar2Var.of("abc")).get()).isEqualTo("ABC");
            assertThat(Standard.lower(Varchar2Var.of("ABC")).get()).isEqualTo("abc");
            assertThat(Standard.initcap(Varchar2Var.of("hello world")).get()).isEqualTo("Hello World");
        }

        @Test
        void substr_extractsSubstring() {
            assertThat(Standard.substr(Varchar2Var.of("ABCDEF"), PlsIntegerVar.of(2)).get())
                    .isEqualTo("BCDEF");
            assertThat(Standard.substr$1(Varchar2Var.of("ABCDEF"), PlsIntegerVar.of(2), PlsIntegerVar.of(3)).get())
                    .isEqualTo("BCD");
        }

        @Test
        void instr_findsOccurrence() {
            assertThat(Standard.instr(Varchar2Var.of("CORPORATE FLOOR"), Varchar2Var.of("OR")).get())
                    .isEqualTo(2);
        }

        @Test
        void length_lpad_rpad_trim() {
            assertThat(Standard.length$1(Varchar2Var.of("abc")).get()).isEqualTo(3);
            assertThat(Standard.lpad(Varchar2Var.of("7"), PlsIntegerVar.of(3)).get()).isEqualTo("  7");
            assertThat(Standard.rpad$2(Varchar2Var.of("7"), PlsIntegerVar.of(3)).get()).isEqualTo("7  ");
            assertThat(Standard.trim(Varchar2Var.of("  x  ")).get()).isEqualTo("x");
        }

        @Test
        void replace_replacesSubstring() {
            assertThat(Standard.replace$1(Varchar2Var.of("JACK and JUE"), Varchar2Var.of("J"), Varchar2Var.of("BL")).get())
                    .isEqualTo("BLACK and BLUE");
        }

        @Test
        void hextoraw_and_rawtohex() {
            RawVar raw = Standard.hextoraw(Varchar2Var.of("4869"));
            assertThat(raw.get()).containsExactly((byte) 0x48, (byte) 0x69);
            assertThat(Standard.rawtohex(raw).get()).isEqualToIgnoringCase("4869");
        }

        @Test
        void chr_and_ascii() {
            assertThat(Standard.chr(PlsIntegerVar.of(65)).get()).isEqualTo("A");
            assertThat(Standard.ascii(Varchar2Var.of("A")).get()).isEqualTo(65);
        }

        @Test
        void translate_mapsAndRemovesChars() {
            assertThat(Standard.translate(
                    Varchar2Var.of("ABCDEF"),
                    Varchar2Var.of("ACE"),
                    Varchar2Var.of("XY")).get()).isEqualTo("XBYDF");
        }
    }

    @Nested
    @DisplayName("NULL-обработка")
    class NullHandling {

        @Test
        void nvl_returnsFirstNonNull() {
            assertThat(Standard.nvl$10(Varchar2Var.nullValue(), Varchar2Var.of("def")).get())
                    .isEqualTo("def");
            assertThat(Standard.nvl$10(Varchar2Var.of("abc"), Varchar2Var.of("def")).get())
                    .isEqualTo("abc");
        }

        @Test
        void nvl2_selectsBranch() {
            assertThat(Standard.nvl2(Varchar2Var.of("x"), Varchar2Var.of("yes"), Varchar2Var.of("no")).get())
                    .isEqualTo("yes");
            assertThat(Standard.nvl2(Varchar2Var.nullValue(), Varchar2Var.of("yes"), Varchar2Var.of("no")).get())
                    .isEqualTo("no");
        }

        @Test
        void nullif_comparesValues() {
            assertThat(Standard.nullif$1(Varchar2Var.of("a"), Varchar2Var.of("a")).isNull()).isTrue();
            assertThat(Standard.nullif$1(Varchar2Var.of("a"), Varchar2Var.of("b")).get()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("Даты и преобразования")
    class DatesAndConversions {

        @Test
        void addMonths_addsMonths() {
            DateVar d = DateVar.of(LocalDateTime.of(2024, 1, 31, 0, 0));
            DateVar result = Standard.addMonths(d, NumberVar.of(1));
            assertThat(result.get().getMonthValue()).isEqualTo(2);
        }

        @Test
        void sysdate_isNotNull() {
            assertThat(Standard.sysdate().isNull()).isFalse();
        }

        @Test
        void toNumber_parsesNumber() {
            assertThat(Standard.toNumber$1(Varchar2Var.of("123.45")).get())
                    .isEqualByComparingTo("123.45");
        }

        @Test
        void toChar_forNumberAndDate() {
            assertThat(Standard.toChar$5(NumberVar.of(42)).get()).isEqualTo("42");
            assertThat(Standard.toChar$3(DateVar.of(LocalDateTime.of(2024, 1, 15, 0, 0))).get())
                    .isNotBlank();
        }

        @Test
        void emptyBlob_and_emptyClob() {
            assertThat(Standard.emptyBlob().get()).isEmpty();
            assertThat(Standard.emptyClob().get()).isEmpty();
        }

        @Test
        void sysGuid_returns16Bytes() {
            assertThat(Standard.sysGuid().get()).hasSize(16);
        }
    }

    @Nested
    @DisplayName("Регулярные выражения")
    class Regexp {

        @Test
        void regexpLike_matchesPattern() {
            assertThat(Standard.regexpLike(Varchar2Var.of("abc123"), Varchar2Var.of("^[a-z]+\\d+$")).get())
                    .isTrue();
        }

        @Test
        void regexpReplace_replacesMatches() {
            assertThat(Standard.regexpReplace$1(
                    Varchar2Var.of("a1b2"), Varchar2Var.of("\\d"), Varchar2Var.of("X")).get())
                    .isEqualTo("aXbX");
        }

        @Test
        void regexpSubstr_extractsMatch() {
            assertThat(Standard.regexpSubstr(Varchar2Var.of("abc123def"), Varchar2Var.of("\\d+")).get())
                    .isEqualTo("123");
        }

        @Test
        void regexpCount_countsMatches() {
            assertThat(Standard.regexpCount(Varchar2Var.of("a1b2c3"), Varchar2Var.of("\\d")).get())
                    .isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Исключения и граничные случаи")
    class EdgeCases {

        @Test
        void raiseApplicationError_throwsException() {
            assertThatThrownBy(() -> Standard.raiseApplicationError(
                    NumberVar.of(-20001), Varchar2Var.of("ошибка")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ошибка");
        }

        @Test
        void optionalDefault_acceptsEmpty() {
            Varchar2Var result = Standard.decompose(Varchar2Var.of("é"), Optional.empty());
            assertThat(result.isNull()).isFalse();
        }
    }
}
