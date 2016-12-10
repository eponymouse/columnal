parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

number : NUMBER DIGITS UNIT;
type : BOOLEAN | number | TEXT | DATE | tagRef;
taggedDecl : TAGGED OPEN_BRACKET tagItem+ CLOSE_BRACKET;
tagRef : TAGGED STRING;

constructor : UNQUOTED_CONSTRUCTOR | QUOTED_CONSTRUCTOR;
tagItem : constructor (CONS type)?;

columnName : STRING;
column : COLUMN columnName type NEWLINE;