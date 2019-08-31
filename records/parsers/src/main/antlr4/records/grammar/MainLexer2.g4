lexer grammar MainLexer2;

import BasicLexer;

@members {
    String currentPrefix;
}

END : '@END';
BEGIN : '@BEGIN' ' '* ([A-Z][A-Z]+)? '\r'? '\n' {currentPrefix = getText().substring("@BEGIN".length()).trim(); } -> pushMode(DETAIL);
VERSION : 'VERSION';
SOFTWARE : 'COLUMNAL';

mode DETAIL;
DETAIL_END: ([A-Z][A-Z]+)? ' '* '@END' {getText().startsWith(currentPrefix) && getText().substring(currentPrefix.length()).trim().equals("@END")}? -> popMode;
DETAIL_LINE: (~[\n\r])* '\r'? '\n' {getText().startsWith(currentPrefix) && !getText().substring(currentPrefix.length()).trim().startsWith("@END")}? {setText(getText().substring(currentPrefix.length()));};
