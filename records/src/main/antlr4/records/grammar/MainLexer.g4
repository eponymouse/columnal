lexer grammar MainLexer;

import BasicLexer;

DATA : 'DATA';
TEXTFILE : 'TEXTFILE';
LINKED : 'LINKED';
END : '@END';
BEGIN : '@BEGIN' '\r'? '\n' -> pushMode(DETAIL);
TRANSFORMATION: 'TRANSFORMATION';
SOURCE: 'SOURCE';
FORMAT : 'FORMAT';
POSITION : 'POSITION';
TEXT : 'TEXT';
BLANK : 'BLANK';
DATE : 'DATE';
// Should include all except END:
//KEYWORD : DATA | TEXTFILE | LINKED | BEGIN | TRANSFORMATION | FORMAT | POSITION | TEXT | BLANK | DATE;

// TODO a data item can be e.g. Foo:"Hello" for storing tagged data, as long as there's no space
//ITEM : (KEYWORD | ATOM | STRING) ;

//LINE : WS? ITEM (WS ITEM)+ WS? NEWLINE;

//BLANKLINE: WS? NEWLINE;

//FILE: (LINE | BLANKLINE)+;

mode DETAIL;
DETAIL_END: '@END' -> popMode;
DETAIL_LINE: ~[\n\r@] (~[\n\r])* '\r'? '\n';
