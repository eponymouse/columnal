lexer grammar MainLexer2;

import BasicLexer;

@members {
    String currentPrefix;
}

DETAIL_BEGIN : '@BEGIN' ' '* ([A-Z][A-Z]+) ' '* '\r'? '\n' {currentPrefix = getText().substring("@BEGIN".length()).trim(); } -> pushMode(DETAIL);
VERSION : 'VERSION';
SOFTWARE : 'COLUMNAL';

mode DETAIL;
DETAIL_LINE: (~[\n\r])* '\r'? '\n' {getText().trim().startsWith(currentPrefix)}? {setText(getText().trim().substring(currentPrefix.length()).trim());};
DETAIL_END: '@END' ' '* ([A-Z][A-Z]+) {getText().endsWith(currentPrefix)}? -> popMode;
