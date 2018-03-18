lexer grammar ExpressionLexer;

import StringLexerShared;
WS : ( ' ' | '\t' )+ -> skip ;

OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

TYPE_BEGIN : '{|' -> pushMode(TYPE_MODE);

UNIT : '{' ~[|}]* '}'
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };

PLUS_MINUS: '+-';
ADD_OR_SUBTRACT: [+\-];
TIMES: '*';
DIVIDE: '/';
AND: '&';
OR: '|';
EQUALITY : '=';
NON_EQUALITY : '<>';
LESS_THAN: '<=' | '<';
GREATER_THAN: '>=' | '>';
MATCHES: '~';
BACKSLASH: '\\';
COLUMN : '@column';
WHOLECOLUMN: '@wholecolumn';
MATCH : '@match';
CASE : '@case';
COLON: ':';
ORCASE : '@or';
IF : '@if';
THEN : '@then';
ELSE : '@else';
CASEGUARD: '@given';
FUNCTION : '@function';
NEWVAR : '@newvar';
ANY : '@any';
UNFINISHED : '@unfinished';
INVALIDOPS : '@invalidops';
CONSTRUCTOR : '@tag';
UNKNOWNCONSTRUCTOR : '@unknowntag';
FIX_TYPE : '@type'; 
RAISEDTO : '^';
COMMA: ',';
STRING_CONCAT : ';';
IMPLICIT_LAMBDA_PARAM : '?';

NUMBER : [+\-]? [0-9]+ ('.' [0-9]+)?;

TRUE: 'true';
FALSE: 'false';

UNQUOTED_IDENT : ~[ \t\n\r"(){}[\]@+\-/*&|=?:;~$!<>\\]+ {GrammarUtility.validUnquoted(getText())}?;


mode TYPE_MODE;
TYPE_END: '|}' -> popMode;
// Anything that isn't the closing:
TYPE_CONTENT: ~'|'+;

