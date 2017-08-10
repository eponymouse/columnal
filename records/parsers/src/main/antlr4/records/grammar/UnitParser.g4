parser grammar UnitParser;

options { tokenVocab = UnitLexer; }

singleUnit : IDENT;
scale : NUMBER (POWER NUMBER)?;
single : singleUnit (POWER NUMBER)?;
divideBy : (WS? DIVIDE WS? unit);
timesBy : (WS? unit | WS? TIMES WS? unit);
unbracketedUnit : unit (divideBy | timesBy+)?;
unit : single | OPEN_BRACKET unbracketedUnit CLOSE_BRACKET;

unitUse : unbracketedUnit EOF;

aliasDeclaration : ALIAS WS singleUnit WS? EQUALS WS? singleUnit NEWLINE;

display : (PREFIX | SUFFIX) WS? STRING;

unitDeclaration : UNIT WS singleUnit WS STRING WS? display* (EQUALS WS? (scale (WS|(WS? TIMES WS?))?)? unit WS?)? NEWLINE;

declaration : aliasDeclaration | unitDeclaration;

blankLine : WS? NEWLINE;
file : blankLine* (declaration blankLine*)*;