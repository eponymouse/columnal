lexer grammar TransformationLexer;

import BasicLexer;

EXPRESSION_BEGIN : '@EXPRESSION' -> pushMode(EXPRESSION_MODE);
VALUE_BEGIN : '@VALUE' -> pushMode(VALUE_MODE);
TYPE_BEGIN : '@TYPE' -> pushMode(TYPE_MODE);

mode EXPRESSION_MODE;
EXPRESSION_END: NEWLINE -> popMode;
EXPRESSION: (~[\n\r])+;

mode VALUE_MODE;
VALUE_END: NEWLINE -> popMode;
VALUE: (~[\n\r])+;

mode TYPE_MODE;
TYPE: (~[\n\r@])+ -> popMode;
