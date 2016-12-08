parser grammar UnitParser;

options { tokenVocab = UnitLexer; }

singleUnit : IDENT;
scale : NUMBER;
singleOrScale : singleUnit | scale;
unit : singleOrScale (WS unit | WS? DIVIDE WS? unit | WS? TIMES WS? unit)? | OPEN_BRACKET unit CLOSE_BRACKET;

unitDeclaration : UNIT WS singleUnit WS (STRING WS?)? (EQUALS WS? unit WS?)? NEWLINE;
