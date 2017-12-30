lexer grammar DataLexer;

import StringLexerShared;

WS : ( ' ' | '\t' )+ ;

COMMA: ',';

POSITIVE_INTEGER : [0-9]+;
DOT: '.';

NEWLINE : '\r'? '\n' ;

TRUE: 'true';
FALSE: 'false';
OPEN_ROUND : '(';
CLOSE_ROUND : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';
SLASH : '/';
PLUS : '+';
MINUS : '-';
COLON: ':';

UNQUOTED_IDENT : ~[ \t\n\r"()@+\-/*&|=?:;~$!<>\\,[\]]+ {GrammarUtility.validUnquoted(getText())}?;



