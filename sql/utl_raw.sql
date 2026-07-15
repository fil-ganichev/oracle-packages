CREATE OR REPLACE NONEDITIONABLE PACKAGE utl_raw IS

  big_endian         CONSTANT PLS_INTEGER := 1;
  little_endian      CONSTANT PLS_INTEGER := 2;
  machine_endian     CONSTANT PLS_INTEGER := 3;

  FUNCTION concat(r1  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW,
                  r8  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW,
                  r8  IN RAW,
                  r9  IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW,
                  r8  IN RAW,
                  r9  IN RAW,
                  r10 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW,
                  r8  IN RAW,
                  r9  IN RAW,
                  r10 IN RAW,
                  r11 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION concat(r1  IN RAW,
                  r2  IN RAW,
                  r3  IN RAW,
                  r4  IN RAW,
                  r5  IN RAW,
                  r6  IN RAW,
                  r7  IN RAW,
                  r8  IN RAW,
                  r9  IN RAW,
                  r10 IN RAW,
                  r11 IN RAW,
                  r12 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(concat, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_to_raw(c IN VARCHAR2 CHARACTER SET ANY_CS) RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_to_raw, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_to_varchar2(r IN RAW) RETURN VARCHAR2;
    pragma RESTRICT_REFERENCES(cast_to_varchar2, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_to_nvarchar2(r IN RAW) RETURN NVARCHAR2;
    pragma RESTRICT_REFERENCES(cast_to_nvarchar2, WNDS, RNDS, WNPS, RNPS);

  FUNCTION length(r IN RAW) RETURN NUMBER;
    pragma RESTRICT_REFERENCES(length, WNDS, RNDS, WNPS, RNPS);

  FUNCTION substr(r   IN RAW,
                  pos IN BINARY_INTEGER) RETURN RAW;
    pragma RESTRICT_REFERENCES(substr, WNDS, RNDS, WNPS, RNPS);

  FUNCTION substr(r   IN RAW,
                  pos IN BINARY_INTEGER,
                  len IN BINARY_INTEGER) RETURN RAW;
    pragma RESTRICT_REFERENCES(substr, WNDS, RNDS, WNPS, RNPS);

  FUNCTION translate(r        IN RAW,
                     from_set IN RAW,
                     to_set   IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(translate, WNDS, RNDS, WNPS, RNPS);

  FUNCTION transliterate(r        IN RAW,
                         to_set   IN RAW DEFAULT NULL,
                         from_set IN RAW DEFAULT NULL,
                         pad      IN RAW DEFAULT NULL) RETURN RAW;
    pragma RESTRICT_REFERENCES(transliterate, WNDS, RNDS, WNPS, RNPS);

  FUNCTION overlay(overlay_str IN RAW,
                   target      IN RAW,
                   pos         IN BINARY_INTEGER DEFAULT 1,
                   len         IN BINARY_INTEGER DEFAULT NULL,
                   pad         IN RAW            DEFAULT NULL) RETURN RAW;
    pragma RESTRICT_REFERENCES(overlay, WNDS, RNDS, WNPS, RNPS);

  FUNCTION copies(r IN RAW,
                  n IN NUMBER) RETURN RAW;
    pragma RESTRICT_REFERENCES(copies, WNDS, RNDS, WNPS, RNPS);

  FUNCTION xrange(start_byte IN RAW DEFAULT NULL,
                  end_byte   IN RAW DEFAULT NULL) RETURN RAW;
    pragma RESTRICT_REFERENCES(xrange, WNDS, RNDS, WNPS, RNPS);

  FUNCTION reverse(r IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(reverse, WNDS, RNDS, WNPS, RNPS);

  FUNCTION compare(r1  IN RAW,
                   r2  IN RAW,
                   pad IN RAW DEFAULT NULL)  RETURN NUMBER;
    pragma RESTRICT_REFERENCES(compare, WNDS, RNDS, WNPS, RNPS);

  FUNCTION convert(r            IN RAW,
                   to_charset   IN VARCHAR2,
                   from_charset IN VARCHAR2) RETURN RAW;
    pragma RESTRICT_REFERENCES(convert, WNDS, RNDS, WNPS, RNPS);

  FUNCTION bit_and(r1 IN RAW,
                   r2 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(bit_and, WNDS, RNDS, WNPS, RNPS);

  FUNCTION bit_or(r1 IN RAW,
                  r2 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(bit_or, WNDS, RNDS, WNPS, RNPS);

  FUNCTION bit_xor(r1 IN RAW,
                   r2 IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(bit_xor, WNDS, RNDS, WNPS, RNPS);

  FUNCTION bit_complement(r IN RAW) RETURN RAW;
    pragma RESTRICT_REFERENCES(bit_complement, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_to_number(r IN RAW) RETURN NUMBER;
    pragma RESTRICT_REFERENCES(cast_to_number, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_from_number(n IN NUMBER) RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_from_number, WNDS, RNDS, WNPS, RNPS);


  FUNCTION cast_to_binary_integer(r IN RAW)
                                  RETURN BINARY_INTEGER;
    pragma RESTRICT_REFERENCES(cast_to_binary_integer, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_to_binary_integer(r IN RAW,
                                  endianess IN PLS_INTEGER)
                                  RETURN BINARY_INTEGER;
    pragma RESTRICT_REFERENCES(cast_to_binary_integer, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_from_binary_integer(n         IN BINARY_INTEGER)
                                    RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_from_binary_integer,WNDS,RNDS,WNPS,RNPS);

  FUNCTION cast_from_binary_integer(n         IN BINARY_INTEGER,
                                    endianess IN PLS_INTEGER)
                                    RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_from_binary_integer,WNDS,RNDS,WNPS,RNPS);

  FUNCTION cast_from_binary_float(n         IN BINARY_FLOAT,
                                  endianess IN PLS_INTEGER
                                    DEFAULT 1)
                                  RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_from_binary_float,WNDS,RNDS,WNPS,RNPS);

  FUNCTION cast_to_binary_float(r IN RAW,
                                endianess IN PLS_INTEGER
                                  DEFAULT 1)
                                RETURN BINARY_FLOAT;
    pragma RESTRICT_REFERENCES(cast_to_binary_float, WNDS, RNDS, WNPS, RNPS);

  FUNCTION cast_from_binary_double(n         IN BINARY_DOUBLE,
                                   endianess IN PLS_INTEGER
                                     DEFAULT 1)
                                   RETURN RAW;
    pragma RESTRICT_REFERENCES(cast_from_binary_double,WNDS,RNDS,WNPS,RNPS);

  FUNCTION cast_to_binary_double(r IN RAW,
                                 endianess IN PLS_INTEGER
                                   DEFAULT 1)
                                 RETURN BINARY_DOUBLE;
    pragma RESTRICT_REFERENCES(cast_to_binary_double, WNDS, RNDS, WNPS, RNPS);

END UTL_RAW;