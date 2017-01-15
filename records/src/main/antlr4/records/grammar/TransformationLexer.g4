lexer grammar TransformationLexer;

import BasicLexer;

EXPRESSION_BEGIN : '@EXPRESSION' -> pushMode(EXPRESSION_MODE);

mode EXPRESSION_MODE;
EXPRESSION_END: NEWLINE -> popMode;
EXPRESSION: (~[\n\r])+;
