parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

decimalPlaces : DIGITS (DASH DIGITS)? (SPACE_KWD | ZERO_KWD);
number : NUMBER decimalPlaces? UNIT?;
date : YEARMONTHDAY | YEARMONTH | TIMEOFDAY | TIMEOFDAYZONED | DATETIME | DATETIMEZONED;
tuple : OPEN_BRACKET type (COMMA type)+ (COMMA TUPLE_MORE)? CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
functionType : OPEN_BRACKET tuple ARROW type CLOSE_BRACKET;
type : unbracketedType | bracketedType;
unbracketedType : BOOLEAN | number | TEXT | date | applyRef | array | ident | TYPEVAR ident; 
taggedDecl : TAGGED tagDeclParam* OPEN_BRACKET tagItem (TAGOR tagItem)* CLOSE_BRACKET;
tagDeclParam : TYPEVAR ident | UNITVAR ident;
bracketedType : (OPEN_BRACKET type CLOSE_BRACKET) | tuple | functionType;
tagRefParam : bracketedType | UNIT | OPEN_BRACKET UNIT CLOSE_BRACKET | OPEN_BRACKET UNITVAR ident CLOSE_BRACKET;
applyRef : APPLY ident tagRefParam+; // First ident is name, rest are type params

// Type names are not valid as idents
ident : UNQUOTED_NAME;
tagItem : ident (OPEN_BRACKET type (COMMA type)* CLOSE_BRACKET)?;

defaultValue: DEFAULT VALUE VALUE_END;

// Type names are fine as a column name:
columnName : ident | NUMBER | BOOLEAN | TEXT | date;
// The defaultValue contains the NEWLINE if that option is picked:
column : COLUMN columnName TYPE type (defaultValue | NEWLINE);

typeName : ident;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;

completeType : type EOF;

// Version for editing.  Number is treated as terminal here because UNIT is self-contained:
typeExpressionTerminal : number | date | BOOLEAN | TEXT | ident | UNIT | INCOMPLETE STRING;
applyTypeExpression : APPLY ident roundTypeExpression+;
arrayTypeExpression : OPEN_SQUARE typeExpression CLOSE_SQUARE;
roundTypeExpression : OPEN_BRACKET typeExpression (ARROW typeExpression | (COMMA typeExpression)+)? CLOSE_BRACKET;
typeExpression : typeExpressionTerminal | arrayTypeExpression | roundTypeExpression | invalidOpsTypeExpression | applyTypeExpression;
invalidOpsTypeExpression : INVALIDOPS OPEN_BRACKET (STRING | typeExpression)* CLOSE_BRACKET;

completeTypeExpression : typeExpression EOF;