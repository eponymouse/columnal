parser grammar DataParser;

options { tokenVocab = DataLexer; }

number : NUMBER;
bool : TRUE | FALSE;
string : STRING; // Includes text and dates
tag : STRING | UNQUOTED_IDENT;
tagged : CONSTRUCTOR tag (CONS WS? item)?;
item : number | bool | string | tagged;

blank : WS NEWLINE;
row : WS? item (WS item)* WS? NEWLINE;

data : (blank | row)*;