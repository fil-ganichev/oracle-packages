CREATE OR REPLACE PACKAGE utl_encode IS
  complete         CONSTANT PLS_INTEGER := 1; 
  header_piece     CONSTANT PLS_INTEGER := 2; 
  middle_piece     CONSTANT PLS_INTEGER := 3; 
  end_piece        CONSTANT PLS_INTEGER := 4; 
  base64           CONSTANT PLS_INTEGER := 1;
  quoted_printable CONSTANT PLS_INTEGER := 2;
  function base64_encode(r in raw) return raw;
  function base64_decode(r in raw) return raw;
  function uuencode(r          in raw,
                    type       in pls_integer default complete,
                    filename   in varchar2 default 'uuencode.txt',
                    permission in varchar2 default '0') return raw;
  function uudecode(r in raw) return raw;
  function quoted_printable_encode(r in raw) return raw;
  function quoted_printable_decode(r in raw) return raw;
  function text_encode(buf            in varchar2,
                       encode_charset in varchar2 default null,
                       encoding       in pls_integer default null)
  return varchar2;
  function text_decode(buf            in varchar2,
                       encode_charset in varchar2 default null,
                       encoding       in pls_integer default null)
  return varchar2;
  function mimeheader_encode(buf in varchar2,
                             encode_charset in varchar2 default null,
                             encoding       in pls_integer default null)
  return varchar2;
  function mimeheader_decode(buf in varchar2)
  return varchar2;
END UTL_ENCODE;
