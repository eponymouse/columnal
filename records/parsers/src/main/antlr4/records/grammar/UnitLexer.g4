lexer grammar UnitLexer;

import StringLexerShared;

WS : ( ' ' | '\t' )+;

NEWLINE : '\r'? '\n' ;

NUMBER : [+\-]? [0-9] [_0-9]* ('.' [_0-9]* [0-9])? {setText(getText().replace("_", ""));};

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

UNITVAR : '@unitvar';

// No spaces in unit identifiers
IDENT : [\p{Alpha}\p{General_Category=Other_Letter}\p{General_Category=Currency_Symbol}]+ ('_' [\p{Alpha}\p{General_Category=Other_Letter}\p{General_Category=Currency_Symbol}]+)*;

COMMENT : '//' ~[\r\n]* NEWLINE -> skip;
