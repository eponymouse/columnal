parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

decimalPlaces : DIGITS (DASH DIGITS)? (SPACE_KWD | ZERO_KWD);
number : NUMBER decimalPlaces? UNIT;
date : YEARMONTHDAY | YEARMONTH | TIMEOFDAY | TIMEOFDAYZONED | DATETIME | DATETIMEZONED;
tuple : OPEN_BRACKET type (COMMA type)+ CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
type : BOOLEAN | number | TEXT | date | tagRef | tuple | array;
taggedDecl : TAGGED OPEN_BRACKET tagItem+ CLOSE_BRACKET;
tagRef : TAGGED STRING;

constructor : UNQUOTED_CONSTRUCTOR | QUOTED_CONSTRUCTOR;
tagItem : constructor (CONS type)?;

columnName : STRING;
column : COLUMN columnName type NEWLINE;

typeName : STRING;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;