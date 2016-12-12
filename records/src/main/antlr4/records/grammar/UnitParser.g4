parser grammar UnitParser;

options { tokenVocab = UnitLexer; }

singleUnit : IDENT;
scale : NUMBER (POWER NUMBER)?;
single : singleUnit (POWER NUMBER)?;
unit : (single | OPEN_BRACKET unit CLOSE_BRACKET) (WS? unit | WS? DIVIDE WS? unit | WS? TIMES WS? unit)?;

aliasDeclaration : ALIAS WS singleUnit WS? EQUALS WS? singleUnit NEWLINE;

display : (PREFIX | SUFFIX) WS? STRING;

unitDeclaration : UNIT WS singleUnit WS (STRING WS?)? display* (EQUALS WS? (scale (WS|(WS? TIMES WS?))?)? unit WS?)? NEWLINE;

declaration : aliasDeclaration | unitDeclaration;

blankLine : WS? NEWLINE;
file : blankLine* (declaration blankLine*)*;