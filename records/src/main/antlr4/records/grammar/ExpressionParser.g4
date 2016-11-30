parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

tableId : (STRING | UNQUOTED_IDENT);
columnId : (STRING | UNQUOTED_IDENT);
columnRef : tableId? COLREF columnId;

numericLiteral : NUMBER;
stringLiteral : STRING;

terminal : columnRef | numericLiteral | stringLiteral;

plusMinusExpression : OPEN_BRACKET expression (PLUS_MINUS expression)+ CLOSE_BRACKET;
timesExpression : OPEN_BRACKET expression (TIMES expression)+ CLOSE_BRACKET;
divideExpression : OPEN_BRACKET expression DIVIDE expression CLOSE_BRACKET;
equalExpression : OPEN_BRACKET expression EQUALITY expression CLOSE_BRACKET;
notEqualExpression : OPEN_BRACKET expression NON_EQUALITY expression CLOSE_BRACKET;
lessThanExpression : OPEN_BRACKET expression (LESS_THAN expression)+ CLOSE_BRACKET;
greaterThanExpression : OPEN_BRACKET expression (GREATER_THAN expression)+ CLOSE_BRACKET;
andExpression : OPEN_BRACKET expression (AND expression)+ CLOSE_BRACKET;
orExpression : OPEN_BRACKET expression (OR expression)+ CLOSE_BRACKET;
compoundExpression : plusMinusExpression | timesExpression | divideExpression | equalExpression | notEqualExpression | lessThanExpression | greaterThanExpression | andExpression | orExpression;

variable : NEWVAR UNQUOTED_IDENT;
constructor : STRING | UNQUOTED_IDENT;
patternMatch : constructor (CONS patternMatch)? | variable | expression;
pattern : patternMatch (AND expression)*;

matchClause : pattern (DELIM pattern)* MAPSTO expression;
match : OPEN_BRACKET expression MATCH matchClause (DELIM matchClause)* CLOSE_BRACKET;

expression : compoundExpression | terminal | match;