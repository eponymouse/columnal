parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

number : NUMBER STRING DIGITS UNIT;
aType : BOOLEAN | number | TEXT | DATE | tagged;
type : aType | OPEN_BRACKET aType CLOSE_BRACKET;
tagged : TAGGED tagItem+;

tagItem : CONSTRUCTOR (CONS type)?;

columnName : STRING;
column : COLUMN columnName type NEWLINE;