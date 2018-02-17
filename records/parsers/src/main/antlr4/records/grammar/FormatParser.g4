parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

decimalPlaces : DIGITS (DASH DIGITS)? (SPACE_KWD | ZERO_KWD);
number : NUMBER decimalPlaces? UNIT?;
date : YEARMONTHDAY | YEARMONTH | TIMEOFDAY | TIMEOFDAYZONED | DATETIME | DATETIMEZONED;
tuple : OPEN_BRACKET type (COMMA type)+ CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
type : BOOLEAN | number | TEXT | date | tagRef | tuple | array | typeVar | AUTOMATIC;
taggedDecl : TAGGED ident* OPEN_BRACKET tagItem (TAGOR tagItem)* CLOSE_BRACKET;
tagRef : TAGGED ident type*; // First ident is name, rest are type params
typeVar : TYPEVAR ident;

ident : UNQUOTED_NAME | STRING;
tagItem : ident (CONS type)?;

defaultValue: DEFAULT VALUE VALUE_END;

columnName : ident;
// The defaultValue contains the NEWLINE if that option is picked:
column : COLUMN columnName type (defaultValue | NEWLINE);

typeName : ident;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;

completeType : type EOF;
completeTypeOrIncomplete : (type | INCOMPLETE STRING) EOF;