lexer grammar UnitLexer;

fragment ESCAPED_ESCAPE : '^^';
fragment ESCAPED_QUOTE : '^"';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : ('"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ESCAPED_ESCAPE | ~[\n\r^"] )*? '"')
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1).replace("^\"", "\"").replace("^n", "\n").replace("^r", "\r").replace("^^","^")); };

WS : ( ' ' | '\t' )+;

NEWLINE : '\r'? '\n' ;

NUMBER : [+-]? [0-9] [_0-9]* ('.' [_0-9]* [0-9])? {setText(getText().replace("_", ""));};

POWER : '^';
TIMES: '*';
DIVIDE: '/';
EQUALS : '=';
UNIT : 'UNIT';
OPEN_BRACKET: '(';
CLOSE_BRACKET: ')';
PREFIX : 'PREFIX';
SUFFIX : 'SUFFIX';
ALIAS : 'ALIAS';

IDENT : ~[0-9 \t\r\n^*/@()={}[\]]+;

COMMENT : '//' ~[\r\n]* NEWLINE -> skip;
