lexer grammar FormatLexer;

/* TODO share this with basic lexer? */
fragment ESCAPED_ESCAPE : '^^';
fragment ESCAPED_QUOTE : '^"';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : ('"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ESCAPED_ESCAPE | ~[\n\r^"] )*? '"')
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1).replace("^\"", "\"").replace("^n", "\n").replace("^r", "\r").replace("^^","^")); };

DIGITS : [0-9]+;

UNIT : '{' ~'}'* '}';

BOOLEAN : 'BOOLEAN';
NUMBER : 'NUMBER';
TEXT : 'TEXT';
DATE : 'DATETIME';
TAGGED : 'TAGGED';
TYPE : 'TYPE';

WS : ( ' ' | '\t' )+ -> skip;

CONS: ':';
QUOTED_CONSTRUCTOR : ('\\' '"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ESCAPED_ESCAPE | ~[\n\r^"] )*? '"')
                            { String orig = getText(); setText(orig.substring(2, orig.length() - 1).replace("^\"", "\"").replace("^n", "\n").replace("^r", "\r").replace("^^","^")); };
UNQUOTED_CONSTRUCTOR : ('\\' ~[ \t\r\n:"]+) { setText(getText().substring(1)); };
OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';

COLUMN : 'COLUMN';

NEWLINE : '\r'? '\n' ;

