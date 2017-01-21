lexer grammar ExpressionLexer;

/* TODO share this with basic lexer? */
fragment ESCAPED_ESCAPE : '^^';
fragment ESCAPED_QUOTE : '^"';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : ('"' ( ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ESCAPED_ESCAPE | ~[\n\r^"] )*? '"')
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1).replace("^\"", "\"").replace("^n", "\n").replace("^r", "\r").replace("^^","^")); };

WS : ( ' ' | '\t' )+ -> skip ;

OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

UNIT : '{' ~'}'* '}'
  { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };

PLUS_MINUS: [+-];
TIMES: '*';
DIVIDE: '/';
AND: '&';
OR: '|';
EQUALITY : '=';
NON_EQUALITY : '/=';
LESS_THAN: '<=' | '<';
GREATER_THAN: '>=' | '>';
COLUMN : '@column';
WHOLECOLUMN: '@wholecolumn';
MATCH : '@match';
CASE : '@case';
COLON: ':';
ORCASE : '@orcase';
MAPSTO : '@then';
PATTERN : '$';
CASEGUARD: '@given';
FUNCTION : '@function';
CONSTRUCTOR : '\\';
RAISEDTO : '^';
COMMA: ',';

NUMBER : [+-]? [0-9]+ ('.' [0-9]+)?;

TRUE: 'true';
FALSE: 'false';

UNQUOTED_IDENT : ~[ \t\n\r"()[\]@+-/*&|=?:;~$!<>\\]+ {utility.Utility.validUnquoted(getText())}?;



