lexer grammar TransformationLexer;

import BasicLexer;

EXPRESSION_BEGIN : '@EXPRESSION' -> pushMode(EXPRESSION_MODE);
VALUE_BEGIN : '@VALUE' -> pushMode(VALUE_MODE);
INVALID_VALUE_BEGIN : '@INVALIDVALUE' -> pushMode(VALUE_MODE);
TYPE_BEGIN : '@TYPE' -> pushMode(TYPE_MODE);

mode EXPRESSION_MODE;
EXPRESSION_END: NEWLINE -> popMode;
EXPRESSION: (~[\n\r])+;

mode VALUE_MODE;
VALUE_END: '@ENDVALUE' -> popMode;
VALUE: (~[\n\r@])+;

mode TYPE_MODE;
TYPE_END : NEWLINE -> popMode;
TYPE: (~[\n\r])+;
