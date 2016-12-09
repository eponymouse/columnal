parser grammar FormatParser;

options { tokenVocab = FormatLexer; }

number : NUMBER DIGITS UNIT;
type : BOOLEAN | number | TEXT | DATE | tagged;
tagged : TAGGED OPEN_BRACKET tagItem+ CLOSE_BRACKET;

constructor : UNQUOTED_CONSTRUCTOR | QUOTED_CONSTRUCTOR;
tagItem : constructor (CONS type)?;

columnName : STRING;
column : COLUMN columnName type NEWLINE;