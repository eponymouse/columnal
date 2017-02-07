parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

tableId : STRING | UNQUOTED_IDENT;
columnId : STRING | UNQUOTED_IDENT;
columnRefType : COLUMN | WHOLECOLUMN;
columnRef : columnRefType (tableId COLON)? columnId;

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
ifThenElseExpression : IF expression THEN expression ELSE expression;
compoundExpression : plusMinusExpression | timesExpression | divideExpression | raisedExpression | equalExpression | notEqualExpression | lessThanExpression | greaterThanExpression | andExpression | orExpression | ifThenElseExpression;

constructor : CONSTRUCTOR typeName COLON constructorName;
tagExpression : constructor (COLON expression)?;

functionName : UNQUOTED_IDENT;
callExpression : functionName UNIT* OPEN_BRACKET (topLevelExpression | expression (COMMA expression)+) CLOSE_BRACKET;

tupleExpression : OPEN_BRACKET expression (COMMA expression)+ CLOSE_BRACKET;
arrayExpression : OPEN_SQUARE (expression (COMMA expression)*)? CLOSE_SQUARE;

newVariable : PATTERN UNQUOTED_IDENT;
typeName : STRING | UNQUOTED_IDENT;
constructorName : STRING | UNQUOTED_IDENT;
patternTuple : PATTERN tupleExpression;
patternMatch : PATTERN constructor (COLON patternMatch)? | newVariable | expression;
pattern : patternMatch (CASEGUARD expression)?;

/* Single argument, matched once as variable name or tuple pattern */
functionArg : (newVariable | patternTuple) COLON expression;
/* Single argument, matched by multiple cases (equivalent to FUNCTION x : @match x) */
functionCase : matchClause+;
function: FUNCTION (functionArg | functionCase);

matchClause : CASE pattern (ORCASE pattern)* THEN expression;
match : MATCH expression matchClause+;

bracketedCompound : OPEN_BRACKET compoundExpression CLOSE_BRACKET;
bracketedMatch : OPEN_BRACKET match CLOSE_BRACKET;
// tagExpression doesn't need brackets because the constructor means it's identifiable from its left token
expression : bracketedCompound | terminal | bracketedMatch | callExpression | tupleExpression | arrayExpression | tagExpression;
topLevelExpression : compoundExpression | match | expression /* includes terminal */;
