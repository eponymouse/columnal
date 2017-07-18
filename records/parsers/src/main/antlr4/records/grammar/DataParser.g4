parser grammar DataParser;

options { tokenVocab = DataLexer; }

number : NUMBER;
bool : TRUE | FALSE;
string : STRING; // Includes text and dates
tag : UNQUOTED_IDENT;
tagged : tag WS? (bracketedItem)?;
tuple : OPEN_ROUND WS? item (WS? COMMA WS? item)+ WS? CLOSE_ROUND;
array : OPEN_SQUARE WS? (item (WS? COMMA WS? item)*)? WS? CLOSE_SQUARE;
unbracketedItem : number | bool | string | tagged | array;
bracketedItem : (OPEN_ROUND WS? unbracketedItem WS? CLOSE_ROUND) | tuple;
item : number | bool | string | tagged | array | tuple;

blank : WS NEWLINE;
row : WS? item (WS item)* WS? NEWLINE;

data : (blank | row)*;