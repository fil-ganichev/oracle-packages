package oracle.packages;

import java.util.Arrays;

/** Обёртка Oracle RAW (массив байтов). */
public class RawVar extends AbstractVar<byte[]> {

    public RawVar() {
        super();
    }

    public RawVar(byte[] value) {
        super(value == null ? null : Arrays.copyOf(value, value.length));
    }

    public static RawVar of(byte[] value) {
        return new RawVar(value);
    }

    public static RawVar ofHex(String hex) {
        if (hex == null) {
            return nullValue();
        }
        String cleaned = hex.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            return of(new byte[0]);
        }
        if (cleaned.length() % 2 != 0) {
            cleaned = "0" + cleaned;
        }
        byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return of(bytes);
    }

    public static RawVar nullValue() {
        return new RawVar((byte[]) null);
    }

    @Override
    public void set(byte[] value) {
        super.set(value == null ? null : Arrays.copyOf(value, value.length));
    }

    @Override
    public byte[] get() {
        byte[] value = super.get();
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    /** Возвращает внутреннюю ссылку без копирования (для внутренних операций). */
    byte[] getInternal() {
        return super.get();
    }

    public String toHex() {
        byte[] value = super.get();
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length * 2);
        for (byte b : value) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
