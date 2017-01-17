parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

tableId : (STRING | UNQUOTED_IDENT);
columnId : (STRING | UNQUOTED_IDENT);
columnRef : (COLREF tableId)? COLREF columnId;

numericLiteral : NUMBER UNIT?;
stringLiteral : STRING;
booleanLiteral : TRUE | FALSE;

varRef  : UNQUOTED_IDENT;

terminal : columnRef | numericLiteral | stringLiteral | booleanLiteral | varRef;

// Could have units in ops:
//plusMinusExpression :  expression PLUS_MINUS UNIT? expression (PLUS_MINUS expression)*;
//timesExpression :  expression TIMES UNIT? expression (TIMES expression)*;
//divideExpression :  expression DIVIDE UNIT? expression;

plusMinusExpression :  expression (PLUS_MINUS expression)+;
timesExpression :  expression (TIMES expression)+;
divideExpression :  expression DIVIDE expression;
raisedExpression : expression RAISEDTO expression;
equalExpression :  expression EQUALITY expression;
notEqualExpression :  expression NON_EQUALITY expression;
lessThanExpression :  expression (LESS_THAN expression)+;
greaterThanExpression :  expression (GREATER_THAN expression)+;
andExpression :  expression (AND expression)+;
orExpression :  expression (OR expression)+;
compoundExpression : plusMinusExpression | timesExpression | divideExpression | raisedExpression | equalExpression | notEqualExpression | lessThanExpression | greaterThanExpression | andExpression | orExpression;

constructor : rawConstructor rawConstructor;
tagExpression : constructor (CONS expression)?;

functionName : UNQUOTED_IDENT;
callExpression : functionName UNIT* OPEN_BRACKET (topLevelExpression | expression (COMMA expression)+) CLOSE_BRACKET;

tupleExpression : OPEN_BRACKET expression (COMMA expression)+ CLOSE_BRACKET;
arrayExpression : OPEN_SQUARE (expression (COMMA expression)*)? CLOSE_SQUARE;

newVariable : NEWVAR UNQUOTED_IDENT;
constructorName : STRING | UNQUOTED_IDENT;
rawConstructor : CONSTRUCTOR constructorName;
patternMatch : MATCHCONSTRUCTOR constructorName (CONS patternMatch)? | newVariable | expression;
pattern : patternMatch (PATTERNAND expression)*;

matchClause : pattern (DELIM pattern)* MAPSTO expression;
match : expression MATCH matchClause (DELIM matchClause)*;

bracketedCompound : OPEN_BRACKET compoundExpression CLOSE_BRACKET;
bracketedMatch : OPEN_BRACKET match CLOSE_BRACKET;
// tagExpression doesn't need brackets because the constructor means it's identifiable from its left token
expression : bracketedCompound | terminal | bracketedMatch | callExpression | tupleExpression | arrayExpression | tagExpression;
topLevelExpression : compoundExpression | match | expression /* includes terminal */;
