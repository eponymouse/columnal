parser grammar DataParser;

options { tokenVocab = DataLexer; }

number : (PLUS | MINUS)? POSITIVE_INTEGER (DOT POSITIVE_INTEGER)? WS?;
bool : (TRUE | FALSE) WS?;
string : STRING WS?; // Includes text and dates
ymd: POSITIVE_INTEGER MINUS POSITIVE_INTEGER MINUS POSITIVE_INTEGER WS?;
ym: POSITIVE_INTEGER MINUS POSITIVE_INTEGER WS?;
localDateTime: ymd localTime;
localTime: POSITIVE_INTEGER COLON POSITIVE_INTEGER COLON POSITIVE_INTEGER (DOT POSITIVE_INTEGER)? WS?;
// UNQUOTED_IDENT because it can be Z:
offset: (UNQUOTED_IDENT { getCurrentToken().getText().equals("Z"); } | ((PLUS | MINUS) POSITIVE_INTEGER COLON POSITIVE_INTEGER));
offsetTime: localTime offset;
zone : UNQUOTED_IDENT ((SLASH | MINUS) UNQUOTED_IDENT ((PLUS | MINUS) POSITIVE_INTEGER)?)*;
zonedDateTime: localDateTime (offset | zone) WS?;
tag : (UNQUOTED_IDENT | STRING) WS?;
openRound : OPEN_ROUND WS?;
closeRound : CLOSE_ROUND WS?;
openSquare : OPEN_SQUARE WS?;
closeSquare : CLOSE_SQUARE WS?;
comma: COMMA WS?;
// Important that datetime comes before numbers:
//unbracketedItem : dateOrTime | number | bool | string | tagged | array;
//bracketedItem : (OPEN_ROUND WS? unbracketedItem WS? CLOSE_ROUND) | tuple;
//item : dateOrTime | number | bool | string | tagged | array | tuple;

invalidItem : INVALID WS? STRING WS?;

// Invalid is only expected at the outermost level of a data item, not nested:
numberOrInvalid : number | invalidItem;
stringOrInvalid : string | invalidItem;
boolOrInvalid : bool | invalidItem;
ymdOrInvalid : ymd | invalidItem;
ymOrInvalid : ym | invalidItem;
localDateTimeOrInvalid : localDateTime | invalidItem;
localTimeOrInvalid : localTime | invalidItem;
zonedDateTimeOrInvalid : zonedDateTime | invalidItem;
tagOrInvalid : tag | invalidItem;
openRoundOrInvalid : openRound | invalidItem;
openSquareOrInvalid : openSquare | invalidItem;

//blank : WS NEWLINE;
//row : WS? item (WS item)* WS? NEWLINE;
endRow : NEWLINE;
startRow : WS?;
whitespace: WS?;

//data : (blank | row)*;