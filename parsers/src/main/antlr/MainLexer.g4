lexer grammar MainLexer;

import BasicLexer;

@members {
    String currentPrefix;
}

DATA : 'DATA';
TEXTFILE : 'TEXTFILE';
LINKED : 'LINKED';
END : '@END';
BEGIN : '@BEGIN' ' '* ([A-Z][A-Z]+)? '\r'? '\n' {currentPrefix = getText().substring("@BEGIN".length()).trim(); } -> pushMode(DETAIL);
TRANSFORMATION: 'TRANSFORMATION';
SOURCE: 'SOURCE';
FORMAT : 'FORMAT';
VALUES : 'VALUES';
SKIPROWS : 'SKIP';
TYPES : 'TYPES';
VERSION : 'VERSION';
UNITS : 'UNITS';
DISPLAY : 'DISPLAY';
SOFTWARE : 'COLUMNAL';
COMMENT : 'COMMENT';
CONTENT : 'CONTENT';



mode DETAIL;
DETAIL_END: ([A-Z][A-Z]+)? ' '* '@END' {getText().startsWith(currentPrefix) && getText().substring(currentPrefix.length()).trim().equals("@END")}? -> popMode;
DETAIL_LINE: (~[\n\r])* '\r'? '\n' {getText().startsWith(currentPrefix) && !getText().substring(currentPrefix.length()).trim().startsWith("@END")}? {setText(getText().substring(currentPrefix.length()));};
