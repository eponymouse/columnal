lexer grammar ExpressionLexer;

import StringLexerShared;
WS : ( ' ' | '\t' )+ -> skip ;

OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

UNIT : '{' ~[}]* '}'
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };
TYPE : '`' ~[`]* '`'
    { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };

PLUS_MINUS: '\u00B1';
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
COLUMN : '@column';
WHOLECOLUMN: '@entirecolumn';
MATCH : '@match';
ENDMATCH : '@endmatch';
CASE : '@case';
COLON: ':';
ORCASE : '@or';
IF : '@if';
THEN : '@then';
ELSE : '@else';
ENDIF : '@endif';
CASEGUARD: '@given';
FUNCTION : '@function';
NEWVAR : '@newvar';
ANY : '@anything';
UNFINISHED : '@unfinished';
INVALIDOPS : '@invalidops';
CONSTRUCTOR : '@tag';
UNKNOWNCONSTRUCTOR : '@unknowntag';
CALL: '@call'; 
RAISEDTO : '^';
COMMA: ',';
STRING_CONCAT : ';';
IMPLICIT_LAMBDA_PARAM : '?';

NUMBER : [0-9]+ ('.' [0-9]+)?;

TRUE: 'true';
FALSE: 'false';

UNQUOTED_IDENT : ~[ \t\n\r"(){}[\]@+\-/*&|=?:;~$!<>\\,`]+ (' '+ ~[ \t\n\r"(){}[\]@+\-/*&|=?:;~$!<>\\,`]+)* 
  {setText(GrammarUtility.collapseSpaces(getText()));}
  {GrammarUtility.validUnquoted(getText())}?;

