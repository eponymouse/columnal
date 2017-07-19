parser grammar DataParser;

options { tokenVocab = DataLexer; }

number : WS? (PLUS | MINUS)? POSITIVE_INTEGER (DOT POSITIVE_INTEGER)?;
bool : WS? (TRUE | FALSE);
string : WS? STRING; // Includes text and dates
ymd: WS? POSITIVE_INTEGER SLASH POSITIVE_INTEGER SLASH POSITIVE_INTEGER;
ym: WS? POSITIVE_INTEGER SLASH POSITIVE_INTEGER;
localDateTime: WS? ymd WS localTime;
localTime: WS? POSITIVE_INTEGER COLON POSITIVE_INTEGER COLON POSITIVE_INTEGER (DOT POSITIVE_INTEGER)?;
offset: (PLUS | MINUS) POSITIVE_INTEGER COLON POSITIVE_INTEGER;
offsetTime: localTime offset;
zone : UNQUOTED_IDENT (SLASH UNQUOTED_IDENT ((PLUS | MINUS) (POSITIVE_INTEGER | UNQUOTED_IDENT))?)?;
zonedDateTime: localDateTime WS? (offset | zone);
// Important to have longest first:
dateOrTime: zonedDateTime | localDateTime | ymd | ym | offsetTime | localTime;
tag : WS? UNQUOTED_IDENT WS?;
openRound : WS? OPEN_ROUND WS?;
closeRound : WS? CLOSE_ROUND WS?;
openSquare : WS? OPEN_SQUARE WS?;
closeSquare : WS? CLOSE_SQUARE WS?;
comma: WS? COMMA WS?;
// Important that datetime comes before numbers:
//unbracketedItem : dateOrTime | number | bool | string | tagged | array;
//bracketedItem : (OPEN_ROUND WS? unbracketedItem WS? CLOSE_ROUND) | tuple;
//item : dateOrTime | number | bool | string | tagged | array | tuple;

//blank : WS NEWLINE;
//row : WS? item (WS item)* WS? NEWLINE;
endRow : WS? NEWLINE;
startRow : WS?;

//data : (blank | row)*;