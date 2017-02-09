lexer grammar BasicLexer;

import StringLexerShared;

WS : ( ' ' | '\t' )+ -> skip ;

NEWLINE : '\r'? '\n' ;

ATOM : ~( ' ' | '\t' | '\n' | '\r' | '"' )+ ;
