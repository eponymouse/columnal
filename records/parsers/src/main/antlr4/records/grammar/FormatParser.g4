parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

decimalPlaces : DIGITS (DASH DIGITS)? (SPACE_KWD | ZERO_KWD);
number : NUMBER decimalPlaces? UNIT?;
date : YEARMONTHDAY | YEARMONTH | TIMEOFDAY | TIMEOFDAYZONED | DATETIME | DATETIMEZONED;
tuple : OPEN_BRACKET type (COMMA type)+ CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
functionType : OPEN_BRACKET type ARROW type CLOSE_BRACKET;
type : BOOLEAN | number | TEXT | date | tagRef | tuple | array | typeVar | functionType;
taggedDecl : TAGGED ident* OPEN_BRACKET tagItem (TAGOR tagItem)* CLOSE_BRACKET;
tagRef : TAGGED ident (DASH type)*; // First ident is name, rest are type params
typeVar : TYPEVAR ident;

ident : UNQUOTED_NAME | STRING;
tagItem : ident (OPEN_BRACKET type (COMMA type)* CLOSE_BRACKET)?;

defaultValue: DEFAULT VALUE VALUE_END;

columnName : ident;
// The defaultValue contains the NEWLINE if that option is picked:
column : COLUMN columnName type (defaultValue | NEWLINE);

typeName : ident;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;

completeType : type EOF;

// Version for editing.  Number is treated as terminal here because UNIT is self-contained:
typeExpressionTerminal : number | date | BOOLEAN | TEXT | INCOMPLETE STRING;
arrayTypeExpression : OPEN_SQUARE typeExpression CLOSE_SQUARE;
roundTypeExpression : OPEN_BRACKET typeExpression (ARROW typeExpression | (COMMA typeExpression)+)? CLOSE_BRACKET;
typeExpression : typeExpressionTerminal | arrayTypeExpression | roundTypeExpression | invalidOpsTypeExpression;
invalidOpsTypeExpression : INVALIDOPS OPEN_BRACKET typeExpression (STRING typeExpression)+ CLOSE_BRACKET;

completeTypeExpression : typeExpression EOF;