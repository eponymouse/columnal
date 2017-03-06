lexer grammar UnitLexer;

import StringLexerShared;

WS : ( ' ' | '\t' )+;

NEWLINE : '\r'? '\n' ;

NUMBER : [+-]? [0-9] [_0-9]* ('.' [_0-9]* [0-9])? {setText(getText().replace("_", ""));};

POWER : '^';
TIMES: '*';
DIVIDE: '/';
EQUALS : '=';
UNIT : 'UNIT';
OPEN_BRACKET: '(';
CLOSE_BRACKET: ')';
PREFIX : 'PREFIX';
SUFFIX : 'SUFFIX';
ALIAS : 'ALIAS';

IDENT : ~[0-9 \t\r\n^*/@()={}[\]"]+;

COMMENT : '//' ~[\r\n]* NEWLINE -> skip;
