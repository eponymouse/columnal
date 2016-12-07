lexer grammar MainLexer;

import BasicLexer;

DATA : 'DATA';
TEXTFILE : 'TEXTFILE';
LINKED : 'LINKED';
END : '@END';
BEGIN : '@BEGIN' '\r'? '\n' -> pushMode(DETAIL);
TRANSFORMATION: 'TRANSFORMATION';
SOURCE: 'SOURCE';
FORMAT : 'FORMAT';
POSITION : 'POSITION';
VALUES : 'VALUES';
SKIPROWS : 'SKIP';
VERSION : 'VERSION';

mode DETAIL;
DETAIL_END: '@END' -> popMode;
DETAIL_LINE: ~[\n\r@] (~[\n\r])* '\r'? '\n';
