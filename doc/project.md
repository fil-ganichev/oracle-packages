# Oracle Packages in Java — описание проекта

## Цель

Рабочий Gradle-проект на **Java 19**, эмулирующий функционал трёх встроенных пакетов Oracle:

| Пакет Oracle | Java-класс | SQL-спецификация |
|--------------|------------|------------------|
| `SYS.STANDARD` | `oracle.packages.Standard` | `sql/standard.sql` |
| `UTL_RAW` | `oracle.packages.UtlRaw` | `sql/utl_raw.sql` |
| `UTL_ENCODE` | `oracle.packages.UtlEncode` | `sql/utl_encode.sql` |

Спецификации лежат в `sql/`. Логика функций сверена с официальной документацией Oracle (побитовые операции, Base64/UUEncode/Quoted-Printable, стандартные SQL/PLSQL-функции).

## Стек и окружение

- **Язык:** Java 19 (`C:\Program Files\jdk-19`)
- **Сборка:** Gradle 8.5 (wrapper: `gradlew` / `gradlew.bat`)
- **Тесты:** JUnit 5, AssertJ, Mockito
- **Логирование:** SLF4J + Logback

```bash
.\gradlew.bat build
```

## Структура проекта

```text
oracle-packages/
├── build.gradle
├── settings.gradle
├── doc/                    # ТЗ и описание проекта
├── sql/                    # Исходные .sql спецификации
└── src/
    ├── main/
    │   ├── java/oracle/packages/
    │   └── resources/logback.xml
    └── test/
        └── java/oracle/packages/
```

## Архитектура

### 1. Система типов (обёртки `Var`)

Базовый интерфейс `Var<T>` и `AbstractVar<T>` с методами:

- `isNull()` — значение отсутствует (Oracle NULL);
- `get()` — сырое Java-значение;
- `set(...)` — обновить значение.

Наследники: `Varchar2Var`, `NumberVar`, `RawVar`, `BlobVar`, `ClobVar`, `BooleanVar`, `DateVar`, `TimestampVar`, `BinaryDoubleVar`, `BinaryFloatVar`, `PlsIntegerVar`, `RowidVar`, `BfileVar`, `DsIntervalVar`, `YmIntervalVar`, `Nvarchar2Var`, `NclobVar`, `MlsLabelVar`.

### 2. Именование и сигнатуры

- **DEFAULT-параметры SQL** → `Optional<T>` (где `T` — соответствующий `Var`).
- **Перегрузки запрещены.** Одинаковые имена в SQL именуются:
  - `someMethod` — первая версия;
  - `someMethod$1`, `someMethod$2`, … — следующие, в порядке объявления в SQL.
- **Идентификаторы кода** (имена методов, классов, переменных) — **латиница**.
- **Комментарии и Javadoc** — на кириллице.

### 3. Статистика реализации по пакетам

Учитываются только публичные статические **методы** (константы пакетов в счёт не входят).  
Классификация:

- **полностью** — рабочая логика, соответствующая Oracle для типичных сценариев;
- **заглушка** — фиксированный / фиктивный результат без реальной семантики Oracle;
- **частично** — identity, делегирование в упрощённый аналог, игнор format/parms, урезанный `DECODE` и т.п.

| Пакет | Всего методов | Полностью | Заглушки | Частично |
|-------|---------------|-----------|----------|----------|
| `Standard` | **290** | **188** (64,8 %) | **22** (7,6 %) | **80** (27,6 %) |
| `UtlRaw` | **40** | **37** (92,5 %) | **0** | **3** (7,5 %) |
| `UtlEncode` | **10** | **10** (100 %) | **0** | **0** |
| **Итого** | **340** | **235** (69,1 %) | **22** (6,5 %) | **83** (24,4 %) |

Детальный перечень заглушек и частичных методов — в §5. Проект компилируется и проходит тесты. Межпакетные зависимости в тестах изолируются Mockito.

### 4. Почему многие методы неполные

Неполнота — не одна причина. Ниже типовые ситуации, в том числе там, где Oracle-контекст сессии ни при чём.

#### 4.1. Привязка к сессии / среде Oracle

`USER`, `UID`, `SYS_CONTEXT`, `USERENV`, `DBTIMEZONE`, `SESSIONTIMEZONE`, `SQLCODE`/`SQLERRM`, `LEVEL`, `ROWNUM`, `ROWID` читают состояние СУБД или SQL-курсора. Вне живой сессии Oracle у них нет однозначного смысла → фиксированные значения или `NULL`.

#### 4.2. Сигнатура в `sql/*.sql` уже урезана относительно «настоящего» Oracle

Исходные спецификации — не полная документация Oracle, а **поверхность для транспайлера/эмуляции**. По ним нельзя восстановить полную семантику:

| Пример | Что в SQL-спецификации | Что в реальном Oracle | Следствие в Java |
|--------|------------------------|-----------------------|------------------|
| `coalesce()` | `function COALESCE return VARCHAR2;` — **без параметров** | `COALESCE(expr1, expr2, …)` — произвольное число аргументов | Вызвать «настоящий» COALESCE не из чего; метод нужен для совместимости сигнатур → `NULL` |
| `greatest` / `least` | один формальный параметр `pattern` | сравнение **нескольких** выражений (особая SQL-грамматика) | Один аргумент → identity (вернуть его же) |
| `decode` … | фиксированные тройки `(expr, pat, res)` | цепочка пар + необязательный default | Реализована только одна пара без default |

`coalesce` — не «забыли дописать логику», а **в спецификации нет аргументов**, из которых можно что-то выбрать.

#### 4.3. Параметр есть, но семантика — целая подсистема Oracle (NLS / format models)

Перегрузки с `format` / `parms` (`TO_CHAR`, `TO_NUMBER`, `TO_DATE`, `TO_DSINTERVAL$1`, `TO_NCHAR`, `NLS_*`) требуют парсера Oracle format models, NLS-параметров (`NLS_NUMERIC_CHARACTERS`, …) и таблиц charset/collation. Это сопоставимо с мини-движком NLS.

Поэтому базовый путь без маски сделан рабочим, а `format`/`parms` часто **игнорируются**.

**`toDsinterval$1(right, parms)`** — типичный пример: второй аргумент в Oracle — NLS-параметры распознавания литерала. Без NLS-парсера метод сводится к `toDsinterval(right)`. Даже одноаргументный `toDsinterval` сейчас использует `Duration.parse` (ISO-8601), а не Oracle-литералы вида `'100 10:20:30.123'` — это пробел формата строки, а не «контекст сессии».

#### 4.4. Алгоритм известен, но Oracle-формула тонкая

**`monthsBetween`** реализован, но упрощённо: целые месяцы + `(day1 − day2) / 31`. Полный Oracle `MONTHS_BETWEEN` дополнительно учитывает последние дни месяцев (тогда результат целый), вклад времени суток и краевые случаи 28–31. Точная помесячная арифметика Oracle сознательно не воспроизводилась побайтно.

Аналогично: `compose`/`decompose` → Java Unicode NFC/NFD; `castToNumber`/`castFromNumber` в `UtlRaw` → ASCII/big-endian вместо внутреннего формата Oracle NUMBER.

#### 4.5. Коллация / кодовые единицы Unicode

`INSTR2`/`INSTR4`/`INSTRC`, `LENGTH2`/`LENGTH4`/`LENGTHC`, `SUBSTR2`/`SUBSTR4`/`SUBSTRC` различают символы, UTF-16, UCS-4 и collation. Пока нет модели charset/collation, они **делегированы** в обычный character-level `INSTR`/`LENGTH`/`SUBSTR`.

#### 4.6. Приоритет объёма

Пакет `STANDARD` — сотни перегрузок. Порядок реализации на текущем этапе:

1. локальная проверяемая логика (математика, строки, даты, NULL, REGEXP, encode);
2. заглушки session/NLS/псевдостолбцов — чтобы все сигнатуры из `sql/` существовали;
3. частичные делегаты там, где SQL-сигнатура или NLS не позволяют честную реализацию без большого объёма.

Цель — **рабочий, компилируемый, тестируемый** каркас по всем объявлениям из `sql/`, а не побайтовый клон Oracle.

### 5. Перечень заглушек и упрощённых реализаций

#### `Standard` — полные заглушки (22 метода)

| Java-метод | Oracle | Поведение заглушки |
|------------|--------|-------------------|
| `coalesce()` | `COALESCE` без аргументов | всегда `NULL` VARCHAR2 (см. §4.2) |
| `dbtimezone()` | `DBTIMEZONE` | всегда `"+00:00"` |
| `level()` | `LEVEL` | всегда `1` |
| `rownum()` | `ROWNUM` | всегда `1` |
| `rowid()` | псевдостолбец `ROWID` | фиксированная строка `AAABBBCCC0000000001` |
| `sqlcode()` | `SQLCODE` | всегда `0` |
| `sqlerrm` / `sqlerrm$1` | `SQLERRM` | текст-эмуляция `ORA-…`, не реальный стек ошибок сессии |
| `sysContext` / `sysContext$1` | `SYS_CONTEXT` | всегда `NULL` |
| `uid()` | `UID` | всегда `0` |
| `user()` | `USER` | `System.getProperty("user.name")` или `"SCOTT"` |
| `userenv` | `USERENV` | небольшой набор ключей (`SESSIONID`, `SID`, `TERMINAL`, `LANGUAGE`, `LANG`), иначе `NULL` |
| `nullfn` | `NULLFN` | всегда `NULL` RAW |
| `nlsCharsetDeclLen` | `NLS_CHARSET_DECL_LEN` | возвращает `bytecnt` как int |
| `nlsCharsetId` | `NLS_CHARSET_ID` | `hashCode(name) & 0xFFFF` |
| `nlsCharsetName` | `NLS_CHARSET_NAME` | всегда `"AL32UTF8"` |
| `toMultiByte` | `TO_MULTI_BYTE` | возвращает вход без изменений |
| `toSingleByte` | `TO_SINGLE_BYTE` | возвращает вход без изменений |
| `toChar$7` | `TO_CHAR(MLSLABEL, …)` | заглушка MLSLABEL |
| `toDate$1` | `TO_DATE(NUMBER, format)` | число → `LocalDate.ofEpochDay` (не Julian Oracle) |
| `extract$1` | `EXTRACT(…, NUMBER)` | возвращает сам `NUMBER` без разбора |

#### `Standard` — частично реализованные (80 методов)

| Java-метод | Oracle | Поведение |
|------------|--------|-----------|
| `greatest` … `greatest$9` (10) | `GREATEST` | один аргумент → возвращает его (см. §4.2) |
| `least` … `least$9` (10) | `LEAST` | то же |
| `decode` … `decode$8` (9) | `DECODE` | только одна пара `(expr, pat, res)` (см. §4.2) |
| `compose` | `COMPOSE` | Java NFC (§4.4) |
| `decompose` | `DECOMPOSE` | Java NFD / canonical |
| `length2` | `LENGTH2` | делегирует в обычный `LENGTH` (§4.5) |
| `length4` | `LENGTH4` | делегирует в обычный `LENGTH` (не UTF-32) |
| `lengthc` | `LENGTHC` | делегирует в обычный `LENGTH` (без collation) |
| `instr2` | `INSTR2` | делегирует в `INSTR` |
| `instr4` | `INSTR4` | делегирует в `INSTR` |
| `instrc` | `INSTRC` | делегирует в `INSTR` |
| `substr2` / `substr4` / `substrc` (3) | `SUBSTR2/4/C` | как обычный `SUBSTR` |
| `nlsLower` / `nlsLower$1` | `NLS_LOWER` | обычный `toLowerCase`, `parms` игнорируются (§4.3) |
| `nlsUpper` / `nlsUpper$1` | `NLS_UPPER` | обычный `toUpperCase`, `parms` игнорируются |
| `sessiontimezone` | `SESSIONTIMEZONE` | `ZoneId.systemDefault().getId()` |
| `tzOffset` | `TZ_OFFSET` | смещение через JVM `ZoneId`, при ошибке `"+00:00"` |
| `sysAtTimeZone` | `SYS_AT_TIME_ZONE` | смена TZ через `ZoneId`, без полной NLS-семантики |
| `sysLiteraltodsinterval` | `SYS_LITERALTODSINTERVAL` | `Duration.parse` |
| `sysLiteraltotimestamp` / `sysLiteraltotztimestamp` | `SYS_LITERALTOTIMESTAMP*` | `OffsetDateTime.parse` |
| `sysLiteraltoyminterval` | `SYS_LITERALTOYMINTERVAL` | `Period.parse` |
| `toNumber$2` / `toNumber$3` | `TO_NUMBER(…, format[, parms])` | format/parms игнорируются (§4.3) |
| `toDate$3` | `TO_DATE(…, format, parms)` | `parms` игнорируются |
| `toBinaryDouble` / `toBinaryDouble$1` | `TO_BINARY_DOUBLE(…, format[, parms])` | `Double.parseDouble`, format игнорируется |
| `toBinaryFloat` / `toBinaryFloat$1` | `TO_BINARY_FLOAT(…, format[, parms])` | `Float.parseFloat`, format игнорируется |
| `toChar`, `toChar$1`, `$2`, `$4`, `$8`–`$17`, `$19`–`$22` (18) | `TO_CHAR` с масками / parms / interval | format models неполны (§4.3) |
| `toNchar$3` / `toNchar$5` | `TO_NCHAR(…, parms)` | `parms` игнорируются |
| `toDsinterval$1` | `TO_DSINTERVAL(…, parms)` | `parms` игнорируются (§4.3) |
| `monthsBetween` | `MONTHS_BETWEEN` | упрощённая формула (§4.4) |

#### `UtlRaw` — частично реализованные (3 метода)

| Java-метод | Поведение |
|------------|-----------|
| `castToNumber` | не внутренний Oracle NUMBER format: ASCII-число или big-endian `long` (§4.4) |
| `castFromNumber` | ASCII-байты `toPlainString()`, не Oracle internal NUMBER |
| `castToNvarchar2` | интерпретация байт как UTF-16 (не полный NLS Oracle) |

Полных заглушек в `UtlRaw` нет (**0**). Остальные **37** методов реализованы полностью.

#### `UtlEncode`

Полноценных заглушек и частичных реализаций нет: все **10** методов (Base64, UUEncode, Quoted-Printable, text/MIME) реализованы. Отличия от Oracle возможны только на краевых случаях (фрагменты UU `HEADER`/`MIDDLE`/`END`, точное форматирование строк).

## Реализованные пакеты

### `Standard` — 290 методов (188 полностью / 22 заглушки / 80 частично)

Реальная логика (по Oracle), в числе полностью реализованных:

- математика: `abs`, `bitand`, `ceil`/`floor`/`round`/`trunc`, `mod`, `power`, `sqrt`, тригонометрия, `sign`;
- строки: `concat`, `chr`, `initcap`, `instr`/`instr$N`, `length`/`lengthb`, `lpad`/`rpad`, `ltrim`/`rtrim`, `replace`, `substr`/`substrb`, `translate`, `trim`, `ascii`, `hextoraw`, `rawtohex`;
- даты: `addMonths`, `lastDay`, `nextDay`, `sysdate`, trunc дат;
- NULL: `nvl`, `nvl2`, `nullif`, `nanvl`;
- преобразования: `convert`, базовые `toNumber`/`toChar`/`toDate` без сложных масок;
- `REGEXP_*` через Java `Pattern`/`Matcher`;
- `emptyBlob` / `emptyClob`, `sysGuid`, `raiseApplicationError`.

Заглушки и частичные реализации — в §4–§5.

### `UtlRaw` — 40 методов (37 полностью / 0 заглушек / 3 частично)

Конкатенация (до 12 аргументов), `substr`, `translate`/`transliterate`, `overlay`, `copies`, `xrange`, `reverse`, `compare`, `convert`, побитовые `bitAnd`/`bitOr`/`bitXor`/`bitComplement`, cast BINARY_INTEGER/FLOAT/DOUBLE с endian. Частично: `castToNumber`, `castFromNumber`, `castToNvarchar2`.

### `UtlEncode` — 10 методов (10 полностью / 0 заглушек / 0 частично)

`base64Encode`/`base64Decode`, UUEncode/UUDecode (фрагменты COMPLETE / HEADER / MIDDLE / END), Quoted-Printable, `textEncode`/`textDecode`, MIME-заголовки RFC 2047 (`mimeheaderEncode`/`mimeheaderDecode`).

## Тестирование

| Класс | Покрытие |
|-------|----------|
| `VarTest` | обёртки `Var` |
| `StandardTest` | математика, строки, NULL, даты, REGEXP, ошибки |
| `UtlRawTest` | RAW-операции + Mockito |
| `UtlEncodeTest` | Base64, QP, UU, MIME |

В тестах — fluent-AssertJ, успешные и граничные сценарии (`null` / `Optional.empty()`). Имена тестовых методов — на латинице; пояснения групп можно задавать через `@DisplayName`.

## Документы в `doc/`

| Файл | Содержание |
|------|------------|
| `tz.md` | Инструкция/ТЗ для реализации |
| `raw_tz.txt` | Краткая исходная постановка |
| `project.md` | Это описание проекта |
