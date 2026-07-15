package oracle.packages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты пакета {@link UtlEncode}.
 */
class UtlEncodeTest {

    @Nested
    @DisplayName("Base64")
    class Base64Tests {

        @Test
        void base64Encode_and_decode_roundTrip() {
            RawVar original = RawVar.of("Hello".getBytes(StandardCharsets.UTF_8));
            RawVar encoded = UtlEncode.base64Encode(original);
            assertThat(new String(encoded.getInternal(), StandardCharsets.US_ASCII))
                    .isEqualTo("SGVsbG8=");
            RawVar decoded = UtlEncode.base64Decode(encoded);
            assertThat(decoded.get()).isEqualTo(original.get());
        }

        @Test
        void base64Encode_returnsNullForNullInput() {
            assertThat(UtlEncode.base64Encode(RawVar.nullValue()).isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Quoted-Printable")
    class QuotedPrintable {

        @Test
        void quotedPrintable_roundTrip() {
            RawVar original = RawVar.of(new byte[]{0x00, 0x41, (byte) 0xFF});
            RawVar encoded = UtlEncode.quotedPrintableEncode(original);
            String text = new String(encoded.getInternal(), StandardCharsets.US_ASCII);
            assertThat(text).contains("=00").contains("A").contains("=FF");
            assertThat(UtlEncode.quotedPrintableDecode(encoded).get()).isEqualTo(original.get());
        }
    }

    @Nested
    @DisplayName("UUEncode")
    class UuEncode {

        @Test
        void uuencode_and_uudecode_roundTrip() {
            RawVar original = RawVar.of("Cat".getBytes(StandardCharsets.US_ASCII));
            RawVar encoded = UtlEncode.uuencode(
                    original,
                    Optional.empty(),
                    Optional.of(Varchar2Var.of("cat.txt")),
                    Optional.of(Varchar2Var.of("644")));
            String text = new String(encoded.getInternal(), StandardCharsets.US_ASCII);
            assertThat(text).contains("begin 644 cat.txt").contains("end");
            RawVar decoded = UtlEncode.uudecode(encoded);
            assertThat(decoded.get()).isEqualTo(original.get());
        }

        @Test
        void uuencode_withCompleteType() {
            RawVar encoded = UtlEncode.uuencode(
                    RawVar.of("X".getBytes(StandardCharsets.US_ASCII)),
                    Optional.of(PlsIntegerVar.of(UtlEncode.COMPLETE)),
                    Optional.empty(),
                    Optional.empty());
            assertThat(encoded.isNull()).isFalse();
        }
    }

    @Nested
    @DisplayName("Текст и MIME")
    class TextAndMime {

        @Test
        void textEncode_decode_base64() {
            Varchar2Var original = Varchar2Var.of("Привет");
            Varchar2Var encoded = UtlEncode.textEncode(
                    original, Optional.of(Varchar2Var.of("UTF-8")), Optional.empty());
            Varchar2Var decoded = UtlEncode.textDecode(
                    encoded, Optional.of(Varchar2Var.of("UTF-8")), Optional.empty());
            assertThat(decoded.get()).isEqualTo("Привет");
        }

        @Test
        void textEncode_quotedPrintable() {
            Varchar2Var encoded = UtlEncode.textEncode(
                    Varchar2Var.of("A"),
                    Optional.empty(),
                    Optional.of(PlsIntegerVar.of(UtlEncode.QUOTED_PRINTABLE)));
            assertThat(encoded.get()).contains("A");
        }

        @Test
        void mimeheader_encode_decode() {
            Varchar2Var original = Varchar2Var.of("Subject");
            Varchar2Var encoded = UtlEncode.mimeheaderEncode(
                    original, Optional.of(Varchar2Var.of("UTF-8")), Optional.empty());
            assertThat(encoded.get()).startsWith("=?").endsWith("?=");
            assertThat(UtlEncode.mimeheaderDecode(encoded).get()).isEqualTo("Subject");
        }

        @Test
        void mimeheaderDecode_plainText() {
            assertThat(UtlEncode.mimeheaderDecode(Varchar2Var.of("plain")).get())
                    .isEqualTo("plain");
        }

        @Test
        void textEncode_returnsNullForNullInput() {
            assertThat(UtlEncode.textEncode(Varchar2Var.nullValue(), Optional.empty(), Optional.empty()).isNull())
                    .isTrue();
        }
    }
}
