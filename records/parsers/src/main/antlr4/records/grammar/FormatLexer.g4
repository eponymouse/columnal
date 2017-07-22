lexer grammar FormatLexer;

import StringLexerShared;

DIGITS : [0-9]+;

UNIT : '{' ~'}'* '}';

BOOLEAN : 'BOOLEAN';
NUMBER : 'NUMBER';
TEXT : 'TEXT';
YEARMONTHDAY : 'YEARMONTHDAY';
YEARMONTH : 'YEARMONTH';
TIMEOFDAY : 'TIMEOFDAY';
TIMEOFDAYZONED : 'TIMEOFDAYZONED';
DATETIME : 'DATETIME';
DATETIMEZONED : 'DATETIMEZONED';
TAGGED : 'TAGGED';
TYPE : 'TYPE';
SPACE_KWD : 'SPACE';
ZERO_KWD : 'ZERO';
DEFAULT : 'DEFAULT' -> pushMode(VALUE_MODE);

WS : ( ' ' | '\t' )+ -> skip;

CONS: ':';
QUOTED_CONSTRUCTOR : ('\\' STRING) { String orig = getText(); setText(GrammarUtility.processEscapes(orig.substring(1))); };
UNQUOTED_CONSTRUCTOR : ('\\' ~[ \t\r\n:"]+) { setText(getText().substring(1)); };
OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
COMMA: ',';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';
DASH: '-';

COLUMN : 'COLUMN';

NEWLINE : '\r'? '\n' ;

mode VALUE_MODE;
VALUE_END: NEWLINE -> popMode;
VALUE: (~[\n\r])+;

