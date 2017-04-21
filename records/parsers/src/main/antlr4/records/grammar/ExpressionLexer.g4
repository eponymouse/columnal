lexer grammar ExpressionLexer;

import StringLexerShared;
WS : ( ' ' | '\t' )+ -> skip ;

OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

UNIT : '{' ~'}'* '}'
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };

PLUS_MINUS: '+-';
ADD_OR_SUBTRACT: [+-];
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
ORCASE : '@orcase';
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
RAISEDTO : '^';
COMMA: ',';

NUMBER : [+-]? [0-9]+ ('.' [0-9]+)?;

TRUE: 'true';
FALSE: 'false';

UNQUOTED_IDENT : ~[ \t\n\r"()[\]@+-/*&|=?:;~$!<>\\~]+ {GrammarUtility.validUnquoted(getText())}?;



