lexer grammar DataLexer;

import StringLexerShared;

WS : ( ' ' | '\t' )+ ;

CONS: ':';
CONSTRUCTOR : '\\';
COMMA: ',';

NUMBER : [+-]? [0-9]+ ('.' [0-9]+)?;

NEWLINE : '\r'? '\n' ;

TRUE: 'true';
FALSE: 'false';
OPEN_ROUND : '(';
CLOSE_ROUND : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

UNQUOTED_IDENT : ~[ \t\n\r"()@+-/*&|=?:;~$!<>\\,[\]]+ {utility.Utility.validUnquoted(getText())}?;



