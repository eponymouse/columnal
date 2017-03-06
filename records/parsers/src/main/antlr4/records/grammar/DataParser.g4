parser grammar DataParser;

options { tokenVocab = DataLexer; }

number : NUMBER;
bool : TRUE | FALSE;
string : STRING; // Includes text and dates
tag : STRING | UNQUOTED_IDENT;
tagged : CONSTRUCTOR tag (CONS WS? item)?;
tuple : OPEN_ROUND WS? item (WS? COMMA WS? item)+ WS? CLOSE_ROUND;
array : OPEN_SQUARE WS? (item (WS? COMMA WS? item)*)? WS? CLOSE_SQUARE;
item : number | bool | string | tagged | tuple | array;

blank : WS NEWLINE;
row : WS? item (WS item)* WS? NEWLINE;

data : (blank | row)*;