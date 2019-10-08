lexer grammar ExpressionLexer2;

RAW_STRING : ('"' (~[\n\r"])*? '"')
    { String orig = getText(); setText(orig.substring(1, orig.length() - 1)); };
WS : ( ' ' | '\t' )+ -> skip ;

OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';

// From https://stackoverflow.com/a/29247186
CUSTOM_LITERAL : [a-zA-Z]+ CURLIED;
CURLIED : '{' (CURLIED|.)*? '}';

PLUS_MINUS: '\u00B1';
ADD_OR_SUBTRACT: [+\-];
TIMES: '*';
DIVIDE: '/';
AND: '&';
OR: '|';
EQUALITY : '=';
EQUALITY_PATTERN : '=~';
NON_EQUALITY : '<>';
LESS_THAN: '<=' | '<';
GREATER_THAN: '>=' | '>';
MATCH : '@match';
ENDMATCH : '@endmatch';
CASE : '@case';
SCOPE : '\\';
COLON: ':';
ORCASE : '@orcase';
IF : '@if';
THEN : '@then';
ELSE : '@else';
ENDIF : '@endif';
CASEGUARD: '@given';
FUNCTION : '@function';
ENDFUNCTION : '@endfunction';
ANYTHING : '_';
UNFINISHED : '@unfinished';
INVALIDOPS : '@invalidops';
INVALIDEXP : '@invalidexp';
CALL: '@call'; 
RAISEDTO : '^';
COMMA: ',';
STRING_CONCAT : ';';
IMPLICIT_LAMBDA_PARAM : '?';
DEFINE: '@define';
ENDDEFINE: '@enddefine';
HAS_TYPE: '::';
FIELD_ACCESS : '#';

NUMBER : [0-9]+ ('.' [0-9]+)?;

TRUE: 'true';
FALSE: 'false';
  
IDENT : [\p{Alpha}\p{General_Category=Other_Letter}] (('_' | ' ')? [\p{Alpha}\p{Digit}\p{General_Category=Other_Letter}])*;

