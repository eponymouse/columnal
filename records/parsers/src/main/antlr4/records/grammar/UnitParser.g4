parser grammar UnitParser;

options { tokenVocab = UnitLexer; }

singleUnit : IDENT | UNITVAR WS IDENT;
single : singleUnit (POWER NUMBER)? | ({_input.LT(1).getText().equals("1")}? NUMBER);
divideBy : (WS? DIVIDE WS? unit);
timesBy : WS? TIMES WS? unit;
unbracketedUnit : unit (divideBy | timesBy+)?;
unit : single | OPEN_BRACKET unbracketedUnit CLOSE_BRACKET;

unitUse : unbracketedUnit EOF;

aliasDeclaration : ALIAS WS singleUnit WS? EQUALS WS? singleUnit NEWLINE;

display : (PREFIX | SUFFIX) WS? STRING;

// n ^ m
scalePower : NUMBER (WS? POWER WS? NUMBER)?;
// scalePower with optional divide by scalePower 
scale : scalePower (WS? DIVIDE WS? scalePower)?;

fullScale: scale EOF;

unitDeclaration : UNIT WS singleUnit WS STRING WS? display* (EQUALS WS? (scale WS? TIMES WS?)? unbracketedUnit WS?)? NEWLINE;

declaration : aliasDeclaration | unitDeclaration;

blankLine : WS? NEWLINE;
file : blankLine* (declaration blankLine*)*;
