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

BINARY_OP : [+-/*&|=];
AT: '@';

NUMBER : [+-]? [0-9]+ ('.' [0-9]+)?;

UNQUOTED_IDENT : ~[ \t\n\r"()]+;



