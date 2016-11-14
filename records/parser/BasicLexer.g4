lexer grammar BasicLexer;

fragment ESCAPED_ESCAPE : '^^';
fragment ESCAPED_QUOTE : '^"';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : '"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ~('\n'|'\r'|'^') )*? '"';

WS : ( ' ' | '\t' )+ -> skip ;

NEWLINE : '\r'? '\n' ;

ATOM : ~( ' ' | '\t' | '\n' | '\r' | '"' )+ ;
