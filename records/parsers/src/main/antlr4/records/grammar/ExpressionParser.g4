parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

ident : STRING | UNQUOTED_IDENT;

tableId : ident;
columnId : ident;
columnRefType : COLUMN | WHOLECOLUMN;
columnRef : columnRefType (tableId COLON)? columnId;

numericLiteral : ADD_OR_SUBTRACT? NUMBER UNIT?;
stringLiteral : STRING;
booleanLiteral : TRUE | FALSE;
unfinished : UNFINISHED STRING;

varRef  : ident;

any : ANY;

implicitLambdaParam : IMPLICIT_LAMBDA_PARAM;
typeExpression : TYPE;
unitExpression : UNIT;


// newVariable only valid in pattern matches, but that's done in semantic check, not syntactic:
// Similar,y constructor may need an argument, but that's sorted out in type checking.
terminal : columnRef | numericLiteral | stringLiteral | booleanLiteral | varRef | newVariable | any | implicitLambdaParam | constructor | standardFunction | typeExpression | unitExpression | unfinished;

// Could have units in ops:
//plusMinusExpression :  expression PLUS_MINUS UNIT? expression (PLUS_MINUS expression)*;
//timesExpression :  expression TIMES UNIT? expression (TIMES expression)*;
//divideExpression :  expression DIVIDE UNIT? expression;

addSubtractExpression :  expression (ADD_OR_SUBTRACT expression)+;
timesExpression :  expression (TIMES expression)+;
divideExpression :  expression DIVIDE expression;
raisedExpression : expression RAISEDTO expression;
equalExpression :  expression (EQUALITY expression)+;
notEqualExpression :  expression NON_EQUALITY expression;
lessThanExpression :  expression (LESS_THAN expression)+;
greaterThanExpression :  expression (GREATER_THAN expression)+;
andExpression :  expression (AND expression)+;
orExpression :  expression (OR expression)+;
matchesExpression : expression MATCHES expression;
ifThenElseExpression : IF expression THEN expression ELSE expression ENDIF;
plusMinusPattern : expression PLUS_MINUS expression;
anyOperator : ADD_OR_SUBTRACT | TIMES | DIVIDE | RAISEDTO | EQUALITY | NON_EQUALITY | LESS_THAN | GREATER_THAN | AND | OR | MATCHES | PLUS_MINUS | COMMA;
invalidOpExpression : INVALIDOPS expression (STRING expression)+;
stringConcatExpression : expression (STRING_CONCAT expression)+;
compoundExpression : addSubtractExpression | timesExpression | divideExpression | raisedExpression | equalExpression | notEqualExpression | lessThanExpression | greaterThanExpression | andExpression | orExpression | matchesExpression | plusMinusPattern | ifThenElseExpression | stringConcatExpression | invalidOpExpression;

constructor : CONSTRUCTOR typeName COLON constructorName;

standardFunction : FUNCTION ident;
callTarget : varRef | standardFunction | constructor | unfinished;
callExpression : CALL callTarget OPEN_BRACKET (topLevelExpression | expression (COMMA expression)+) CLOSE_BRACKET;

tupleExpression : OPEN_BRACKET expression (COMMA expression)+ CLOSE_BRACKET;
arrayExpression : OPEN_SQUARE (compoundExpression | (expression (COMMA expression)*))? CLOSE_SQUARE;

newVariable : NEWVAR ident;
typeName : ident;
constructorName : ident;
pattern : topLevelExpression (CASEGUARD topLevelExpression)?;

/* Single argument, matched once as variable name or tuple pattern */
// functionArg : (newVariable | patternTuple) COLON expression;
/* Single argument, matched by multiple cases (equivalent to FUNCTION x : @match x) */
// functionCase : matchClause+;
//function: FUNCTION (functionArg | functionCase);

matchClause : CASE pattern (ORCASE pattern)* THEN expression;
match : MATCH expression matchClause* ENDMATCH;

bracketedExpression : OPEN_BRACKET topLevelExpression CLOSE_BRACKET;
// callExpression doesn't need brackets because the constructor means it's identifiable from its left token.  Same for fixTypeExpression and constructor
expression : bracketedExpression | terminal | callExpression | tupleExpression | arrayExpression;
topLevelExpression : compoundExpression | match | expression /* includes terminal */;

completeExpression: topLevelExpression EOF;

completeBooleanLiteral : booleanLiteral EOF;
completeNumericLiteral : numericLiteral EOF;