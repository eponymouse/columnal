parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

number : NUMBER DIGITS UNIT;
tuple : OPEN_BRACKET type (COMMA type)+ CLOSE_BRACKET;
array : OPEN_SQUARE type CLOSE_SQUARE;
type : BOOLEAN | number | TEXT | DATE | tagRef | tuple | array;
taggedDecl : TAGGED OPEN_BRACKET tagItem+ CLOSE_BRACKET;
tagRef : TAGGED STRING;

constructor : UNQUOTED_CONSTRUCTOR | QUOTED_CONSTRUCTOR;
tagItem : constructor (CONS type)?;

columnName : STRING;
column : COLUMN columnName type NEWLINE;

typeName : STRING;
typeDecl : TYPE typeName taggedDecl NEWLINE;
typeDecls : NEWLINE* (typeDecl NEWLINE*)*;