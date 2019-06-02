parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

number : NUMBER UNIT?;
date : YEARMONTHDAY | YEARMONTH | TIMEOFDAY | DATETIME | DATETIMEZONED;
record : OPEN_BRACKET columnName COLON type (COMMA columnName COLON type)* (COMMA RECORD_MORE)? CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
functionArgs : OPEN_BRACKET type (COMMA type)* CLOSE_BRACKET;
functionType : functionArgs ARROW type;
type : unbracketedType | bracketedType;
unbracketedType : BOOLEAN | number | TEXT | date | applyRef | array | ident | TYPEVAR ident | functionType; 
taggedDecl : TAGGED tagDeclParam* OPEN_BRACKET tagItem (TAGOR tagItem)* CLOSE_BRACKET;
tagDeclParam : TYPEVAR ident | UNITVAR ident;
bracketedType : (OPEN_BRACKET type CLOSE_BRACKET) | record;
tagRefParam : bracketedType | UNIT | OPEN_BRACKET UNIT CLOSE_BRACKET | OPEN_BRACKET UNITVAR ident CLOSE_BRACKET;
applyRef : APPLY ident tagRefParam+; // First ident is name, rest are type params

// Type names are not valid as idents
ident : UNQUOTED_NAME;
tagItem : ident bracketedType?;

defaultValue: DEFAULT VALUE VALUE_END;

// Type names are fine as a column name:
columnName : ident | NUMBER | BOOLEAN | TEXT | date;
// The defaultValue contains the NEWLINE if that option is picked:
column : COLUMN columnName TYPE type (defaultValue | NEWLINE);

// Type names are fine as a field name:
fieldName : ident | NUMBER | BOOLEAN | TEXT | date | STRING;

typeName : ident;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;

completeType : type EOF;

// Version for editing.  Number is treated as terminal here because UNIT is self-contained:
typeExpressionTerminal : number | date | BOOLEAN | TEXT | ident | UNIT | INCOMPLETE STRING;
applyArgumentExpression : OPEN_BRACKET typeExpression CLOSE_BRACKET | recordTypeExpression;
applyTypeExpression : APPLY ident applyArgumentExpression+;
arrayTypeExpression : OPEN_SQUARE typeExpression CLOSE_SQUARE;
functionTypeExpression : OPEN_BRACKET typeExpression (COMMA typeExpression)* CLOSE_BRACKET ARROW typeExpression; 
recordTypeExpression : OPEN_BRACKET fieldName COLON typeExpression (COMMA fieldName COLON typeExpression)* CLOSE_BRACKET;
typeExpression : typeExpressionTerminal | arrayTypeExpression | recordTypeExpression | functionTypeExpression | invalidOpsTypeExpression | applyTypeExpression;
invalidOpsTypeExpression : INVALIDOPS OPEN_BRACKET (STRING | typeExpression)* CLOSE_BRACKET;

completeTypeExpression : typeExpression EOF;
