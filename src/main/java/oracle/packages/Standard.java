package oracle.packages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java-эмуляция пакета Oracle SYS.STANDARD.
 * Реализует встроенные функции и процедуры PL/SQL согласно {@code sql/standard.sql}.
 */
public final class Standard {

    private static final Logger log = LoggerFactory.getLogger(Standard.class);

    private static final MathContext NUMBER_MATH = MathContext.DECIMAL128;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter BASIC_TO_CHAR_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private Standard() {
    }

    // --- Вспомогательные методы ---

    /** Проверяет NULL-обёртку Oracle. */
    private static boolean isNull(Var<?> v) {
        return v == null || v.isNull();
    }

    /** Возвращает знак SIGNTYPE (-1, 0, 1). */
    private static PlsIntegerVar signOf(BigDecimal n) {
        int cmp = n.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            return PlsIntegerVar.of(1);
        }
        if (cmp < 0) {
            return PlsIntegerVar.of(-1);
        }
        return PlsIntegerVar.of(0);
    }

    /** Oracle MOD: знак результата совпадает со знаком делителя. */
    private static BigDecimal oracleMod(BigDecimal m, BigDecimal n) {
        if (n.compareTo(BigDecimal.ZERO) == 0) {
            return m;
        }
        BigDecimal quotient = m.divide(n, 0, RoundingMode.FLOOR);
        return m.subtract(n.multiply(quotient));
    }

    /** Oracle REM: знак результата совпадает со знаком делимого. */
    private static BigDecimal oracleRem(BigDecimal m, BigDecimal n) {
        if (n.compareTo(BigDecimal.ZERO) == 0) {
            return m;
        }
        BigDecimal quotient = m.divide(n, 0, RoundingMode.DOWN);
        return m.subtract(n.multiply(quotient));
    }

    /** Преобразует модификаторы Oracle REGEXP в флаги {@link Pattern}. */
    private static int oracleRegexpFlags(String modifier) {
        if (modifier == null) {
            return 0;
        }
        int flags = 0;
        String m = modifier.toLowerCase(Locale.ROOT);
        if (m.contains("i")) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (m.contains("n")) {
            flags |= Pattern.DOTALL;
        }
        if (m.contains("m")) {
            flags |= Pattern.MULTILINE;
        }
        if (m.contains("x")) {
            flags |= Pattern.COMMENTS;
        }
        return flags;
    }

    /** Создаёт {@link Pattern} с учётом модификаторов Oracle. */
    private static Pattern compileOraclePattern(String pattern, String modifier) {
        return Pattern.compile(pattern, oracleRegexpFlags(modifier));
    }

    /** Разрешает 1-based позицию Oracle INSTR/SUBSTR (отрицательная — с конца). */
    private static int resolveOracleStart(int length, int pos) {
        if (pos == 0) {
            return -1;
        }
        if (pos > 0) {
            return pos - 1;
        }
        return length + pos;
    }

    /** INSTR: поиск n-го вхождения подстроки. */
    private static int instrNth(String haystack, String needle, int start, int nth) {
        if (needle.isEmpty()) {
            return start + 1;
        }
        int from = Math.max(0, Math.min(start, haystack.length()));
        int found = 0;
        int idx = from;
        while (idx <= haystack.length()) {
            int at = haystack.indexOf(needle, idx);
            if (at < 0) {
                return 0;
            }
            found++;
            if (found == nth) {
                return at + 1;
            }
            idx = at + 1;
        }
        return 0;
    }

    /** SUBSTR Oracle: pos 1-based, len может быть отрицательной. */
    private static String substrInternal(String s, int pos, int len) {
        int start = resolveOracleStart(s.length(), pos);
        if (start < 0 || start >= s.length()) {
            return "";
        }
        if (len < 0) {
            int end = resolveOracleStart(s.length(), len);
            if (end < start) {
                return "";
            }
            return s.substring(start, Math.min(end + 1, s.length()));
        }
        // Защита от переполнения при len == Integer.MAX_VALUE
        long endLong = (long) start + (long) len;
        int end = (int) Math.min(endLong, s.length());
        return s.substring(start, end);
    }

    /** SUBSTRB: работа в байтах UTF-8. */
    private static String substrbInternal(String s, int pos, int len) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int start = resolveOracleStart(bytes.length, pos);
        if (start < 0 || start >= bytes.length) {
            return "";
        }
        int end = Math.min(start + len, bytes.length);
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    /** LPAD/RPAD Oracle. */
    private static String padInternal(String str, int len, String pad, boolean left) {
        if (str.length() >= len) {
            return str.substring(0, len);
        }
        String p = (pad == null || pad.isEmpty()) ? " " : pad;
        StringBuilder sb = new StringBuilder(len);
        int padLen = len - str.length();
        while (sb.length() < padLen) {
            sb.append(p);
        }
        String padding = sb.substring(0, padLen);
        return left ? padding + str : str + padding;
    }

    /** LTRIM/RTRIM Oracle по набору символов. */
    private static String trimSet(String str, String tset, boolean left, boolean right) {
        if (tset == null || tset.isEmpty()) {
            tset = " ";
        }
        int start = 0;
        int end = str.length();
        if (left) {
            while (start < end && tset.indexOf(str.charAt(start)) >= 0) {
                start++;
            }
        }
        if (right) {
            while (end > start && tset.indexOf(str.charAt(end - 1)) >= 0) {
                end--;
            }
        }
        return str.substring(start, end);
    }

    /** Oracle TRANSLATE: посимвольная замена по позициям SRC/DEST. */
    private static String translateInternal(String str, String src, String dest) {
        if (src == null || src.isEmpty()) {
            return str;
        }
        String d = dest == null ? "" : dest;
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int idx = src.indexOf(c);
            if (idx < 0) {
                sb.append(c);
            } else if (idx < d.length()) {
                sb.append(d.charAt(idx));
            }
        }
        return sb.toString();
    }

    /** Oracle TRUNC для NUMBER с указанием десятичных разрядов. */
    private static BigDecimal truncNumber(BigDecimal n, int places) {
        if (places >= 0) {
            return n.setScale(places, RoundingMode.DOWN);
        }
        BigDecimal factor = BigDecimal.TEN.pow(-places);
        return n.divide(factor, 0, RoundingMode.DOWN).multiply(factor);
    }

    /** Oracle ROUND для NUMBER. */
    private static BigDecimal roundNumber(BigDecimal n, int places) {
        return n.setScale(places, RoundingMode.HALF_UP);
    }

    /** ADD_MONTHS Oracle с коррекцией последнего дня месяца. */
    private static LocalDateTime addMonthsInternal(LocalDateTime dt, long months) {
        LocalDate date = dt.toLocalDate();
        int day = date.getDayOfMonth();
        LocalDate shifted = date.plusMonths(months);
        if (day > shifted.lengthOfMonth()) {
            shifted = shifted.withDayOfMonth(shifted.lengthOfMonth());
        }
        return LocalDateTime.of(shifted, dt.toLocalTime());
    }

    /** MONTHS_BETWEEN Oracle (упрощённая формула с базой 31 день). */
    private static BigDecimal monthsBetweenInternal(LocalDateTime left, LocalDateTime right) {
        int months = (left.getYear() - right.getYear()) * 12 + (left.getMonthValue() - right.getMonthValue());
        int dayDiff = left.getDayOfMonth() - right.getDayOfMonth();
        return BigDecimal.valueOf(months).add(BigDecimal.valueOf(dayDiff).divide(BigDecimal.valueOf(31), NUMBER_MATH));
    }

    /** NEXT_DAY: следующий указанный день недели. */
    private static LocalDateTime nextDayInternal(LocalDateTime dt, String dayName) {
        DayOfWeek target = parseDayOfWeek(dayName);
        LocalDate next = dt.toLocalDate().with(TemporalAdjusters.next(target));
        return LocalDateTime.of(next, dt.toLocalTime());
    }

    /** Разбор имени дня недели Oracle. */
    private static DayOfWeek parseDayOfWeek(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        return switch (n.length() >= 3 ? n.substring(0, 3) : n) {
            case "SUN" -> DayOfWeek.SUNDAY;
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.valueOf(n);
        };
    }

    /** TRUNC DATE по форматной маске (базовые единицы). */
    private static LocalDateTime truncDateInternal(LocalDateTime dt, String fmt) {
        if (fmt == null || fmt.isEmpty()) {
            return dt.toLocalDate().atStartOfDay();
        }
        String f = fmt.toUpperCase(Locale.ROOT);
        if (f.contains("YYYY") || f.contains("YEAR")) {
            return LocalDateTime.of(dt.getYear(), 1, 1, 0, 0);
        }
        if (f.contains("MM") || f.contains("MON")) {
            return LocalDateTime.of(dt.getYear(), dt.getMonthValue(), 1, 0, 0);
        }
        if (f.contains("DD")) {
            return dt.toLocalDate().atStartOfDay();
        }
        if (f.contains("HH")) {
            return dt.withMinute(0).withSecond(0).withNano(0);
        }
        if (f.contains("MI")) {
            return dt.withSecond(0).withNano(0);
        }
        return dt.toLocalDate().atStartOfDay();
    }

    /** Сравнение дат для DECODE. */
    private static boolean datesEqual(LocalDateTime a, LocalDateTime b) {
        return a.truncatedTo(ChronoUnit.MICROS).equals(b.truncatedTo(ChronoUnit.MICROS));
    }

    /** Сравнение чисел для DECODE. */
    private static boolean numbersEqual(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }

    /** Базовое TO_CHAR для DATE без сложных NLS-масок. */
    private static String toCharDateBasic(LocalDateTime dt, String format) {
        if (format == null || format.isEmpty()) {
            return BASIC_TO_CHAR_DATE.format(dt);
        }
        try {
            return DateTimeFormatter.ofPattern(format, Locale.ENGLISH).format(dt);
        } catch (IllegalArgumentException e) {
            log.warn("Неподдерживаемая маска TO_CHAR для DATE: {}", format);
            return dt.toString();
        }
    }

    /** Базовое TO_CHAR для NUMBER. */
    private static String toCharNumberBasic(BigDecimal n, String format) {
        if (format == null || format.isEmpty()) {
            return n.stripTrailingZeros().toPlainString();
        }
        if ("FM9999999990.999999999".equals(format) || "9999999990.999999999".equals(format)) {
            return n.toPlainString();
        }
        log.warn("Упрощённое TO_CHAR для NUMBER с маской: {}", format);
        return n.toPlainString();
    }

    /** Базовый разбор TO_DATE ISO yyyy-MM-dd. */
    private static LocalDateTime parseDateBasic(String s, String format) {
        if (format == null || format.isEmpty() || "YYYY-MM-DD".equalsIgnoreCase(format)) {
            if (s.length() <= 10) {
                return LocalDate.parse(s.substring(0, Math.min(10, s.length())), ISO_DATE).atStartOfDay();
            }
            return LocalDateTime.parse(s, ISO_DATE_TIME);
        }
        return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
    }

    /** REGEXP_COUNT внутренняя реализация. */
    private static PlsIntegerVar regexpCountInternal(Varchar2Var src, Varchar2Var pattern, PlsIntegerVar position,
                                                     Optional<Varchar2Var> modifier) {
        if (isNull(src) || isNull(pattern)) {
            return PlsIntegerVar.nullValue();
        }
        String mod = modifier.filter(v -> !isNull(v)).map(Var::get).orElse(null);
        int pos = isNull(position) ? 1 : position.get();
        String text = src.get();
        int start = Math.max(0, pos - 1);
        if (start > text.length()) {
            return PlsIntegerVar.of(0);
        }
        Matcher m = compileOraclePattern(pattern.get(), mod).matcher(text.substring(start));
        int count = 0;
        while (m.find()) {
            count++;
        }
        return PlsIntegerVar.of(count);
    }

    /** REGEXP_INSTR внутренняя реализация. */
    private static PlsIntegerVar regexpInstrInternal(Varchar2Var src, Varchar2Var pattern, PlsIntegerVar position,
                                                     PlsIntegerVar occurrence, PlsIntegerVar returnParam,
                                                     Optional<Varchar2Var> modifier, Optional<PlsIntegerVar> subexpr) {
        if (isNull(src) || isNull(pattern)) {
            return PlsIntegerVar.nullValue();
        }
        String mod = modifier.filter(v -> !isNull(v)).map(Var::get).orElse(null);
        int pos = isNull(position) ? 1 : position.get();
        int occ = isNull(occurrence) ? 1 : occurrence.get();
        int ret = isNull(returnParam) ? 0 : returnParam.get();
        int sub = subexpr.filter(v -> !isNull(v)).map(Var::get).orElse(0);
        String text = src.get();
        int start = Math.max(0, pos - 1);
        if (start > text.length()) {
            return PlsIntegerVar.of(0);
        }
        Matcher m = compileOraclePattern(pattern.get(), mod).matcher(text);
        m.region(start, text.length());
        int found = 0;
        while (m.find()) {
            found++;
            if (found == occ) {
                if (sub > 0 && sub <= m.groupCount()) {
                    return PlsIntegerVar.of(start + m.start(sub) + 1);
                }
                if (ret == 0) {
                    return PlsIntegerVar.of(m.start() + 1);
                }
                return PlsIntegerVar.of(m.end() + 1);
            }
        }
        return PlsIntegerVar.of(0);
    }

    /** REGEXP_REPLACE внутренняя реализация. */
    private static Varchar2Var regexpReplaceInternal(Varchar2Var src, Varchar2Var pattern,
                                                     Optional<Varchar2Var> repl, PlsIntegerVar position,
                                                     PlsIntegerVar occurrence, Optional<Varchar2Var> modifier) {
        if (isNull(src) || isNull(pattern)) {
            return Varchar2Var.nullValue();
        }
        String mod = modifier.filter(v -> !isNull(v)).map(Var::get).orElse(null);
        String replacement = repl.filter(v -> !isNull(v)).map(Var::get).orElse("");
        int pos = isNull(position) ? 1 : position.get();
        int occ = isNull(occurrence) ? 0 : occurrence.get();
        String text = src.get();
        int start = Math.max(0, pos - 1);
        if (start > text.length()) {
            return Varchar2Var.of(text);
        }
        Pattern p = compileOraclePattern(pattern.get(), mod);
        if (occ == 0) {
            return Varchar2Var.of(text.substring(0, start) + p.matcher(text.substring(start)).replaceAll(replacement));
        }
        Matcher m = p.matcher(text);
        m.region(start, text.length());
        StringBuilder sb = new StringBuilder(text.substring(0, start));
        int found = 0;
        int lastEnd = start;
        while (m.find()) {
            found++;
            sb.append(text, lastEnd, m.start());
            if (found == occ) {
                sb.append(replacement);
                lastEnd = m.end();
                break;
            }
            lastEnd = m.end();
        }
        sb.append(text.substring(lastEnd));
        return Varchar2Var.of(sb.toString());
    }

    /** REGEXP_SUBSTR внутренняя реализация. */
    private static Varchar2Var regexpSubstrInternal(Varchar2Var src, Varchar2Var pattern, PlsIntegerVar position,
                                                      PlsIntegerVar occurrence, Optional<Varchar2Var> modifier,
                                                      Optional<PlsIntegerVar> subexpr) {
        if (isNull(src) || isNull(pattern)) {
            return Varchar2Var.nullValue();
        }
        String mod = modifier.filter(v -> !isNull(v)).map(Var::get).orElse(null);
        int pos = isNull(position) ? 1 : position.get();
        int occ = isNull(occurrence) ? 1 : occurrence.get();
        int sub = subexpr.filter(v -> !isNull(v)).map(Var::get).orElse(0);
        String text = src.get();
        int start = Math.max(0, pos - 1);
        Matcher m = compileOraclePattern(pattern.get(), mod).matcher(text);
        m.region(start, text.length());
        int found = 0;
        while (m.find()) {
            found++;
            if (found == occ) {
                if (sub > 0 && sub <= m.groupCount()) {
                    return Varchar2Var.of(m.group(sub));
                }
                return Varchar2Var.of(m.group());
            }
        }
        return Varchar2Var.nullValue();
    }

    /** INSTR с параметрами по умолчанию. */
    private static PlsIntegerVar instrWithDefaults(Varchar2Var str1, Varchar2Var str2,
                                                   Optional<PlsIntegerVar> pos, Optional<PlsIntegerVar> nth) {
        if (isNull(str1) || isNull(str2)) {
            return PlsIntegerVar.nullValue();
        }
        int p = pos.filter(v -> !isNull(v)).map(Var::get).orElse(1);
        int n = nth.filter(v -> !isNull(v)).map(Var::get).orElse(1);
        return PlsIntegerVar.of(instrNth(str1.get(), str2.get(), resolveOracleStart(str1.get().length(), p), n));
    }

    /** SUBSTR с длиной по умолчанию. */
    private static Varchar2Var substrWithDefaultLen(Varchar2Var str1, PlsIntegerVar pos, Optional<PlsIntegerVar> len) {
        if (isNull(str1) || isNull(pos)) {
            return Varchar2Var.nullValue();
        }
        int l = len.filter(v -> !isNull(v)).map(Var::get).orElse(Integer.MAX_VALUE);
        return Varchar2Var.of(substrInternal(str1.get(), pos.get(), l));
    }

    /** Преобразование charset для CONVERT. */
    private static String convertCharset(String src, String dest, String source) {
        Charset srcCs = source == null ? StandardCharsets.UTF_8 : Charset.forName(source);
        Charset dstCs = Charset.forName(dest);
        return new String(src.getBytes(srcCs), dstCs);
    }

    /** NVL для любой обёртки. */
    private static <T extends Var<V>, V> T nvlGeneric(T v1, T v2) {
        if (isNull(v1)) {
            return v2 == null ? v1 : v2;
        }
        return v1;
    }

    /** NANVL: замена NaN. */
    private static boolean isNan(double d) {
        return Double.isNaN(d);
    }

    /** EXTRACT для DATE. */
    private static NumberVar extractFromDate(Varchar2Var what, DateVar expr) {
        if (isNull(what) || isNull(expr)) {
            return NumberVar.nullValue();
        }
        LocalDateTime dt = expr.get();
        String w = what.get().toUpperCase(Locale.ROOT);
        return switch (w) {
            case "YEAR", "YYYY", "YY" -> NumberVar.of(dt.getYear());
            case "MONTH", "MM" -> NumberVar.of(dt.getMonthValue());
            case "DAY", "DD" -> NumberVar.of(dt.getDayOfMonth());
            case "HOUR", "HH24" -> NumberVar.of(dt.getHour());
            case "MINUTE", "MI" -> NumberVar.of(dt.getMinute());
            case "SECOND", "SS" -> NumberVar.of(dt.getSecond());
            default -> NumberVar.nullValue();
        };
    }

    /** EXTRACT для TIMESTAMP. */
    private static NumberVar extractFromTimestamp(Varchar2Var what, TimestampVar expr) {
        if (isNull(what) || isNull(expr)) {
            return NumberVar.nullValue();
        }
        return extractFromDate(what, DateVar.of(expr.get().toLocalDateTime()));
    }

    // --- Математические функции ---

    /** ABS для NUMBER. */
    public static NumberVar abs(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(n.get().abs());
    }

    /** ACOS для BINARY_DOUBLE. */
    public static BinaryDoubleVar acos(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.acos(d.get()));
    }

    /** ADD_MONTHS(DATE, NUMBER). */
    public static DateVar addMonths(DateVar left, NumberVar right) {
        if (isNull(left) || isNull(right)) return DateVar.nullValue();
        return DateVar.of(addMonthsInternal(left.get(), right.get().longValue()));
    }

    /** ADD_MONTHS(NUMBER, DATE). */
    public static DateVar addMonths$1(NumberVar left, DateVar right) {
        if (isNull(left) || isNull(right)) return DateVar.nullValue();
        return DateVar.of(addMonthsInternal(right.get(), left.get().longValue()));
    }

    /** ASIN для BINARY_DOUBLE. */
    public static BinaryDoubleVar asin(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.asin(d.get()));
    }

    /** ATAN для BINARY_DOUBLE. */
    public static BinaryDoubleVar atan(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.atan(d.get()));
    }

    /** ATAN2 для BINARY_DOUBLE. */
    public static BinaryDoubleVar atan2(BinaryDoubleVar x, BinaryDoubleVar y) {
        if (isNull(x) || isNull(y)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.atan2(x.get(), y.get()));
    }

    /** BFILENAME — создаёт ссылку BFILE. */
    public static BfileVar bfilename(Varchar2Var directory, Varchar2Var filename) {
        if (isNull(directory) || isNull(filename)) return BfileVar.nullValue();
        return BfileVar.of(directory.get(), filename.get());
    }

    /** BITAND — побитовое И. */
    public static NumberVar bitand(NumberVar left, NumberVar right) {
        if (isNull(left) || isNull(right)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(left.get().longValue() & right.get().longValue()));
    }

    /** CEIL для BINARY_DOUBLE. */
    public static BinaryDoubleVar ceil(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.ceil(d.get()));
    }

    /** CEIL для BINARY_FLOAT. */
    public static BinaryDoubleVar ceil$1(BinaryFloatVar f) {
        if (isNull(f)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.ceil(f.get()));
    }

    /** CEIL для NUMBER. */
    public static NumberVar ceil$2(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(n.get().setScale(0, RoundingMode.CEILING));
    }

    /** CHARTOROWID — преобразование строки в ROWID. */
    public static RowidVar chartorowid(Varchar2Var str) {
        if (isNull(str)) return RowidVar.nullValue();
        return RowidVar.of(str.get());
    }

    /** CHR — символ по коду. */
    public static Varchar2Var chr(PlsIntegerVar n) {
        if (isNull(n)) return Varchar2Var.nullValue();
        int code = n.get();
        if (code < 0) code = code % 256;
        if (code > 0x10FFFF) code = code % 256;
        return Varchar2Var.of(String.valueOf((char) code));
    }

    /** COALESCE без аргументов — заглушка NULL VARCHAR2. */
    public static Varchar2Var coalesce() {
        return Varchar2Var.nullValue();
    }

    /** COMPOSE — NFC-нормализация (заглушка). */
    public static Varchar2Var compose(Varchar2Var ch) {
        if (isNull(ch)) return Varchar2Var.nullValue();
        return Varchar2Var.of(Normalizer.normalize(ch.get(), Normalizer.Form.NFC));
    }

    /** CONCAT двух VARCHAR2. */
    public static Varchar2Var concat(Varchar2Var left, Varchar2Var right) {
        if (isNull(left) || isNull(right)) return Varchar2Var.nullValue();
        return Varchar2Var.of(left.get() + right.get());
    }

    /** CONVERT с набором символов назначения. */
    public static Varchar2Var convert(Varchar2Var src, Varchar2Var destcset) {
        if (isNull(src) || isNull(destcset)) return Varchar2Var.nullValue();
        try {
            return Varchar2Var.of(convertCharset(src.get(), destcset.get(), null));
        } catch (Exception e) {
            log.warn("CONVERT ошибка: {}", e.getMessage());
            return Varchar2Var.of(src.get());
        }
    }

    /** CONVERT с исходным и целевым набором символов. */
    public static Varchar2Var convert$1(Varchar2Var src, Varchar2Var destcset, Varchar2Var srccset) {
        if (isNull(src) || isNull(destcset) || isNull(srccset)) return Varchar2Var.nullValue();
        try {
            return Varchar2Var.of(convertCharset(src.get(), destcset.get(), srccset.get()));
        } catch (Exception e) {
            log.warn("CONVERT ошибка: {}", e.getMessage());
            return Varchar2Var.of(src.get());
        }
    }

    /** COS для BINARY_DOUBLE. */
    public static BinaryDoubleVar cos(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.cos(d.get()));
    }

    /** COS для NUMBER. */
    public static NumberVar cos$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.cos(n.get().doubleValue())));
    }

    /** COSH для BINARY_DOUBLE. */
    public static BinaryDoubleVar cosh(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.cosh(d.get()));
    }

    /** COSH для NUMBER. */
    public static NumberVar cosh$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.cosh(n.get().doubleValue())));
    }

    /** CURRENT_DATE — текущая дата сессии. */
    public static DateVar currentDate() {
        return DateVar.of(LocalDate.now().atStartOfDay());
    }

    /** CURRENT_TIMESTAMP — текущая метка времени с TZ. */
    public static TimestampVar currentTimestamp() {
        return TimestampVar.of(OffsetDateTime.now());
    }

    /** DBTIMEZONE — заглушка часового пояса БД. */
    public static Varchar2Var dbtimezone() {
        return Varchar2Var.of("+00:00");
    }

    /** DECODE(DATE, DATE, VARCHAR2). */
    public static Varchar2Var decode(DateVar expr, DateVar pat, Varchar2Var res) {
        if (isNull(expr) || isNull(pat)) return Varchar2Var.nullValue();
        return datesEqual(expr.get(), pat.get()) ? res : Varchar2Var.nullValue();
    }

    /** DECODE(DATE, DATE, DATE). */
    public static DateVar decode$1(DateVar expr, DateVar pat, DateVar res) {
        if (isNull(expr) || isNull(pat)) return DateVar.nullValue();
        return datesEqual(expr.get(), pat.get()) ? res : DateVar.nullValue();
    }

    /** DECODE(DATE, DATE, NUMBER). */
    public static NumberVar decode$2(DateVar expr, DateVar pat, NumberVar res) {
        if (isNull(expr) || isNull(pat)) return NumberVar.nullValue();
        return datesEqual(expr.get(), pat.get()) ? res : NumberVar.nullValue();
    }

    /** DECODE(NUMBER, NUMBER, DATE). */
    public static DateVar decode$3(NumberVar expr, NumberVar pat, DateVar res) {
        if (isNull(expr) || isNull(pat)) return DateVar.nullValue();
        return numbersEqual(expr.get(), pat.get()) ? res : DateVar.nullValue();
    }

    /** DECODE(NUMBER, NUMBER, NUMBER). */
    public static NumberVar decode$4(NumberVar expr, NumberVar pat, NumberVar res) {
        if (isNull(expr) || isNull(pat)) return NumberVar.nullValue();
        return numbersEqual(expr.get(), pat.get()) ? res : NumberVar.nullValue();
    }

    /** DECODE(NUMBER, NUMBER, VARCHAR2). */
    public static Varchar2Var decode$5(NumberVar expr, NumberVar pat, Varchar2Var res) {
        if (isNull(expr) || isNull(pat)) return Varchar2Var.nullValue();
        return numbersEqual(expr.get(), pat.get()) ? res : Varchar2Var.nullValue();
    }

    /** DECODE(VARCHAR2, VARCHAR2, DATE). */
    public static DateVar decode$6(Varchar2Var expr, Varchar2Var pat, DateVar res) {
        if (isNull(expr) || isNull(pat)) return DateVar.nullValue();
        return expr.get().equals(pat.get()) ? res : DateVar.nullValue();
    }

    /** DECODE(VARCHAR2, VARCHAR2, NUMBER). */
    public static NumberVar decode$7(Varchar2Var expr, Varchar2Var pat, NumberVar res) {
        if (isNull(expr) || isNull(pat)) return NumberVar.nullValue();
        return expr.get().equals(pat.get()) ? res : NumberVar.nullValue();
    }

    /** DECODE(VARCHAR2, VARCHAR2, VARCHAR2). */
    public static Varchar2Var decode$8(Varchar2Var expr, Varchar2Var pat, Varchar2Var res) {
        if (isNull(expr) || isNull(pat)) return Varchar2Var.nullValue();
        return expr.get().equals(pat.get()) ? res : Varchar2Var.nullValue();
    }

    /** DECOMPOSE — NFD-нормализация. */
    public static Varchar2Var decompose(Varchar2Var ch, Optional<Varchar2Var> canmode) {
        if (isNull(ch)) return Varchar2Var.nullValue();
        return Varchar2Var.of(Normalizer.normalize(ch.get(), Normalizer.Form.NFD));
    }

    /** EMPTY_BLOB — пустой LOB. */
    public static BlobVar emptyBlob() {
        return BlobVar.empty();
    }

    /** EMPTY_CLOB — пустой LOB. */
    public static ClobVar emptyClob() {
        return ClobVar.empty();
    }

    /** EXP для BINARY_DOUBLE. */
    public static BinaryDoubleVar exp(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.exp(d.get()));
    }

    /** EXP для NUMBER. */
    public static NumberVar exp$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.exp(n.get().doubleValue())));
    }

    /** FLOOR для BINARY_DOUBLE. */
    public static BinaryDoubleVar floor(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.floor(d.get()));
    }

    /** FLOOR для BINARY_FLOAT. */
    public static BinaryDoubleVar floor$1(BinaryFloatVar f) {
        if (isNull(f)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.floor(f.get()));
    }

    /** FLOOR для NUMBER. */
    public static NumberVar floor$2(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(n.get().setScale(0, RoundingMode.FLOOR));
    }

    /** FROM_TZ — привязка TZ к timestamp. */
    public static TimestampVar fromTz(TimestampVar t, Varchar2Var timezone) {
        if (isNull(t) || isNull(timezone)) return TimestampVar.nullValue();
        ZoneId zone = ZoneId.of(timezone.get());
        return TimestampVar.of(t.get().toLocalDateTime().atZone(zone).toOffsetDateTime());
    }

    /** GREATEST для BINARY_DOUBLE (один аргумент). */
    public static BinaryDoubleVar greatest(BinaryDoubleVar pattern) {
        if (isNull(pattern)) return BinaryDoubleVar.nullValue();
        return pattern;
    }

    /** GREATEST для BINARY_FLOAT. */
    public static BinaryFloatVar greatest$1(BinaryFloatVar pattern) {
        if (isNull(pattern)) return BinaryFloatVar.nullValue();
        return pattern;
    }

    /** GREATEST для DATE. */
    public static DateVar greatest$2(DateVar pattern) {
        if (isNull(pattern)) return DateVar.nullValue();
        return pattern;
    }

    /** GREATEST для DSINTERVAL. */
    public static DsIntervalVar greatest$3(DsIntervalVar pattern) {
        if (isNull(pattern)) return DsIntervalVar.nullValue();
        return pattern;
    }

    /** GREATEST для NUMBER. */
    public static NumberVar greatest$4(NumberVar pattern) {
        if (isNull(pattern)) return NumberVar.nullValue();
        return pattern;
    }

    /** GREATEST для TIMESTAMP_LTZ. */
    public static TimestampVar greatest$5(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** GREATEST для TIMESTAMP_TZ. */
    public static TimestampVar greatest$6(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** GREATEST для TIMESTAMP. */
    public static TimestampVar greatest$7(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** GREATEST для VARCHAR2. */
    public static Varchar2Var greatest$8(Varchar2Var pattern) {
        if (isNull(pattern)) return Varchar2Var.nullValue();
        return pattern;
    }

    /** GREATEST для YMINTERVAL. */
    public static YmIntervalVar greatest$9(YmIntervalVar pattern) {
        if (isNull(pattern)) return YmIntervalVar.nullValue();
        return pattern;
    }

    /** HEXTORAW — шестнадцатеричная строка в RAW. */
    public static RawVar hextoraw(Varchar2Var c) {
        if (isNull(c)) return RawVar.nullValue();
        return RawVar.ofHex(c.get());
    }

    /** INITCAP — заглавная буква каждого слова. */
    public static Varchar2Var initcap(Varchar2Var ch) {
        if (isNull(ch)) return Varchar2Var.nullValue();
        String[] parts = ch.get().split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return Varchar2Var.of(sb.toString());
    }

    /** INSTR(VARCHAR2, VARCHAR2). */
    public static PlsIntegerVar instr(Varchar2Var str1, Varchar2Var str2) {
        return instr$1(str1, str2, PlsIntegerVar.of(1));
    }

    /** INSTR с позицией. */
    public static PlsIntegerVar instr$1(Varchar2Var str1, Varchar2Var str2, PlsIntegerVar pos) {
        return instr$2(str1, str2, pos, PlsIntegerVar.of(1));
    }

    /** INSTR с позицией и номером вхождения. */
    public static PlsIntegerVar instr$2(Varchar2Var str1, Varchar2Var str2, PlsIntegerVar pos, PlsIntegerVar nth) {
        if (isNull(str1) || isNull(str2) || isNull(pos) || isNull(nth)) return PlsIntegerVar.nullValue();
        int start = resolveOracleStart(str1.get().length(), pos.get());
        return PlsIntegerVar.of(instrNth(str1.get(), str2.get(), start, nth.get()));
    }

    /** INSTR2 — UTF-16 (делегирует INSTR). */
    public static PlsIntegerVar instr2(Varchar2Var str1, Varchar2Var str2, Optional<PlsIntegerVar> pos, Optional<PlsIntegerVar> nth) {
        return instrWithDefaults(str1, str2, pos, nth);
    }

    /** INSTR4 — UTF-32 (делегирует INSTR). */
    public static PlsIntegerVar instr4(Varchar2Var str1, Varchar2Var str2, Optional<PlsIntegerVar> pos, Optional<PlsIntegerVar> nth) {
        return instrWithDefaults(str1, str2, pos, nth);
    }

    /** INSTRB — поиск в байтах UTF-8. */
    public static PlsIntegerVar instrb(Varchar2Var str1, Varchar2Var str2, Optional<PlsIntegerVar> pos, Optional<PlsIntegerVar> nth) {
        if (isNull(str1) || isNull(str2)) return PlsIntegerVar.nullValue();
        byte[] hay = str1.get().getBytes(StandardCharsets.UTF_8);
        byte[] needle = str2.get().getBytes(StandardCharsets.UTF_8);
        String h = new String(hay, StandardCharsets.ISO_8859_1);
        String n = new String(needle, StandardCharsets.ISO_8859_1);
        int p = pos.filter(v -> !isNull(v)).map(Var::get).orElse(1);
        int nt = nth.filter(v -> !isNull(v)).map(Var::get).orElse(1);
        return PlsIntegerVar.of(instrNth(h, n, resolveOracleStart(h.length(), p), nt));
    }

    /** INSTRC — поиск с учётом collation (делегирует INSTR). */
    public static PlsIntegerVar instrc(Varchar2Var str1, Varchar2Var str2, Optional<PlsIntegerVar> pos, Optional<PlsIntegerVar> nth) {
        return instrWithDefaults(str1, str2, pos, nth);
    }

    /** LAST_DAY — последний день месяца. */
    public static DateVar lastDay(DateVar right) {
        if (isNull(right)) return DateVar.nullValue();
        LocalDateTime dt = right.get();
        LocalDate last = dt.toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
        return DateVar.of(LocalDateTime.of(last, dt.toLocalTime()));
    }

    /** LEAST для BINARY_DOUBLE. */
    public static BinaryDoubleVar least(BinaryDoubleVar pattern) {
        if (isNull(pattern)) return BinaryDoubleVar.nullValue();
        return pattern;
    }

    /** LEAST для BINARY_FLOAT. */
    public static BinaryFloatVar least$1(BinaryFloatVar pattern) {
        if (isNull(pattern)) return BinaryFloatVar.nullValue();
        return pattern;
    }

    /** LEAST для DATE. */
    public static DateVar least$2(DateVar pattern) {
        if (isNull(pattern)) return DateVar.nullValue();
        return pattern;
    }

    /** LEAST для DSINTERVAL. */
    public static DsIntervalVar least$3(DsIntervalVar pattern) {
        if (isNull(pattern)) return DsIntervalVar.nullValue();
        return pattern;
    }

    /** LEAST для NUMBER. */
    public static NumberVar least$4(NumberVar pattern) {
        if (isNull(pattern)) return NumberVar.nullValue();
        return pattern;
    }

    /** LEAST для TIMESTAMP_LTZ. */
    public static TimestampVar least$5(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** LEAST для TIMESTAMP_TZ. */
    public static TimestampVar least$6(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** LEAST для TIMESTAMP. */
    public static TimestampVar least$7(TimestampVar pattern) {
        if (isNull(pattern)) return TimestampVar.nullValue();
        return pattern;
    }

    /** LEAST для VARCHAR2. */
    public static Varchar2Var least$8(Varchar2Var pattern) {
        if (isNull(pattern)) return Varchar2Var.nullValue();
        return pattern;
    }

    /** LEAST для YMINTERVAL. */
    public static YmIntervalVar least$9(YmIntervalVar pattern) {
        if (isNull(pattern)) return YmIntervalVar.nullValue();
        return pattern;
    }

    /** LENGTH для BLOB. */
    public static PlsIntegerVar length(BlobVar bl) {
        if (isNull(bl)) return PlsIntegerVar.nullValue();
        byte[] b = bl.get();
        return PlsIntegerVar.of(b == null ? 0 : b.length);
    }

    /** LENGTH для VARCHAR2 (символы). */
    public static PlsIntegerVar length$1(Varchar2Var ch) {
        if (isNull(ch)) return PlsIntegerVar.nullValue();
        return PlsIntegerVar.of(ch.get().length());
    }

    /** LENGTH2 — UTF-16 единицы. */
    public static PlsIntegerVar length2(Varchar2Var ch) {
        return length$1(ch);
    }

    /** LENGTH4 — UTF-32 единицы (заглушка). */
    public static PlsIntegerVar length4(Varchar2Var ch) {
        return length$1(ch);
    }

    /** LENGTHB для BLOB. */
    public static PlsIntegerVar lengthb(BlobVar bl) {
        return length(bl);
    }

    /** LENGTHB для VARCHAR2 — длина в байтах UTF-8. */
    public static NumberVar lengthb$1(Varchar2Var ch) {
        if (isNull(ch)) return NumberVar.nullValue();
        return NumberVar.of(ch.get().getBytes(StandardCharsets.UTF_8).length);
    }

    /** LENGTHC — длина с учётом collation (заглушка). */
    public static PlsIntegerVar lengthc(Varchar2Var ch) {
        return length$1(ch);
    }

    /** LEVEL — псевдостолбец иерархии (заглушка). */
    public static NumberVar level() {
        return NumberVar.of(1);
    }

    /** LN для BINARY_DOUBLE. */
    public static BinaryDoubleVar ln(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.log(d.get()));
    }

    /** LN для NUMBER. */
    public static NumberVar ln$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.log(n.get().doubleValue())));
    }

    /** LOCALTIMESTAMP — текущее время без TZ. */
    public static TimestampVar localtimestamp() {
        return TimestampVar.of(OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now())));
    }

    /** LOG для BINARY_DOUBLE. */
    public static BinaryDoubleVar log(BinaryDoubleVar left, BinaryDoubleVar right) {
        if (isNull(left) || isNull(right)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.log(right.get()) / Math.log(left.get()));
    }

    /** LOG для NUMBER. */
    public static NumberVar log$1(NumberVar left, NumberVar right) {
        if (isNull(left) || isNull(right)) return NumberVar.nullValue();
        double r = Math.log(right.get().doubleValue()) / Math.log(left.get().doubleValue());
        return NumberVar.of(BigDecimal.valueOf(r));
    }

    /** LOWER — нижний регистр. */
    public static Varchar2Var lower(Varchar2Var ch) {
        if (isNull(ch)) return Varchar2Var.nullValue();
        return Varchar2Var.of(ch.get().toLowerCase(Locale.ROOT));
    }

    /** LPAD с пробелом. */
    public static Varchar2Var lpad(Varchar2Var str1, PlsIntegerVar len) {
        return lpad$1(str1, len, Varchar2Var.of(" "));
    }

    /** LPAD с указанным заполнителем. */
    public static Varchar2Var lpad$1(Varchar2Var str1, PlsIntegerVar len, Varchar2Var pad) {
        if (isNull(str1) || isNull(len)) return Varchar2Var.nullValue();
        String p = isNull(pad) ? " " : pad.get();
        return Varchar2Var.of(padInternal(str1.get(), len.get(), p, true));
    }

    /** LTRIM пробелы. */
    public static Varchar2Var ltrim(Varchar2Var str1) {
        if (isNull(str1)) return Varchar2Var.nullValue();
        return Varchar2Var.of(trimSet(str1.get(), " ", true, false));
    }

    /** LTRIM по набору символов. */
    public static Varchar2Var ltrim$1(Varchar2Var str1, Varchar2Var tset) {
        if (isNull(str1)) return Varchar2Var.nullValue();
        return Varchar2Var.of(trimSet(str1.get(), isNull(tset) ? " " : tset.get(), true, false));
    }

    /** MOD для NUMBER. */
    public static NumberVar mod(NumberVar n1, NumberVar n2) {
        if (isNull(n1) || isNull(n2)) return NumberVar.nullValue();
        return NumberVar.of(oracleMod(n1.get(), n2.get()));
    }

    /** MONTHS_BETWEEN — разница в месяцах. */
    public static NumberVar monthsBetween(DateVar left, DateVar right) {
        if (isNull(left) || isNull(right)) return NumberVar.nullValue();
        return NumberVar.of(monthsBetweenInternal(left.get(), right.get()));
    }

    /** NANVL для BINARY_DOUBLE. */
    public static BinaryDoubleVar nanvl(BinaryDoubleVar d1, BinaryDoubleVar d2) {
        if (isNull(d1)) return d2 == null ? BinaryDoubleVar.nullValue() : d2;
        if (isNan(d1.get())) return d2 == null ? BinaryDoubleVar.nullValue() : d2;
        return d1;
    }

    /** NANVL для BINARY_FLOAT. */
    public static BinaryFloatVar nanvl$1(BinaryFloatVar f1, BinaryFloatVar f2) {
        if (isNull(f1)) return f2 == null ? BinaryFloatVar.nullValue() : f2;
        if (Float.isNaN(f1.get())) return f2 == null ? BinaryFloatVar.nullValue() : f2;
        return f1;
    }

    /** NANVL для NUMBER. */
    public static NumberVar nanvl$2(NumberVar n1, NumberVar n2) {
        if (isNull(n1)) return n2 == null ? NumberVar.nullValue() : n2;
        return n1;
    }

    /** NCHARTOROWID. */
    public static RowidVar nchartorowid(Nvarchar2Var str) {
        if (isNull(str)) return RowidVar.nullValue();
        return RowidVar.of(str.get());
    }

    /** NCHR — символ NVARCHAR2. */
    public static Nvarchar2Var nchr(PlsIntegerVar n) {
        Varchar2Var v = chr(n);
        if (isNull(v)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(v.get());
    }

    /** NEXT_DAY — следующий день недели. */
    public static DateVar nextDay(DateVar left, Varchar2Var right) {
        if (isNull(left) || isNull(right)) return DateVar.nullValue();
        return DateVar.of(nextDayInternal(left.get(), right.get()));
    }

    /** NHEXTORAW для NVARCHAR2. */
    public static RawVar nhextoraw(Nvarchar2Var c) {
        if (isNull(c)) return RawVar.nullValue();
        return RawVar.ofHex(c.get());
    }

    /** NLS_CHARSET_DECL_LEN — заглушка. */
    public static PlsIntegerVar nlsCharsetDeclLen(NumberVar bytecnt, NumberVar csetid) {
        if (isNull(bytecnt) || isNull(csetid)) return PlsIntegerVar.nullValue();
        return PlsIntegerVar.of(bytecnt.get().intValue());
    }

    /** NLS_CHARSET_ID — заглушка. */
    public static PlsIntegerVar nlsCharsetId(Varchar2Var csetname) {
        if (isNull(csetname)) return PlsIntegerVar.nullValue();
        return PlsIntegerVar.of(csetname.get().hashCode() & 0xFFFF);
    }

    /** NLS_CHARSET_NAME — заглушка. */
    public static Varchar2Var nlsCharsetName(PlsIntegerVar csetid) {
        if (isNull(csetid)) return Varchar2Var.nullValue();
        return Varchar2Var.of("AL32UTF8");
    }

    /** NLS_LOWER для CLOB. */
    public static ClobVar nlsLower(ClobVar ch) {
        if (isNull(ch)) return ClobVar.nullValue();
        return ClobVar.of(ch.get().toLowerCase(Locale.ROOT));
    }

    /** NLS_LOWER для CLOB с параметрами NLS. */
    public static ClobVar nlsLower$1(ClobVar ch, Varchar2Var parms) {
        return nlsLower(ch);
    }

    /** NLS_UPPER для CLOB. */
    public static ClobVar nlsUpper(ClobVar ch) {
        if (isNull(ch)) return ClobVar.nullValue();
        return ClobVar.of(ch.get().toUpperCase(Locale.ROOT));
    }

    /** NLS_UPPER для CLOB с параметрами NLS. */
    public static ClobVar nlsUpper$1(ClobVar ch, Varchar2Var parms) {
        return nlsUpper(ch);
    }

    /** NULLFN — заглушка возвращает NULL RAW. */
    public static RawVar nullfn(Varchar2Var str) {
        return RawVar.nullValue();
    }

    /** NULLIF для BOOLEAN. */
    public static BooleanVar nullif(BooleanVar v1, BooleanVar v2) {
        if (isNull(v1) || isNull(v2)) return BooleanVar.nullValue();
        if (v1.get().equals(v2.get())) return BooleanVar.nullValue();
        return v1;
    }

    /** NULLIF для VARCHAR2. */
    public static Varchar2Var nullif$1(Varchar2Var v1, Varchar2Var v2) {
        if (isNull(v1) || isNull(v2)) return Varchar2Var.nullValue();
        if (v1.get().equals(v2.get())) return Varchar2Var.nullValue();
        return v1;
    }

    /** NUMTODSINTERVAL — число в интервал DS. */
    public static DsIntervalVar numtodsinterval(NumberVar numerator, Varchar2Var units) {
        if (isNull(numerator) || isNull(units)) return DsIntervalVar.nullValue();
        long n = numerator.get().longValue();
        Duration d = switch (units.get().toUpperCase(Locale.ROOT)) {
            case "SECOND", "SEC", "SS" -> Duration.ofSeconds(n);
            case "MINUTE", "MIN", "MI" -> Duration.ofMinutes(n);
            case "HOUR", "HR", "HH" -> Duration.ofHours(n);
            case "DAY", "DD" -> Duration.ofDays(n);
            default -> Duration.ofSeconds(n);
        };
        return DsIntervalVar.of(d);
    }

    /** NUMTOYMINTERVAL — число в интервал YM. */
    public static YmIntervalVar numtoyminterval(NumberVar numerator, Varchar2Var units) {
        if (isNull(numerator) || isNull(units)) return YmIntervalVar.nullValue();
        long n = numerator.get().longValue();
        Period p = switch (units.get().toUpperCase(Locale.ROOT)) {
            case "YEAR", "YYYY", "YY" -> Period.ofYears((int) n);
            case "MONTH", "MM", "MON" -> Period.ofMonths((int) n);
            default -> Period.ofMonths((int) n);
        };
        return YmIntervalVar.of(p);
    }

    /** NVL для BOOLEAN. */
    public static BooleanVar nvl(BooleanVar b1, BooleanVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для DSINTERVAL. */
    public static DsIntervalVar nvl$1(DsIntervalVar b1, DsIntervalVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для TIMESTAMP_LTZ. */
    public static TimestampVar nvl$2(TimestampVar b1, TimestampVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для TIMESTAMP_TZ. */
    public static TimestampVar nvl$3(TimestampVar b1, TimestampVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для TIMESTAMP. */
    public static TimestampVar nvl$4(TimestampVar b1, TimestampVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для YMINTERVAL. */
    public static YmIntervalVar nvl$5(YmIntervalVar b1, YmIntervalVar b2) {
        return nvlGeneric(b1, b2);
    }

    /** NVL для DATE. */
    public static DateVar nvl$6(DateVar d1, DateVar d2) {
        return nvlGeneric(d1, d2);
    }

    /** NVL для BINARY_DOUBLE. */
    public static BinaryDoubleVar nvl$7(BinaryDoubleVar d1, BinaryDoubleVar d2) {
        return nvlGeneric(d1, d2);
    }

    /** NVL для BINARY_FLOAT. */
    public static BinaryFloatVar nvl$8(BinaryFloatVar f1, BinaryFloatVar f2) {
        return nvlGeneric(f1, f2);
    }

    /** NVL для NUMBER. */
    public static NumberVar nvl$9(NumberVar n1, NumberVar n2) {
        return nvlGeneric(n1, n2);
    }

    /** NVL для VARCHAR2. */
    public static Varchar2Var nvl$10(Varchar2Var s1, Varchar2Var s2) {
        return nvlGeneric(s1, s2);
    }

    /** NVL для PLS_INTEGER. */
    public static PlsIntegerVar nvl$11(PlsIntegerVar i1, PlsIntegerVar i2) {
        return nvlGeneric(i1, i2);
    }

    /** POWER для BINARY_DOUBLE. */
    public static BinaryDoubleVar power(BinaryDoubleVar d, BinaryDoubleVar e) {
        if (isNull(d) || isNull(e)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.pow(d.get(), e.get()));
    }

    /** POWER для NUMBER. */
    public static NumberVar power$1(NumberVar n, NumberVar e) {
        if (isNull(n) || isNull(e)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.pow(n.get().doubleValue(), e.get().doubleValue())));
    }

    /** RAWTOHEX — RAW в hex-строку. */
    public static Varchar2Var rawtohex(RawVar r) {
        if (isNull(r)) return Varchar2Var.nullValue();
        return Varchar2Var.of(r.toHex());
    }

    /** RAWTONHEX — RAW в NVARCHAR2 hex. */
    public static Nvarchar2Var rawtonhex(RawVar r) {
        if (isNull(r)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(r.toHex());
    }

    /** REGEXP_COUNT (2 аргумента). */
    public static PlsIntegerVar regexpCount(Varchar2Var srcstr, Varchar2Var pattern) {
        return regexpCountInternal(srcstr, pattern, PlsIntegerVar.of(1), Optional.empty());
    }

    /** REGEXP_COUNT с позицией. */
    public static PlsIntegerVar regexpCount$1(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position) {
        return regexpCountInternal(srcstr, pattern, position, Optional.empty());
    }

    /** REGEXP_COUNT с модификатором. */
    public static PlsIntegerVar regexpCount$2(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, Varchar2Var modifier) {
        return regexpCountInternal(srcstr, pattern, position, Optional.ofNullable(modifier));
    }

    /** REGEXP_INSTR (2 аргумента). */
    public static PlsIntegerVar regexpInstr(Varchar2Var srcstr, Varchar2Var pattern) {
        return regexpInstrInternal(srcstr, pattern, PlsIntegerVar.of(1), PlsIntegerVar.of(1), PlsIntegerVar.of(0), Optional.empty(), Optional.empty());
    }

    /** REGEXP_INSTR с позицией. */
    public static PlsIntegerVar regexpInstr$1(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position) {
        return regexpInstrInternal(srcstr, pattern, position, PlsIntegerVar.of(1), PlsIntegerVar.of(0), Optional.empty(), Optional.empty());
    }

    /** REGEXP_INSTR с occurrence. */
    public static PlsIntegerVar regexpInstr$2(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence) {
        return regexpInstrInternal(srcstr, pattern, position, occurrence, PlsIntegerVar.of(0), Optional.empty(), Optional.empty());
    }

    /** REGEXP_INSTR с returnparam. */
    public static PlsIntegerVar regexpInstr$3(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence, PlsIntegerVar returnparam) {
        return regexpInstrInternal(srcstr, pattern, position, occurrence, returnparam, Optional.empty(), Optional.empty());
    }

    /** REGEXP_INSTR с modifier. */
    public static PlsIntegerVar regexpInstr$4(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence, PlsIntegerVar returnparam, Varchar2Var modifier) {
        return regexpInstrInternal(srcstr, pattern, position, occurrence, returnparam, Optional.ofNullable(modifier), Optional.empty());
    }

    /** REGEXP_INSTR с subexpression. */
    public static PlsIntegerVar regexpInstr$5(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence, PlsIntegerVar returnparam, Varchar2Var modifier, PlsIntegerVar subexpression) {
        return regexpInstrInternal(srcstr, pattern, position, occurrence, returnparam, Optional.ofNullable(modifier), Optional.ofNullable(subexpression));
    }

    /** REGEXP_LIKE (2 аргумента). */
    public static BooleanVar regexpLike(Varchar2Var srcstr, Varchar2Var pattern) {
        return regexpLike$1(srcstr, pattern, Varchar2Var.nullValue());
    }

    /** REGEXP_LIKE с modifier. */
    public static BooleanVar regexpLike$1(Varchar2Var srcstr, Varchar2Var pattern, Varchar2Var modifier) {
        if (isNull(srcstr) || isNull(pattern)) return BooleanVar.nullValue();
        String mod = isNull(modifier) ? null : modifier.get();
        boolean match = compileOraclePattern(pattern.get(), mod).matcher(srcstr.get()).find();
        return BooleanVar.of(match);
    }

    /** REGEXP_REPLACE (2 аргумента). */
    public static Varchar2Var regexpReplace(Varchar2Var srcstr, Varchar2Var pattern) {
        return regexpReplaceInternal(srcstr, pattern, Optional.empty(), PlsIntegerVar.of(1), PlsIntegerVar.of(0), Optional.empty());
    }

    /** REGEXP_REPLACE с replacestr. */
    public static Varchar2Var regexpReplace$1(Varchar2Var srcstr, Varchar2Var pattern, Varchar2Var replacestr) {
        return regexpReplaceInternal(srcstr, pattern, Optional.ofNullable(replacestr), PlsIntegerVar.of(1), PlsIntegerVar.of(0), Optional.empty());
    }

    /** REGEXP_REPLACE с position. */
    public static Varchar2Var regexpReplace$2(Varchar2Var srcstr, Varchar2Var pattern, Varchar2Var replacestr, PlsIntegerVar position) {
        return regexpReplaceInternal(srcstr, pattern, Optional.ofNullable(replacestr), position, PlsIntegerVar.of(0), Optional.empty());
    }

    /** REGEXP_REPLACE с occurrence. */
    public static Varchar2Var regexpReplace$3(Varchar2Var srcstr, Varchar2Var pattern, Varchar2Var replacestr, PlsIntegerVar position, PlsIntegerVar occurrence) {
        return regexpReplaceInternal(srcstr, pattern, Optional.ofNullable(replacestr), position, occurrence, Optional.empty());
    }

    /** REGEXP_REPLACE с modifier. */
    public static Varchar2Var regexpReplace$4(Varchar2Var srcstr, Varchar2Var pattern, Varchar2Var replacestr, PlsIntegerVar position, PlsIntegerVar occurrence, Varchar2Var modifier) {
        return regexpReplaceInternal(srcstr, pattern, Optional.ofNullable(replacestr), position, occurrence, Optional.ofNullable(modifier));
    }

    /** REGEXP_SUBSTR (2 аргумента). */
    public static Varchar2Var regexpSubstr(Varchar2Var srcstr, Varchar2Var pattern) {
        return regexpSubstrInternal(srcstr, pattern, PlsIntegerVar.of(1), PlsIntegerVar.of(1), Optional.empty(), Optional.empty());
    }

    /** REGEXP_SUBSTR с position. */
    public static Varchar2Var regexpSubstr$1(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position) {
        return regexpSubstrInternal(srcstr, pattern, position, PlsIntegerVar.of(1), Optional.empty(), Optional.empty());
    }

    /** REGEXP_SUBSTR с occurrence. */
    public static Varchar2Var regexpSubstr$2(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence) {
        return regexpSubstrInternal(srcstr, pattern, position, occurrence, Optional.empty(), Optional.empty());
    }

    /** REGEXP_SUBSTR с modifier. */
    public static Varchar2Var regexpSubstr$3(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence, Varchar2Var modifier) {
        return regexpSubstrInternal(srcstr, pattern, position, occurrence, Optional.ofNullable(modifier), Optional.empty());
    }

    /** REGEXP_SUBSTR с subexpression. */
    public static Varchar2Var regexpSubstr$4(Varchar2Var srcstr, Varchar2Var pattern, PlsIntegerVar position, PlsIntegerVar occurrence, Varchar2Var modifier, PlsIntegerVar subexpression) {
        return regexpSubstrInternal(srcstr, pattern, position, occurrence, Optional.ofNullable(modifier), Optional.ofNullable(subexpression));
    }

    /** REM для NUMBER. */
    public static NumberVar rem(NumberVar left, NumberVar right) {
        if (isNull(left) || isNull(right)) return NumberVar.nullValue();
        return NumberVar.of(oracleRem(left.get(), right.get()));
    }

    /** REPLACE без newsub. */
    public static Varchar2Var replace(Varchar2Var srcstr, Varchar2Var oldsub) {
        return replace$1(srcstr, oldsub, Varchar2Var.nullValue());
    }

    /** REPLACE с newsub. */
    public static Varchar2Var replace$1(Varchar2Var srcstr, Varchar2Var oldsub, Varchar2Var newsub) {
        if (isNull(srcstr) || isNull(oldsub)) return Varchar2Var.nullValue();
        String rep = isNull(newsub) ? "" : newsub.get();
        return Varchar2Var.of(srcstr.get().replace(oldsub.get(), rep));
    }

    /** ROUND NUMBER без places. */
    public static NumberVar round(NumberVar left) {
        return round$1(left, PlsIntegerVar.of(0));
    }

    /** ROUND NUMBER с places. */
    public static NumberVar round$1(NumberVar left, PlsIntegerVar right) {
        if (isNull(left) || isNull(right)) return NumberVar.nullValue();
        return NumberVar.of(roundNumber(left.get(), right.get()));
    }

    /** ROWIDTOCHAR. */
    public static Varchar2Var rowidtochar(RowidVar str) {
        if (isNull(str)) return Varchar2Var.nullValue();
        return Varchar2Var.of(str.get());
    }

    /** ROWIDTONCHAR. */
    public static Nvarchar2Var rowidtonchar(RowidVar str) {
        if (isNull(str)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(str.get());
    }

    /** ROWNUM — псевдостолбец (заглушка). */
    public static NumberVar rownum() {
        return NumberVar.of(1);
    }

    /** RPAD для CLOB. */
    public static ClobVar rpad(ClobVar str1, PlsIntegerVar len) {
        if (isNull(str1) || isNull(len)) return ClobVar.nullValue();
        return ClobVar.of(padInternal(str1.get(), len.get(), " ", false));
    }

    /** RPAD для CLOB с pad. */
    public static ClobVar rpad$1(ClobVar str1, PlsIntegerVar len, ClobVar pad) {
        if (isNull(str1) || isNull(len)) return ClobVar.nullValue();
        String p = isNull(pad) ? " " : pad.get();
        return ClobVar.of(padInternal(str1.get(), len.get(), p, false));
    }

    /** RPAD для VARCHAR2. */
    public static Varchar2Var rpad$2(Varchar2Var str1, PlsIntegerVar len) {
        return rpad$3(str1, len, Varchar2Var.of(" "));
    }

    /** RPAD для VARCHAR2 с pad. */
    public static Varchar2Var rpad$3(Varchar2Var str1, PlsIntegerVar len, Varchar2Var pad) {
        if (isNull(str1) || isNull(len)) return Varchar2Var.nullValue();
        String p = isNull(pad) ? " " : pad.get();
        return Varchar2Var.of(padInternal(str1.get(), len.get(), p, false));
    }

    /** RTRIM пробелы. */
    public static Varchar2Var rtrim(Varchar2Var str1) {
        if (isNull(str1)) return Varchar2Var.nullValue();
        return Varchar2Var.of(trimSet(str1.get(), " ", false, true));
    }

    /** RTRIM по набору символов. */
    public static Varchar2Var rtrim$1(Varchar2Var str1, Varchar2Var tset) {
        if (isNull(str1)) return Varchar2Var.nullValue();
        return Varchar2Var.of(trimSet(str1.get(), isNull(tset) ? " " : tset.get(), false, true));
    }

    /** SESSIONTIMEZONE — заглушка. */
    public static Varchar2Var sessiontimezone() {
        return Varchar2Var.of(ZoneId.systemDefault().getId());
    }

    /** SIGN для BINARY_DOUBLE. */
    public static PlsIntegerVar sign(BinaryDoubleVar d) {
        if (isNull(d)) return PlsIntegerVar.nullValue();
        return signOf(BigDecimal.valueOf(d.get()));
    }

    /** SIGN для BINARY_FLOAT. */
    public static PlsIntegerVar sign$1(BinaryFloatVar f) {
        if (isNull(f)) return PlsIntegerVar.nullValue();
        return signOf(BigDecimal.valueOf(f.get()));
    }

    /** SIGN для NUMBER. */
    public static PlsIntegerVar sign$2(NumberVar n) {
        if (isNull(n)) return PlsIntegerVar.nullValue();
        return signOf(n.get());
    }

    /** SIN для BINARY_DOUBLE. */
    public static BinaryDoubleVar sin(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.sin(d.get()));
    }

    /** SIN для NUMBER. */
    public static NumberVar sin$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.sin(n.get().doubleValue())));
    }

    /** SINH для BINARY_DOUBLE. */
    public static BinaryDoubleVar sinh(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.sinh(d.get()));
    }

    /** SINH для NUMBER. */
    public static NumberVar sinh$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.sinh(n.get().doubleValue())));
    }

    /** SQLCODE — заглушка. */
    public static PlsIntegerVar sqlcode() {
        return PlsIntegerVar.of(0);
    }

    /** SQLERRM с кодом. */
    public static Varchar2Var sqlerrm(PlsIntegerVar code) {
        if (isNull(code)) return Varchar2Var.nullValue();
        return Varchar2Var.of("ORA-" + String.format("%05d", Math.abs(code.get())) + ": эмуляция SQLERRM");
    }

    /** SQLERRM без кода. */
    public static Varchar2Var sqlerrm$1() {
        return Varchar2Var.of("ORA-0000: normal, successful completion");
    }

    /** SQRT для BINARY_DOUBLE. */
    public static BinaryDoubleVar sqrt(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.sqrt(d.get()));
    }

    /** SQRT для BINARY_FLOAT. */
    public static BinaryFloatVar sqrt$1(BinaryFloatVar f) {
        if (isNull(f)) return BinaryFloatVar.nullValue();
        return BinaryFloatVar.of((float) Math.sqrt(f.get()));
    }

    /** SQRT для NUMBER. */
    public static NumberVar sqrt$2(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.sqrt(n.get().doubleValue())));
    }

    /** SUBSTR(VARCHAR2, POS). */
    public static Varchar2Var substr(Varchar2Var str1, PlsIntegerVar pos) {
        return substr$1(str1, pos, PlsIntegerVar.of(Integer.MAX_VALUE));
    }

    /** SUBSTR(VARCHAR2, POS, LEN). */
    public static Varchar2Var substr$1(Varchar2Var str1, PlsIntegerVar pos, PlsIntegerVar len) {
        if (isNull(str1) || isNull(pos) || isNull(len)) return Varchar2Var.nullValue();
        return Varchar2Var.of(substrInternal(str1.get(), pos.get(), len.get()));
    }

    /** SUBSTR2 с len по умолчанию. */
    public static Varchar2Var substr2(Varchar2Var str1, PlsIntegerVar pos, Optional<PlsIntegerVar> len) {
        return substrWithDefaultLen(str1, pos, len);
    }

    /** SUBSTR4 с len по умолчанию. */
    public static Varchar2Var substr4(Varchar2Var str1, PlsIntegerVar pos, Optional<PlsIntegerVar> len) {
        return substrWithDefaultLen(str1, pos, len);
    }

    /** SUBSTRB(VARCHAR2, POS). */
    public static Varchar2Var substrb(Varchar2Var str1, PlsIntegerVar pos) {
        if (isNull(str1) || isNull(pos)) return Varchar2Var.nullValue();
        byte[] bytes = str1.get().getBytes(StandardCharsets.UTF_8);
        int start = resolveOracleStart(bytes.length, pos.get());
        if (start < 0 || start >= bytes.length) return Varchar2Var.of("");
        return Varchar2Var.of(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8));
    }

    /** SUBSTRB(VARCHAR2, POS, LEN). */
    public static Varchar2Var substrb$1(Varchar2Var str1, PlsIntegerVar pos, PlsIntegerVar len) {
        if (isNull(str1) || isNull(pos) || isNull(len)) return Varchar2Var.nullValue();
        return Varchar2Var.of(substrbInternal(str1.get(), pos.get(), len.get()));
    }

    /** SUBSTRC с len по умолчанию. */
    public static Varchar2Var substrc(Varchar2Var str1, PlsIntegerVar pos, Optional<PlsIntegerVar> len) {
        return substrWithDefaultLen(str1, pos, len);
    }

    /** SYS_AT_TIME_ZONE — заглушка смены TZ. */
    public static TimestampVar sysAtTimeZone(TimestampVar t, Varchar2Var i) {
        if (isNull(t) || isNull(i)) return TimestampVar.nullValue();
        ZoneId zone = ZoneId.of(i.get());
        return TimestampVar.of(t.get().atZoneSameInstant(zone).toOffsetDateTime());
    }

    /** SYS_CONTEXT — заглушка контекста сессии. */
    public static Varchar2Var sysContext(Varchar2Var namespace, Varchar2Var attribute) {
        if (isNull(namespace) || isNull(attribute)) return Varchar2Var.nullValue();
        log.debug("SYS_CONTEXT({}, {})", namespace.get(), attribute.get());
        return Varchar2Var.nullValue();
    }

    /** SYS_CONTEXT с length optional. */
    public static Varchar2Var sysContext$1(Varchar2Var namespace, Varchar2Var attribute, Varchar2Var newoptional) {
        return sysContext(namespace, attribute);
    }

    /** SYS_EXTRACT_UTC — UTC из timestamp TZ. */
    public static TimestampVar sysExtractUtc(TimestampVar t) {
        if (isNull(t)) return TimestampVar.nullValue();
        return TimestampVar.of(t.get().withOffsetSameInstant(ZoneOffset.UTC));
    }

    /** SYS_GUID — 16 байт UUID. */
    public static RawVar sysGuid() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return RawVar.of(bytes);
    }

    /** SYS_LITERALTODATE — литерал в DATE. */
    public static DateVar sysLiteraltodate(Varchar2Var numerator) {
        if (isNull(numerator)) return DateVar.nullValue();
        try {
            return DateVar.of(parseDateBasic(numerator.get(), "YYYY-MM-DD"));
        } catch (DateTimeParseException e) {
            return DateVar.nullValue();
        }
    }

    /** SYS_LITERALTODSINTERVAL — заглушка. */
    public static DsIntervalVar sysLiteraltodsinterval(Varchar2Var numerator, Varchar2Var units) {
        if (isNull(numerator)) return DsIntervalVar.nullValue();
        return DsIntervalVar.of(Duration.parse(numerator.get()));
    }

    /** SYS_LITERALTOTIMESTAMP — заглушка. */
    public static TimestampVar sysLiteraltotimestamp(Varchar2Var numerator) {
        if (isNull(numerator)) return TimestampVar.nullValue();
        return TimestampVar.of(OffsetDateTime.parse(numerator.get()));
    }

    /** SYS_LITERALTOTZTIMESTAMP — заглушка. */
    public static TimestampVar sysLiteraltotztimestamp(Varchar2Var numerator) {
        return sysLiteraltotimestamp(numerator);
    }

    /** SYS_LITERALTOYMINTERVAL — заглушка. */
    public static YmIntervalVar sysLiteraltoyminterval(Varchar2Var numerator, Varchar2Var units) {
        if (isNull(numerator)) return YmIntervalVar.nullValue();
        return YmIntervalVar.of(Period.parse(numerator.get()));
    }

    /** SYSDATE — текущая дата/время. */
    public static DateVar sysdate() {
        return DateVar.of(LocalDateTime.now());
    }

    /** SYSTIMESTAMP — текущая метка времени с TZ. */
    public static TimestampVar systimestamp() {
        return TimestampVar.of(OffsetDateTime.now());
    }

    /** TAN для BINARY_DOUBLE. */
    public static BinaryDoubleVar tan(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.tan(d.get()));
    }

    /** TAN для NUMBER. */
    public static NumberVar tan$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.tan(n.get().doubleValue())));
    }

    /** TANH для BINARY_DOUBLE. */
    public static BinaryDoubleVar tanh(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Math.tanh(d.get()));
    }

    /** TANH для NUMBER. */
    public static NumberVar tanh$1(NumberVar n) {
        if (isNull(n)) return NumberVar.nullValue();
        return NumberVar.of(BigDecimal.valueOf(Math.tanh(n.get().doubleValue())));
    }

    /** TO_ANYLOB — VARCHAR2 в CLOB. */
    public static ClobVar toAnylob(Varchar2Var right) {
        if (isNull(right)) return ClobVar.nullValue();
        return ClobVar.of(right.get());
    }

    /** TO_BINARY_DOUBLE(VARCHAR2, FORMAT). */
    public static BinaryDoubleVar toBinaryDouble(Varchar2Var left, Varchar2Var format) {
        if (isNull(left)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(Double.parseDouble(left.get()));
    }

    /** TO_BINARY_DOUBLE(VARCHAR2, FORMAT, PARMS). */
    public static BinaryDoubleVar toBinaryDouble$1(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toBinaryDouble(left, format);
    }

    /** TO_BINARY_DOUBLE(BINARY_DOUBLE). */
    public static BinaryDoubleVar toBinaryDouble$2(BinaryDoubleVar right) {
        if (isNull(right)) return BinaryDoubleVar.nullValue();
        return right;
    }

    /** TO_BINARY_FLOAT(VARCHAR2, FORMAT). */
    public static BinaryFloatVar toBinaryFloat(Varchar2Var left, Varchar2Var format) {
        if (isNull(left)) return BinaryFloatVar.nullValue();
        return BinaryFloatVar.of(Float.parseFloat(left.get()));
    }

    /** TO_BINARY_FLOAT(VARCHAR2, FORMAT, PARMS). */
    public static BinaryFloatVar toBinaryFloat$1(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toBinaryFloat(left, format);
    }

    /** TO_BINARY_FLOAT(BINARY_FLOAT). */
    public static BinaryFloatVar toBinaryFloat$2(BinaryFloatVar right) {
        if (isNull(right)) return BinaryFloatVar.nullValue();
        return right;
    }

    /** TO_BLOB — RAW в BLOB. */
    public static BlobVar toBlob(RawVar right) {
        if (isNull(right)) return BlobVar.nullValue();
        return BlobVar.of(right.get());
    }

    /** TO_CHAR(BINARY_DOUBLE, format). */
    public static Varchar2Var toChar(BinaryDoubleVar left, Varchar2Var format) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(String.format(Locale.ROOT, "%." + (isNull(format) ? "15" : "6") + "f", left.get()));
    }

    /** TO_CHAR(BINARY_FLOAT, format). */
    public static Varchar2Var toChar$1(BinaryFloatVar left, Varchar2Var format) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(String.valueOf(left.get()));
    }

    /** TO_CHAR(DATE, format). */
    public static Varchar2Var toChar$2(DateVar left, Varchar2Var right) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(toCharDateBasic(left.get(), isNull(right) ? null : right.get()));
    }

    /** TO_CHAR(DATE). */
    public static Varchar2Var toChar$3(DateVar left) {
        return toChar$2(left, Varchar2Var.nullValue());
    }

    /** TO_CHAR(NUMBER, format). */
    public static Varchar2Var toChar$4(NumberVar left, Varchar2Var right) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(toCharNumberBasic(left.get(), isNull(right) ? null : right.get()));
    }

    /** TO_CHAR(NUMBER). */
    public static Varchar2Var toChar$5(NumberVar left) {
        return toChar$4(left, Varchar2Var.nullValue());
    }

    /** TO_CHAR(VARCHAR2). */
    public static Varchar2Var toChar$6(Varchar2Var right) {
        if (isNull(right)) return Varchar2Var.nullValue();
        return right;
    }

    /** TO_CHAR(MLSLABEL, format) — заглушка. */
    public static Varchar2Var toChar$7(MlsLabelVar label, Varchar2Var format) {
        if (isNull(label)) return Varchar2Var.nullValue();
        return Varchar2Var.of(label.get());
    }

    /** TO_CHAR(BINARY_DOUBLE, format, parms). */
    public static Varchar2Var toChar$8(BinaryDoubleVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar(left, format);
    }

    /** TO_CHAR(BINARY_FLOAT, format, parms). */
    public static Varchar2Var toChar$9(BinaryFloatVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$1(left, format);
    }

    /** TO_CHAR(DATE, format, parms). */
    public static Varchar2Var toChar$10(DateVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$2(left, format);
    }

    /** TO_CHAR(DSINTERVAL, format). */
    public static Varchar2Var toChar$11(DsIntervalVar left, Varchar2Var format) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(left.get().toString());
    }

    /** TO_CHAR(DSINTERVAL, format, parms). */
    public static Varchar2Var toChar$12(DsIntervalVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$11(left, format);
    }

    /** TO_CHAR(NUMBER, format, parms). */
    public static Varchar2Var toChar$13(NumberVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$4(left, format);
    }

    /** TO_CHAR(TIMESTAMP_LTZ, format). */
    public static Varchar2Var toChar$14(TimestampVar left, Varchar2Var format) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(left.get().toString());
    }

    /** TO_CHAR(TIMESTAMP_LTZ, format, parms). */
    public static Varchar2Var toChar$15(TimestampVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$14(left, format);
    }

    /** TO_CHAR(TIMESTAMP_TZ, format). */
    public static Varchar2Var toChar$16(TimestampVar left, Varchar2Var format) {
        return toChar$14(left, format);
    }

    /** TO_CHAR(TIMESTAMP_TZ, format, parms). */
    public static Varchar2Var toChar$17(TimestampVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$14(left, format);
    }

    /** TO_CHAR(TIMESTAMP). */
    public static Varchar2Var toChar$18(TimestampVar left) {
        return toChar$14(left, Varchar2Var.nullValue());
    }

    /** TO_CHAR(TIMESTAMP, format). */
    public static Varchar2Var toChar$19(TimestampVar left, Varchar2Var format) {
        return toChar$14(left, format);
    }

    /** TO_CHAR(TIMESTAMP, format, parms). */
    public static Varchar2Var toChar$20(TimestampVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$14(left, format);
    }

    /** TO_CHAR(YMINTERVAL, format). */
    public static Varchar2Var toChar$21(YmIntervalVar left, Varchar2Var format) {
        if (isNull(left)) return Varchar2Var.nullValue();
        return Varchar2Var.of(left.get().toString());
    }

    /** TO_CHAR(YMINTERVAL, format, parms). */
    public static Varchar2Var toChar$22(YmIntervalVar left, Varchar2Var format, Varchar2Var parms) {
        return toChar$21(left, format);
    }

    /** TO_CLOB — VARCHAR2 в CLOB. */
    public static ClobVar toClob(Varchar2Var right) {
        if (isNull(right)) return ClobVar.nullValue();
        return ClobVar.of(right.get());
    }

    /** TO_DATE(VARCHAR2) — ISO yyyy-MM-dd. */
    public static DateVar toDate(Varchar2Var right) {
        if (isNull(right)) return DateVar.nullValue();
        try {
            return DateVar.of(parseDateBasic(right.get(), "YYYY-MM-DD"));
        } catch (DateTimeParseException e) {
            log.warn("TO_DATE parse error: {}", right.get());
            return DateVar.nullValue();
        }
    }

    /** TO_DATE(NUMBER, format) — заглушка Julian. */
    public static DateVar toDate$1(NumberVar left, Varchar2Var right) {
        if (isNull(left)) return DateVar.nullValue();
        return DateVar.of(LocalDate.ofEpochDay(left.get().longValue()).atStartOfDay());
    }

    /** TO_DATE(VARCHAR2, format). */
    public static DateVar toDate$2(Varchar2Var left, Varchar2Var right) {
        if (isNull(left) || isNull(right)) return DateVar.nullValue();
        try {
            return DateVar.of(parseDateBasic(left.get(), right.get()));
        } catch (DateTimeParseException e) {
            return DateVar.nullValue();
        }
    }

    /** TO_DATE(VARCHAR2, format, parms). */
    public static DateVar toDate$3(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toDate$2(left, format);
    }

    /** TO_DSINTERVAL(VARCHAR2). */
    public static DsIntervalVar toDsinterval(Varchar2Var right) {
        if (isNull(right)) return DsIntervalVar.nullValue();
        return DsIntervalVar.of(Duration.parse(right.get()));
    }

    /** TO_DSINTERVAL(VARCHAR2, parms). */
    public static DsIntervalVar toDsinterval$1(Varchar2Var right, Varchar2Var parms) {
        return toDsinterval(right);
    }

    /** TO_MULTI_BYTE — заглушка (возвращает вход). */
    public static Varchar2Var toMultiByte(Varchar2Var c) {
        if (isNull(c)) return Varchar2Var.nullValue();
        return c;
    }

    /** TO_NCHAR(DATE, FORMAT). */
    public static Nvarchar2Var toNchar(DateVar left, Nvarchar2Var format) {
        Varchar2Var v = toChar$2(left, isNull(format) ? Varchar2Var.nullValue() : Varchar2Var.of(format.get()));
        if (isNull(v)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(v.get());
    }

    /** TO_NCHAR(NUMBER, FORMAT). */
    public static Nvarchar2Var toNchar$1(NumberVar left, Nvarchar2Var format) {
        Varchar2Var v = toChar$4(left, isNull(format) ? Varchar2Var.nullValue() : Varchar2Var.of(format.get()));
        if (isNull(v)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(v.get());
    }

    /** TO_NCHAR(BINARY_DOUBLE, format). */
    public static Nvarchar2Var toNchar$2(BinaryDoubleVar left, Nvarchar2Var format) {
        Varchar2Var v = toChar(left, isNull(format) ? Varchar2Var.nullValue() : Varchar2Var.of(format.get()));
        if (isNull(v)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(v.get());
    }

    /** TO_NCHAR(BINARY_DOUBLE, format, parms). */
    public static Nvarchar2Var toNchar$3(BinaryDoubleVar left, Nvarchar2Var format, Nvarchar2Var parms) {
        return toNchar$2(left, format);
    }

    /** TO_NCHAR(BINARY_FLOAT, format). */
    public static Nvarchar2Var toNchar$4(BinaryFloatVar left, Nvarchar2Var format) {
        Varchar2Var v = toChar$1(left, isNull(format) ? Varchar2Var.nullValue() : Varchar2Var.of(format.get()));
        if (isNull(v)) return Nvarchar2Var.nullValue();
        return Nvarchar2Var.of(v.get());
    }

    /** TO_NCHAR(BINARY_FLOAT, format, parms). */
    public static Nvarchar2Var toNchar$5(BinaryFloatVar left, Nvarchar2Var format, Nvarchar2Var parms) {
        return toNchar$4(left, format);
    }

    /** TO_NCHAR(NVARCHAR2). */
    public static Nvarchar2Var toNchar$6(Nvarchar2Var right) {
        if (isNull(right)) return Nvarchar2Var.nullValue();
        return right;
    }

    /** TO_NCLOB — VARCHAR2 в NCLOB. */
    public static NclobVar toNclob(Varchar2Var right) {
        if (isNull(right)) return NclobVar.nullValue();
        return NclobVar.of(right.get());
    }

    /** TO_NUMBER(NUMBER). */
    public static NumberVar toNumber(NumberVar right) {
        if (isNull(right)) return NumberVar.nullValue();
        return right;
    }

    /** TO_NUMBER(VARCHAR2). */
    public static NumberVar toNumber$1(Varchar2Var right) {
        if (isNull(right)) return NumberVar.nullValue();
        try {
            return NumberVar.of(new BigDecimal(right.get().trim()));
        } catch (NumberFormatException e) {
            return NumberVar.nullValue();
        }
    }

    /** TO_NUMBER(VARCHAR2, format, parms). */
    public static NumberVar toNumber$2(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toNumber$1(left);
    }

    /** TO_NUMBER(VARCHAR2, format). */
    public static NumberVar toNumber$3(Varchar2Var left, Varchar2Var format) {
        return toNumber$1(left);
    }

    /** TO_RAW — BLOB в RAW. */
    public static RawVar toRaw(BlobVar right) {
        if (isNull(right)) return RawVar.nullValue();
        return RawVar.of(right.get());
    }

    /** TO_SINGLE_BYTE — заглушка. */
    public static Varchar2Var toSingleByte(Varchar2Var c) {
        if (isNull(c)) return Varchar2Var.nullValue();
        return c;
    }

    /** TO_TIMESTAMP(DATE). */
    public static TimestampVar toTimestamp(DateVar right) {
        if (isNull(right)) return TimestampVar.nullValue();
        return TimestampVar.of(right.get().atOffset(ZoneOffset.systemDefault().getRules().getOffset(right.get())));
    }

    /** TO_TIMESTAMP(TIMESTAMP_TZ). */
    public static TimestampVar toTimestamp$1(TimestampVar right) {
        if (isNull(right)) return TimestampVar.nullValue();
        return TimestampVar.of(right.get());
    }

    /** TO_TIMESTAMP(VARCHAR2). */
    public static TimestampVar toTimestamp$2(Varchar2Var right) {
        if (isNull(right)) return TimestampVar.nullValue();
        return TimestampVar.of(OffsetDateTime.parse(right.get()));
    }

    /** TO_TIMESTAMP(TIMESTAMP_LTZ). */
    public static TimestampVar toTimestamp$3(TimestampVar arg) {
        return toTimestamp$1(arg);
    }

    /** TO_TIMESTAMP(VARCHAR2, format). */
    public static TimestampVar toTimestamp$4(Varchar2Var left, Varchar2Var format) {
        if (isNull(left) || isNull(format)) return TimestampVar.nullValue();
        return TimestampVar.of(LocalDateTime.parse(left.get(), DateTimeFormatter.ofPattern(format.get(), Locale.ENGLISH)).atOffset(ZoneOffset.UTC));
    }

    /** TO_TIMESTAMP(VARCHAR2, format, parms). */
    public static TimestampVar toTimestamp$5(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toTimestamp$4(left, format);
    }

    /** TO_TIMESTAMP_TZ(TIMESTAMP). */
    public static TimestampVar toTimestampTz(TimestampVar right) {
        if (isNull(right)) return TimestampVar.nullValue();
        return right;
    }

    /** TO_TIMESTAMP_TZ(VARCHAR2). */
    public static TimestampVar toTimestampTz$1(Varchar2Var right) {
        if (isNull(right)) return TimestampVar.nullValue();
        return TimestampVar.of(OffsetDateTime.parse(right.get()));
    }

    /** TO_TIMESTAMP_TZ(DATE). */
    public static TimestampVar toTimestampTz$2(DateVar arg) {
        return toTimestamp(arg);
    }

    /** TO_TIMESTAMP_TZ(TIMESTAMP_LTZ). */
    public static TimestampVar toTimestampTz$3(TimestampVar arg) {
        return toTimestampTz(arg);
    }

    /** TO_TIMESTAMP_TZ(VARCHAR2, format). */
    public static TimestampVar toTimestampTz$4(Varchar2Var left, Varchar2Var format) {
        return toTimestamp$4(left, format);
    }

    /** TO_TIMESTAMP_TZ(VARCHAR2, format, parms). */
    public static TimestampVar toTimestampTz$5(Varchar2Var left, Varchar2Var format, Varchar2Var parms) {
        return toTimestamp$4(left, format);
    }

    /** TO_YMINTERVAL(VARCHAR2). */
    public static YmIntervalVar toYminterval(Varchar2Var right) {
        if (isNull(right)) return YmIntervalVar.nullValue();
        return YmIntervalVar.of(Period.parse(right.get()));
    }

    /** TRANSLATE — посимвольная замена. */
    public static Varchar2Var translate(Varchar2Var str1, Varchar2Var src, Varchar2Var dest) {
        if (isNull(str1)) return Varchar2Var.nullValue();
        return Varchar2Var.of(translateInternal(str1.get(), isNull(src) ? "" : src.get(), isNull(dest) ? "" : dest.get()));
    }

    /** TRIM — удаление пробелов с обеих сторон. */
    public static Varchar2Var trim(Varchar2Var v) {
        if (isNull(v)) return Varchar2Var.nullValue();
        return Varchar2Var.of(v.get().trim());
    }

    /** TRIM с операцией BOTH/LEADING/TRAILING. */
    public static Varchar2Var trim$1(Varchar2Var v, Varchar2Var op) {
        if (isNull(v)) return Varchar2Var.nullValue();
        String s = v.get();
        String o = isNull(op) ? "BOTH" : op.get().toUpperCase(Locale.ROOT);
        return switch (o) {
            case "LEADING" -> Varchar2Var.of(trimSet(s, " ", true, false));
            case "TRAILING" -> Varchar2Var.of(trimSet(s, " ", false, true));
            default -> trim(v);
        };
    }

    /** TRIM с символом и операцией. */
    public static Varchar2Var trim$2(Varchar2Var v, Varchar2Var chr, Varchar2Var op) {
        if (isNull(v)) return Varchar2Var.nullValue();
        String s = v.get();
        String c = isNull(chr) ? " " : chr.get();
        String o = isNull(op) ? "BOTH" : op.get().toUpperCase(Locale.ROOT);
        return switch (o) {
            case "LEADING" -> Varchar2Var.of(trimSet(s, c, true, false));
            case "TRAILING" -> Varchar2Var.of(trimSet(s, c, false, true));
            default -> Varchar2Var.of(trimSet(s, c, true, true));
        };
    }

    /** TRUNC для BINARY_DOUBLE. */
    public static BinaryDoubleVar trunc(BinaryDoubleVar d) {
        if (isNull(d)) return BinaryDoubleVar.nullValue();
        return BinaryDoubleVar.of(d.get() >= 0 ? Math.floor(d.get()) : Math.ceil(d.get()));
    }

    /** TRUNC для BINARY_FLOAT. */
    public static BinaryFloatVar trunc$1(BinaryFloatVar f) {
        if (isNull(f)) return BinaryFloatVar.nullValue();
        return BinaryFloatVar.of((float) (f.get() >= 0 ? Math.floor(f.get()) : Math.ceil(f.get())));
    }

    /** TRUNC для NUMBER без places. */
    public static NumberVar trunc$2(NumberVar n) {
        return trunc$3(n, PlsIntegerVar.of(0));
    }

    /** TRUNC для NUMBER с places. */
    public static NumberVar trunc$3(NumberVar n, PlsIntegerVar places) {
        if (isNull(n) || isNull(places)) return NumberVar.nullValue();
        return NumberVar.of(truncNumber(n.get(), places.get()));
    }

    /** TRUNC для DATE — обрезка до дня. */
    public static DateVar trunc$4(DateVar left) {
        if (isNull(left)) return DateVar.nullValue();
        return DateVar.of(left.get().toLocalDate().atStartOfDay());
    }

    /** TRUNC для DATE с форматной маской. */
    public static DateVar trunc$5(DateVar left, Varchar2Var right) {
        if (isNull(left)) return DateVar.nullValue();
        return DateVar.of(truncDateInternal(left.get(), isNull(right) ? null : right.get()));
    }

    /** TZ_OFFSET — заглушка смещения TZ. */
    public static Varchar2Var tzOffset(Varchar2Var region) {
        if (isNull(region)) return Varchar2Var.nullValue();
        try {
            ZoneId zone = ZoneId.of(region.get());
            return Varchar2Var.of(OffsetDateTime.now(zone).getOffset().getId());
        } catch (Exception e) {
            return Varchar2Var.of("+00:00");
        }
    }

    /** UID — заглушка идентификатора пользователя. */
    public static PlsIntegerVar uid() {
        return PlsIntegerVar.of(0);
    }

    /** UPPER — верхний регистр. */
    public static Varchar2Var upper(Varchar2Var ch) {
        if (isNull(ch)) return Varchar2Var.nullValue();
        return Varchar2Var.of(ch.get().toUpperCase(Locale.ROOT));
    }

    /** USER — имя текущего пользователя (заглушка). */
    public static Varchar2Var user() {
        return Varchar2Var.of(System.getProperty("user.name", "SCOTT"));
    }

    /** USERENV — параметры среды сессии (заглушка). */
    public static Varchar2Var userenv(Varchar2Var envstr) {
        if (isNull(envstr)) return Varchar2Var.nullValue();
        String key = envstr.get().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "SESSIONID" -> Varchar2Var.of("1");
            case "SID" -> Varchar2Var.of("1");
            case "TERMINAL" -> Varchar2Var.of("UNKNOWN");
            case "LANGUAGE" -> Varchar2Var.of("AMERICAN");
            case "LANG" -> Varchar2Var.of("AMERICAN");
            default -> Varchar2Var.nullValue();
        };
    }

    /** ASCII — код первого символа. */
    public static PlsIntegerVar ascii(Varchar2Var ch) {
        if (isNull(ch) || ch.get().isEmpty()) return PlsIntegerVar.nullValue();
        return PlsIntegerVar.of((int) ch.get().charAt(0));
    }

    /** ROWID — псевдостолбец ROWID (заглушка). */
    public static RowidVar rowid() {
        return RowidVar.of("AAABBBCCC0000000001");
    }

    /** NVL2 для VARCHAR2. */
    public static Varchar2Var nvl2(Varchar2Var expr, Varchar2Var s1, Varchar2Var s2) {
        if (isNull(expr)) return s2 == null ? Varchar2Var.nullValue() : s2;
        return s1 == null ? Varchar2Var.nullValue() : s1;
    }

    /** EXTRACT из DATE. */
    public static NumberVar extract(Varchar2Var what, DateVar expr) {
        return extractFromDate(what, expr);
    }

    /** EXTRACT из NUMBER (заглушка). */
    public static NumberVar extract$1(Varchar2Var what, NumberVar expr) {
        if (isNull(what) || isNull(expr)) return NumberVar.nullValue();
        return expr;
    }

    /** EXTRACT из TIMESTAMP. */
    public static NumberVar extract$2(Varchar2Var what, TimestampVar expr) {
        return extractFromTimestamp(what, expr);
    }

    /** RAISE_APPLICATION_ERROR — выброс ошибки приложения. */
    public static void raiseApplicationError(NumberVar errorNumber, Varchar2Var errorMsg) {
        String msg = isNull(errorMsg) ? "" : errorMsg.get();
        String code = isNull(errorNumber) ? "20000" : String.valueOf(errorNumber.get().intValue());
        log.error("ORA-{}: {}", code, msg);
        throw new RuntimeException("ORA-" + code + ": " + msg);
    }

    /** RAISE_APPLICATION_ERROR с keep_errors. */
    public static void raiseApplicationError$1(NumberVar errorNumber, Varchar2Var errorMsg, BooleanVar keepErrors) {
        raiseApplicationError(errorNumber, errorMsg);
    }
}
