parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

tableId : (STRING | UNQUOTED_IDENT);
columnId : (STRING | UNQUOTED_IDENT);
columnRef : tableId? COLREF columnId;

numericLiteral : NUMBER;
stringLiteral : STRING;
booleanLiteral : TRUE | FALSE;

terminal : columnRef | numericLiteral | stringLiteral | booleanLiteral;

plusMinusExpression :  expression (PLUS_MINUS expression)+;
timesExpression :  expression (TIMES expression)+;
divideExpression :  expression DIVIDE expression;
equalExpression :  expression EQUALITY expression;
notEqualExpression :  expression NON_EQUALITY expression;
lessThanExpression :  expression (LESS_THAN expression)+;
greaterThanExpression :  expression (GREATER_THAN expression)+;
andExpression :  expression (AND expression)+;
orExpression :  expression (OR expression)+;
compoundExpression : plusMinusExpression | timesExpression | divideExpression | equalExpression | notEqualExpression | lessThanExpression | greaterThanExpression | andExpression | orExpression;

variable : NEWVAR (STRING | UNQUOTED_IDENT);
constructor : STRING | UNQUOTED_IDENT;
patternMatch : constructor (CONS patternMatch)? | variable | expression;
pattern : patternMatch (AND expression)*;

matchClause : pattern (DELIM pattern)* MAPSTO expression;
match : expression MATCH matchClause (DELIM matchClause)*;

bracketedCompound : OPEN_BRACKET compoundExpression CLOSE_BRACKET;
bracketedMatch : OPEN_BRACKET match CLOSE_BRACKET;
expression : bracketedCompound | terminal | bracketedMatch;
topLevelExpression : compoundExpression | match | expression /* includes terminal */;
