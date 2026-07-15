package oracle.packages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Java-эмуляция пакета Oracle UTL_ENCODE.
 * Кодирование/декодирование Base64, UUEncode, Quoted-Printable и MIME-заголовков.
 */
public final class UtlEncode {

    private static final Logger log = LoggerFactory.getLogger(UtlEncode.class);

    public static final int COMPLETE = 1;
    public static final int HEADER_PIECE = 2;
    public static final int MIDDLE_PIECE = 3;
    public static final int END_PIECE = 4;

    public static final int BASE64 = 1;
    public static final int QUOTED_PRINTABLE = 2;

    private UtlEncode() {
    }

    /** Кодирует RAW в Base64. */
    public static RawVar base64Encode(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        byte[] encoded = Base64.getEncoder().encode(r.getInternal());
        return RawVar.of(encoded);
    }

    /** Декодирует Base64 RAW. */
    public static RawVar base64Decode(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        byte[] decoded = Base64.getDecoder().decode(r.getInternal());
        return RawVar.of(decoded);
    }

    /**
     * UUEncode содержимого RAW.
     * @param type тип фрагмента (COMPLETE по умолчанию)
     * @param filename имя файла в заголовке
     * @param permission права доступа в заголовке
     */
    public static RawVar uuencode(RawVar r,
                                  Optional<PlsIntegerVar> type,
                                  Optional<Varchar2Var> filename,
                                  Optional<Varchar2Var> permission) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        int piece = type.filter(t -> !t.isNull()).map(PlsIntegerVar::get).orElse(COMPLETE);
        String name = filename.filter(f -> !f.isNull()).map(Varchar2Var::get).orElse("uuencode.txt");
        String perm = permission.filter(p -> !p.isNull()).map(Varchar2Var::get).orElse("0");

        StringBuilder sb = new StringBuilder();
        if (piece == COMPLETE || piece == HEADER_PIECE) {
            sb.append("begin ").append(perm).append(' ').append(name).append('\n');
        }
        if (piece != HEADER_PIECE) {
            sb.append(uuEncodeBody(r.getInternal()));
        }
        if (piece == COMPLETE || piece == END_PIECE) {
            if (piece != HEADER_PIECE && piece != MIDDLE_PIECE) {
                // тело уже добавлено для COMPLETE
            }
            sb.append("`\nend\n");
        }
        return RawVar.of(sb.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static String uuEncodeBody(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        while (offset < data.length) {
            int lineLen = Math.min(45, data.length - offset);
            sb.append((char) (lineLen + 32));
            int i = 0;
            while (i < lineLen) {
                int b0 = data[offset + i++] & 0xFF;
                int b1 = i < lineLen ? data[offset + i++] & 0xFF : 0;
                int b2 = i < lineLen ? data[offset + i++] & 0xFF : 0;
                int combined = (b0 << 16) | (b1 << 8) | b2;
                sb.append((char) (((combined >>> 18) & 0x3F) + 32));
                sb.append((char) (((combined >>> 12) & 0x3F) + 32));
                sb.append((char) (((combined >>> 6) & 0x3F) + 32));
                sb.append((char) ((combined & 0x3F) + 32));
            }
            sb.append('\n');
            offset += lineLen;
        }
        return sb.toString();
    }

    /** Декодирует UUEncoded RAW. */
    public static RawVar uudecode(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        String text = new String(r.getInternal(), StandardCharsets.US_ASCII);
        String[] lines = text.split("\\R");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        boolean inBody = false;
        for (String line : lines) {
            if (line.startsWith("begin ")) {
                inBody = true;
                continue;
            }
            if (line.equals("end") || line.equals("`") || line.isEmpty()) {
                if (line.equals("end")) {
                    break;
                }
                continue;
            }
            if (!inBody && !line.isEmpty() && line.charAt(0) >= 32) {
                inBody = true;
            }
            if (!inBody) {
                continue;
            }
            int len = (line.charAt(0) - 32) & 0x3F;
            if (len == 0) {
                continue;
            }
            int idx = 1;
            int written = 0;
            while (written < len && idx + 3 < line.length()) {
                int c0 = (line.charAt(idx++) - 32) & 0x3F;
                int c1 = (line.charAt(idx++) - 32) & 0x3F;
                int c2 = (line.charAt(idx++) - 32) & 0x3F;
                int c3 = (line.charAt(idx++) - 32) & 0x3F;
                int combined = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
                if (written < len) {
                    out.write((combined >>> 16) & 0xFF);
                    written++;
                }
                if (written < len) {
                    out.write((combined >>> 8) & 0xFF);
                    written++;
                }
                if (written < len) {
                    out.write(combined & 0xFF);
                    written++;
                }
            }
        }
        return RawVar.of(out.toByteArray());
    }

    /** Кодирует RAW в Quoted-Printable. */
    public static RawVar quotedPrintableEncode(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        StringBuilder sb = new StringBuilder();
        int lineLen = 0;
        for (byte b : r.getInternal()) {
            int ub = b & 0xFF;
            String enc;
            if ((ub >= 33 && ub <= 60) || (ub >= 62 && ub <= 126) || ub == 9 || ub == 32) {
                enc = String.valueOf((char) ub);
            } else {
                enc = String.format("=%02X", ub);
            }
            if (lineLen + enc.length() > 75) {
                sb.append("=\r\n");
                lineLen = 0;
            }
            sb.append(enc);
            lineLen += enc.length();
        }
        return RawVar.of(sb.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /** Декодирует Quoted-Printable RAW. */
    public static RawVar quotedPrintableDecode(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        String text = new String(r.getInternal(), StandardCharsets.US_ASCII)
                .replace("=\r\n", "")
                .replace("=\n", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '=' && i + 2 < text.length()) {
                out.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.write(ch);
            }
        }
        return RawVar.of(out.toByteArray());
    }

    /**
     * Кодирует текстовый буфер в указанной кодировке (Base64 или Quoted-Printable).
     */
    public static Varchar2Var textEncode(Varchar2Var buf,
                                         Optional<Varchar2Var> encodeCharset,
                                         Optional<PlsIntegerVar> encoding) {
        if (buf == null || buf.isNull()) {
            return Varchar2Var.nullValue();
        }
        Charset charset = resolveCharset(encodeCharset);
        int enc = encoding.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BASE64);
        byte[] raw = buf.get().getBytes(charset);
        RawVar encoded = enc == QUOTED_PRINTABLE
                ? quotedPrintableEncode(RawVar.of(raw))
                : base64Encode(RawVar.of(raw));
        return Varchar2Var.of(new String(encoded.getInternal(), StandardCharsets.US_ASCII));
    }

    /** Декодирует текст, закодированный text_encode. */
    public static Varchar2Var textDecode(Varchar2Var buf,
                                         Optional<Varchar2Var> encodeCharset,
                                         Optional<PlsIntegerVar> encoding) {
        if (buf == null || buf.isNull()) {
            return Varchar2Var.nullValue();
        }
        Charset charset = resolveCharset(encodeCharset);
        int enc = encoding.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BASE64);
        RawVar input = RawVar.of(buf.get().getBytes(StandardCharsets.US_ASCII));
        RawVar decoded = enc == QUOTED_PRINTABLE
                ? quotedPrintableDecode(input)
                : base64Decode(input);
        return Varchar2Var.of(new String(decoded.getInternal(), charset));
    }

    /**
     * Кодирует строку как MIME-заголовок (RFC 2047).
     */
    public static Varchar2Var mimeheaderEncode(Varchar2Var buf,
                                               Optional<Varchar2Var> encodeCharset,
                                               Optional<PlsIntegerVar> encoding) {
        if (buf == null || buf.isNull()) {
            return Varchar2Var.nullValue();
        }
        String charsetName = encodeCharset.filter(c -> !c.isNull())
                .map(Varchar2Var::get)
                .orElse("UTF-8");
        Charset charset = Charset.forName(normalizeCharset(charsetName));
        int enc = encoding.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BASE64);
        byte[] raw = buf.get().getBytes(charset);
        String encodedBody;
        String encLetter;
        if (enc == QUOTED_PRINTABLE) {
            encodedBody = new String(quotedPrintableEncode(RawVar.of(raw)).getInternal(), StandardCharsets.US_ASCII)
                    .replace(" ", "_");
            encLetter = "Q";
        } else {
            encodedBody = Base64.getEncoder().encodeToString(raw);
            encLetter = "B";
        }
        return Varchar2Var.of("=?" + charsetName + "?" + encLetter + "?" + encodedBody + "?=");
    }

    /** Декодирует MIME-заголовок RFC 2047. */
    public static Varchar2Var mimeheaderDecode(Varchar2Var buf) {
        if (buf == null || buf.isNull()) {
            return Varchar2Var.nullValue();
        }
        String text = buf.get();
        if (!text.startsWith("=?") || !text.endsWith("?=")) {
            return Varchar2Var.of(text);
        }
        String inner = text.substring(2, text.length() - 2);
        String[] parts = inner.split("\\?", 3);
        if (parts.length < 3) {
            return Varchar2Var.of(text);
        }
        Charset charset = Charset.forName(normalizeCharset(parts[0]));
        String enc = parts[1].toUpperCase(Locale.ROOT);
        String body = parts[2];
        byte[] decoded;
        if ("Q".equals(enc)) {
            decoded = quotedPrintableDecode(RawVar.of(body.replace("_", " ").getBytes(StandardCharsets.US_ASCII)))
                    .getInternal();
        } else {
            decoded = Base64.getDecoder().decode(body);
        }
        return Varchar2Var.of(new String(decoded, charset));
    }

    private static Charset resolveCharset(Optional<Varchar2Var> encodeCharset) {
        if (encodeCharset == null || encodeCharset.isEmpty() || encodeCharset.get().isNull()) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(normalizeCharset(encodeCharset.get().get()));
    }

    private static String normalizeCharset(String name) {
        if (name == null) {
            return "UTF-8";
        }
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "AL32UTF8", "UTF8" -> "UTF-8";
            case "WE8ISO8859P1" -> "ISO-8859-1";
            case "AL16UTF16" -> "UTF-16";
            default -> name;
        };
    }
}
