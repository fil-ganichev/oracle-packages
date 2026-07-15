package oracle.packages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Java-эмуляция пакета Oracle UTL_RAW.
 * Операции над бинарными данными RAW: конкатенация, побитовые операции, преобразования.
 */
public final class UtlRaw {

    private static final Logger log = LoggerFactory.getLogger(UtlRaw.class);

    /** Порядок байтов: big-endian. */
    public static final int BIG_ENDIAN = 1;
    /** Порядок байтов: little-endian. */
    public static final int LITTLE_ENDIAN = 2;
    /** Порядок байтов машины (JVM — big-endian для ByteBuffer по умолчанию, используем native). */
    public static final int MACHINE_ENDIAN = 3;

    private UtlRaw() {
    }

    // --- CONCAT (до 12 аргументов) ---

    /** Конкатенация одного RAW. */
    public static RawVar concat(RawVar r1) {
        return concatInternal(r1);
    }

    public static RawVar concat$1(RawVar r1, RawVar r2) {
        return concatInternal(r1, r2);
    }

    public static RawVar concat$2(RawVar r1, RawVar r2, RawVar r3) {
        return concatInternal(r1, r2, r3);
    }

    public static RawVar concat$3(RawVar r1, RawVar r2, RawVar r3, RawVar r4) {
        return concatInternal(r1, r2, r3, r4);
    }

    public static RawVar concat$4(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5) {
        return concatInternal(r1, r2, r3, r4, r5);
    }

    public static RawVar concat$5(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6) {
        return concatInternal(r1, r2, r3, r4, r5, r6);
    }

    public static RawVar concat$6(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7);
    }

    public static RawVar concat$7(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7, RawVar r8) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7, r8);
    }

    public static RawVar concat$8(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7, RawVar r8, RawVar r9) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7, r8, r9);
    }

    public static RawVar concat$9(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7, RawVar r8, RawVar r9, RawVar r10) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7, r8, r9, r10);
    }

    public static RawVar concat$10(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7, RawVar r8, RawVar r9, RawVar r10, RawVar r11) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);
    }

    public static RawVar concat$11(RawVar r1, RawVar r2, RawVar r3, RawVar r4, RawVar r5, RawVar r6, RawVar r7, RawVar r8, RawVar r9, RawVar r10, RawVar r11, RawVar r12) {
        return concatInternal(r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12);
    }

    private static RawVar concatInternal(RawVar... parts) {
        int total = 0;
        for (RawVar part : parts) {
            if (part != null && !part.isNull()) {
                total += part.getInternal().length;
            }
        }
        if (total > 32767) {
            throw new IllegalArgumentException("Результат CONCAT превышает 32K байт");
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (RawVar part : parts) {
            if (part != null && !part.isNull()) {
                byte[] bytes = part.getInternal();
                System.arraycopy(bytes, 0, result, offset, bytes.length);
                offset += bytes.length;
            }
        }
        return RawVar.of(result);
    }

    /** Преобразует VARCHAR2 в RAW (байты строки в UTF-8). */
    public static RawVar castToRaw(Varchar2Var c) {
        if (c == null || c.isNull()) {
            return RawVar.nullValue();
        }
        return RawVar.of(c.get().getBytes(StandardCharsets.UTF_8));
    }

    /** Преобразует RAW в VARCHAR2. */
    public static Varchar2Var castToVarchar2(RawVar r) {
        if (r == null || r.isNull()) {
            return Varchar2Var.nullValue();
        }
        return Varchar2Var.of(new String(r.getInternal(), StandardCharsets.UTF_8));
    }

    /** Преобразует RAW в NVARCHAR2. */
    public static Nvarchar2Var castToNvarchar2(RawVar r) {
        if (r == null || r.isNull()) {
            return Nvarchar2Var.nullValue();
        }
        return Nvarchar2Var.of(new String(r.getInternal(), StandardCharsets.UTF_16));
    }

    /** Длина RAW в байтах. */
    public static NumberVar length(RawVar r) {
        if (r == null || r.isNull()) {
            return NumberVar.nullValue();
        }
        return NumberVar.of(r.getInternal().length);
    }

    /** Подстрока RAW начиная с позиции pos (1-based; отрицательная — с конца). */
    public static RawVar substr(RawVar r, PlsIntegerVar pos) {
        return substr$1(r, pos, PlsIntegerVar.nullValue());
    }

    /** Подстрока RAW длиной len начиная с pos. */
    public static RawVar substr$1(RawVar r, PlsIntegerVar pos, PlsIntegerVar len) {
        if (r == null || r.isNull() || pos == null || pos.isNull()) {
            return RawVar.nullValue();
        }
        byte[] data = r.getInternal();
        int start = resolveStartIndex(data.length, pos.get());
        if (start < 0 || start >= data.length) {
            return RawVar.of(new byte[0]);
        }
        int length = (len == null || len.isNull()) ? data.length - start : Math.max(0, len.get());
        int end = Math.min(data.length, start + length);
        return RawVar.of(Arrays.copyOfRange(data, start, end));
    }

    private static int resolveStartIndex(int length, int pos) {
        if (pos == 0) {
            throw new IllegalArgumentException("pos не может быть 0");
        }
        if (pos > 0) {
            return pos - 1;
        }
        return length + pos;
    }

    /**
     * Заменяет байты из from_set на соответствующие из to_set.
     * Байты без пары в to_set удаляются (как Oracle TRANSLATE).
     */
    public static RawVar translate(RawVar r, RawVar fromSet, RawVar toSet) {
        if (r == null || r.isNull() || fromSet == null || fromSet.isNull()
                || toSet == null || toSet.isNull()) {
            return RawVar.nullValue();
        }
        byte[] src = r.getInternal();
        byte[] from = fromSet.getInternal();
        byte[] to = toSet.getInternal();
        byte[] map = new byte[256];
        boolean[] mapped = new boolean[256];
        boolean[] delete = new boolean[256];
        Arrays.fill(map, (byte) 0);
        for (int i = 0; i < from.length; i++) {
            int key = from[i] & 0xFF;
            if (i < to.length) {
                map[key] = to[i];
                mapped[key] = true;
            } else {
                delete[key] = true;
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length);
        for (byte b : src) {
            int key = b & 0xFF;
            if (delete[key]) {
                continue;
            }
            out.write(mapped[key] ? map[key] : b);
        }
        return RawVar.of(out.toByteArray());
    }

    /**
     * Транслитерация: каждый байт из from_set заменяется соответствующим из to_set;
     * лишние байты from_set заменяются pad.
     */
    public static RawVar transliterate(RawVar r,
                                       Optional<RawVar> toSet,
                                       Optional<RawVar> fromSet,
                                       Optional<RawVar> pad) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        byte[] src = r.getInternal();
        byte[] from = unwrapOptionalRaw(fromSet).orElse(new byte[]{0x00});
        byte[] to = unwrapOptionalRaw(toSet).orElse(new byte[0]);
        byte padByte = unwrapOptionalRaw(pad).map(b -> b.length > 0 ? b[0] : (byte) 0).orElse((byte) 0);

        byte[] map = new byte[256];
        boolean[] mapped = new boolean[256];
        for (int i = 0; i < 256; i++) {
            map[i] = (byte) i;
        }
        for (int i = 0; i < from.length; i++) {
            int key = from[i] & 0xFF;
            map[key] = i < to.length ? to[i] : padByte;
            mapped[key] = true;
        }
        byte[] result = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            result[i] = map[src[i] & 0xFF];
        }
        return RawVar.of(result);
    }

    /** Накладывает overlay_str поверх target начиная с pos. */
    public static RawVar overlay(RawVar overlayStr,
                                 RawVar target,
                                 Optional<PlsIntegerVar> pos,
                                 Optional<PlsIntegerVar> len,
                                 Optional<RawVar> pad) {
        if (overlayStr == null || overlayStr.isNull() || target == null || target.isNull()) {
            return RawVar.nullValue();
        }
        int position = pos.filter(p -> !p.isNull()).map(PlsIntegerVar::get).orElse(1);
        if (position < 1) {
            throw new IllegalArgumentException("pos должен быть >= 1");
        }
        byte[] overlay = overlayStr.getInternal();
        byte[] tgt = target.getInternal();
        int overlayLen = len.filter(l -> !l.isNull()).map(PlsIntegerVar::get).orElse(overlay.length);
        byte padByte = unwrapOptionalRaw(pad).map(b -> b.length > 0 ? b[0] : (byte) 0).orElse((byte) 0);

        int start = position - 1;
        int newLen = Math.max(tgt.length, start + overlayLen);
        byte[] result = new byte[newLen];
        Arrays.fill(result, padByte);
        System.arraycopy(tgt, 0, result, 0, tgt.length);
        for (int i = 0; i < overlayLen; i++) {
            result[start + i] = i < overlay.length ? overlay[i] : padByte;
        }
        return RawVar.of(result);
    }

    /** Возвращает n копий RAW, склеенных вместе. */
    public static RawVar copies(RawVar r, NumberVar n) {
        if (r == null || r.isNull() || n == null || n.isNull()) {
            return RawVar.nullValue();
        }
        int count = n.get().intValue();
        if (count < 0) {
            throw new IllegalArgumentException("n должно быть >= 0");
        }
        if (count == 0) {
            return RawVar.of(new byte[0]);
        }
        byte[] src = r.getInternal();
        if ((long) src.length * count > 32767) {
            throw new IllegalArgumentException("Результат COPIES превышает 32K");
        }
        byte[] result = new byte[src.length * count];
        for (int i = 0; i < count; i++) {
            System.arraycopy(src, 0, result, i * src.length, src.length);
        }
        return RawVar.of(result);
    }

    /** Диапазон байтов от start_byte до end_byte включительно. */
    public static RawVar xrange(Optional<RawVar> startByte, Optional<RawVar> endByte) {
        int start = unwrapOptionalRaw(startByte).map(b -> b.length > 0 ? (b[0] & 0xFF) : 0).orElse(0);
        int end = unwrapOptionalRaw(endByte).map(b -> b.length > 0 ? (b[0] & 0xFF) : 255).orElse(255);
        if (start <= end) {
            byte[] result = new byte[end - start + 1];
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) (start + i);
            }
            return RawVar.of(result);
        }
        // Обёртка через 255: start..255, 0..end
        byte[] result = new byte[(256 - start) + end + 1];
        int idx = 0;
        for (int i = start; i < 256; i++) {
            result[idx++] = (byte) i;
        }
        for (int i = 0; i <= end; i++) {
            result[idx++] = (byte) i;
        }
        return RawVar.of(result);
    }

    /** Переворачивает порядок байтов. */
    public static RawVar reverse(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        byte[] src = r.getInternal();
        byte[] result = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            result[i] = src[src.length - 1 - i];
        }
        return RawVar.of(result);
    }

    /**
     * Сравнивает два RAW. Возвращает 0 при равенстве, иначе позицию первого различия (1-based).
     */
    public static NumberVar compare(RawVar r1, RawVar r2, Optional<RawVar> pad) {
        if (r1 == null || r1.isNull() || r2 == null || r2.isNull()) {
            return NumberVar.nullValue();
        }
        byte padByte = unwrapOptionalRaw(pad).map(b -> b.length > 0 ? b[0] : (byte) 0).orElse((byte) 0);
        byte[] a = r1.getInternal();
        byte[] b = r2.getInternal();
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            byte ba = i < a.length ? a[i] : padByte;
            byte bb = i < b.length ? b[i] : padByte;
            if (ba != bb) {
                return NumberVar.of(i + 1);
            }
        }
        return NumberVar.of(0);
    }

    /** Конвертирует RAW из одной кодировки в другую. */
    public static RawVar convert(RawVar r, Varchar2Var toCharset, Varchar2Var fromCharset) {
        if (r == null || r.isNull() || toCharset == null || toCharset.isNull()
                || fromCharset == null || fromCharset.isNull()) {
            return RawVar.nullValue();
        }
        Charset from = Charset.forName(normalizeCharset(fromCharset.get()));
        Charset to = Charset.forName(normalizeCharset(toCharset.get()));
        String text = new String(r.getInternal(), from);
        return RawVar.of(text.getBytes(to));
    }

    private static String normalizeCharset(String name) {
        return switch (name.toUpperCase()) {
            case "AL32UTF8", "UTF8" -> "UTF-8";
            case "WE8ISO8859P1", "ISO8859P1" -> "ISO-8859-1";
            case "AL16UTF16", "UTF16" -> "UTF-16";
            default -> name;
        };
    }

    /** Побитовое AND. Результат длины max(|r1|,|r2|), короткий дополняется нулями справа. */
    public static RawVar bitAnd(RawVar r1, RawVar r2) {
        return bitwise(r1, r2, (a, b) -> (byte) (a & b));
    }

    /** Побитовое OR. */
    public static RawVar bitOr(RawVar r1, RawVar r2) {
        return bitwise(r1, r2, (a, b) -> (byte) (a | b));
    }

    /** Побитовое XOR. */
    public static RawVar bitXor(RawVar r1, RawVar r2) {
        return bitwise(r1, r2, (a, b) -> (byte) (a ^ b));
    }

    /** Побитовое дополнение (NOT). */
    public static RawVar bitComplement(RawVar r) {
        if (r == null || r.isNull()) {
            return RawVar.nullValue();
        }
        byte[] src = r.getInternal();
        byte[] result = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            result[i] = (byte) ~src[i];
        }
        return RawVar.of(result);
    }

    @FunctionalInterface
    private interface ByteOp {
        byte apply(byte a, byte b);
    }

    private static RawVar bitwise(RawVar r1, RawVar r2, ByteOp op) {
        if (r1 == null || r1.isNull() || r2 == null || r2.isNull()) {
            return RawVar.nullValue();
        }
        byte[] a = r1.getInternal();
        byte[] b = r2.getInternal();
        int len = Math.max(a.length, b.length);
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            byte ba = i < a.length ? a[i] : 0;
            byte bb = i < b.length ? b[i] : 0;
            result[i] = op.apply(ba, bb);
        }
        return RawVar.of(result);
    }

    /** Преобразует Oracle NUMBER (во внутреннем представлении) из RAW — упрощённая эмуляция. */
    public static NumberVar castToNumber(RawVar r) {
        if (r == null || r.isNull()) {
            return NumberVar.nullValue();
        }
        // Упрощённая интерпретация: байты как строка ASCII или как big-endian integer
        try {
            String asText = new String(r.getInternal(), StandardCharsets.US_ASCII).trim();
            if (asText.matches("-?\\d+(\\.\\d+)?")) {
                return NumberVar.of(new BigDecimal(asText));
            }
        } catch (Exception ignored) {
            log.debug("Не удалось разобрать RAW как ASCII-число");
        }
        ByteBuffer buf = ByteBuffer.wrap(padTo(r.getInternal(), 8)).order(ByteOrder.BIG_ENDIAN);
        return NumberVar.of(buf.getLong());
    }

    /** Упаковывает NUMBER в RAW (ASCII-представление). */
    public static RawVar castFromNumber(NumberVar n) {
        if (n == null || n.isNull()) {
            return RawVar.nullValue();
        }
        return RawVar.of(n.get().toPlainString().getBytes(StandardCharsets.US_ASCII));
    }

    public static PlsIntegerVar castToBinaryInteger(RawVar r) {
        return castToBinaryInteger$1(r, PlsIntegerVar.of(BIG_ENDIAN));
    }

    public static PlsIntegerVar castToBinaryInteger$1(RawVar r, PlsIntegerVar endianess) {
        if (r == null || r.isNull()) {
            return PlsIntegerVar.nullValue();
        }
        ByteOrder order = resolveEndian(endianess);
        byte[] data = padTo(r.getInternal(), 4);
        return PlsIntegerVar.of(ByteBuffer.wrap(data).order(order).getInt());
    }

    public static RawVar castFromBinaryInteger(PlsIntegerVar n) {
        return castFromBinaryInteger$1(n, PlsIntegerVar.of(BIG_ENDIAN));
    }

    public static RawVar castFromBinaryInteger$1(PlsIntegerVar n, PlsIntegerVar endianess) {
        if (n == null || n.isNull()) {
            return RawVar.nullValue();
        }
        ByteBuffer buf = ByteBuffer.allocate(4).order(resolveEndian(endianess));
        buf.putInt(n.get());
        return RawVar.of(buf.array());
    }

    public static RawVar castFromBinaryFloat(BinaryFloatVar n, Optional<PlsIntegerVar> endianess) {
        if (n == null || n.isNull()) {
            return RawVar.nullValue();
        }
        int end = endianess.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BIG_ENDIAN);
        ByteBuffer buf = ByteBuffer.allocate(4).order(resolveEndian(PlsIntegerVar.of(end)));
        buf.putFloat(n.get());
        return RawVar.of(buf.array());
    }

    public static BinaryFloatVar castToBinaryFloat(RawVar r, Optional<PlsIntegerVar> endianess) {
        if (r == null || r.isNull()) {
            return BinaryFloatVar.nullValue();
        }
        int end = endianess.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BIG_ENDIAN);
        byte[] data = padTo(r.getInternal(), 4);
        float value = ByteBuffer.wrap(data).order(resolveEndian(PlsIntegerVar.of(end))).getFloat();
        return BinaryFloatVar.of(value);
    }

    public static RawVar castFromBinaryDouble(BinaryDoubleVar n, Optional<PlsIntegerVar> endianess) {
        if (n == null || n.isNull()) {
            return RawVar.nullValue();
        }
        int end = endianess.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BIG_ENDIAN);
        ByteBuffer buf = ByteBuffer.allocate(8).order(resolveEndian(PlsIntegerVar.of(end)));
        buf.putDouble(n.get());
        return RawVar.of(buf.array());
    }

    public static BinaryDoubleVar castToBinaryDouble(RawVar r, Optional<PlsIntegerVar> endianess) {
        if (r == null || r.isNull()) {
            return BinaryDoubleVar.nullValue();
        }
        int end = endianess.filter(e -> !e.isNull()).map(PlsIntegerVar::get).orElse(BIG_ENDIAN);
        byte[] data = padTo(r.getInternal(), 8);
        double value = ByteBuffer.wrap(data).order(resolveEndian(PlsIntegerVar.of(end))).getDouble();
        return BinaryDoubleVar.of(value);
    }

    private static ByteOrder resolveEndian(PlsIntegerVar endianess) {
        int value = endianess == null || endianess.isNull() ? BIG_ENDIAN : endianess.get();
        return switch (value) {
            case LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN;
            case MACHINE_ENDIAN -> ByteOrder.nativeOrder();
            default -> ByteOrder.BIG_ENDIAN;
        };
    }

    private static byte[] padTo(byte[] src, int size) {
        if (src.length >= size) {
            return Arrays.copyOfRange(src, 0, size);
        }
        byte[] padded = new byte[size];
        System.arraycopy(src, 0, padded, size - src.length, src.length);
        return padded;
    }

    private static Optional<byte[]> unwrapOptionalRaw(Optional<RawVar> opt) {
        if (opt == null || opt.isEmpty()) {
            return Optional.empty();
        }
        RawVar v = opt.get();
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        return Optional.of(v.getInternal());
    }
}
