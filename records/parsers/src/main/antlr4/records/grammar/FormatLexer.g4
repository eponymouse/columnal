lexer grammar FormatLexer;

import StringLexerShared;

DIGITS : [0-9]+;

UNIT : '{' ~'}'* '}';

BOOLEAN : 'Boolean';
NUMBER : 'Number';
TEXT : 'Text';
YEARMONTHDAY : 'Date';
YEARMONTH : 'DateYM';
TIMEOFDAY : 'Time';
DATETIME : 'DateTime';
DATETIMEZONED : 'DateTimeZoned';
TAGGED : '@tagged';
TYPE : '@TYPE';
TYPEVAR : '@typevar';
UNITVAR : '@unitvar';
SPACE_KWD : 'SPACE';
ZERO_KWD : 'ZERO';
DEFAULT : '@DEFAULT' -> pushMode(VALUE_MODE);
APPLY: '@apply';

WS : ( ' ' | '\t' )+ -> skip;

COLUMN : '@COLUMN';

TUPLE_MORE : '_';
OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
COMMA: ',';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';
DASH: '-';
ARROW: '->';
TAGOR: '|';


INCOMPLETE : '@INCOMPLETE';
INVALIDOPS : '@invalidtypeops';


NEWLINE : '\r'? '\n' ;

// Needs to be last to favour built-in type names:
// Important that this is the same as in ExpressionLexer
// (I think because we use @ExpressionIdentifier as a shorthand
// for type identifiers in our code):
UNQUOTED_NAME : [\p{Alpha}\p{General_Category=Other_Letter}] (('_' | ' ')? [\p{Alpha}\p{Digit}\p{General_Category=Other_Letter}])*;

mode VALUE_MODE;
VALUE_END: NEWLINE -> popMode;
VALUE: (~[\n\r])+;
