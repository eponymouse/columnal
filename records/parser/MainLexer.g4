lexer grammar MainLexer;

fragment ESCAPED_ESCAPE : '^^';
fragment ESCAPED_QUOTE : '^"';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : '"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ~('\n'|'\r'|'^') )*? '"';

WS : ( ' ' | '\t' )+ -> skip ;

NEWLINE : '\r'? '\n' ;

WORD_OTHER : ~( ' ' | '\t' | '\n' | '\r' | '"' )+ ;

DATA : 'DATA';
TEXTFILE : 'TEXTFILE';
LINKED : 'LINKED';
END : '@END';
BEGIN : '@BEGIN';
TRANSFORMATION: 'TRANSFORMATION';
FORMAT : 'FORMAT';
POSITION : 'POSITION';
TEXT : 'TEXT';
BLANK : 'BLANK';
DATE : 'DATE';
// Should include all except END:
IDENTIFIER : DATA | TEXTFILE | LINKED | BEGIN | TRANSFORMATION | FORMAT | POSITION | TEXT | BLANK | DATE;

// An item can be e.g. Foo:"Hello" for storing tagged data, as long as there's no space
ITEM : (IDENTIFIER | WORD_OTHER | STRING)+ ;

LINE : WS? ITEM (WS ITEM)+ WS? NEWLINE;

BLANKLINE: WS? NEWLINE;

FILE: (LINE | BLANKLINE)+;

