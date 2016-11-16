parser grammar MainParser;

options { tokenVocab = MainLexer; }

item : ~NEWLINE;

tableId : item;
importType : item;
filePath : item;
dataSourceLinkHeader : DATA tableId LINKED importType filePath NEWLINE;
dataSourceImmedate : DATA tableId detail;

dataSource : (dataSourceLinkHeader | (dataSourceImmedate detail NEWLINE)) dataFormat;

transformationName : item;
transformation : TRANSFORMATION tableId transformationName detail NEWLINE;

detail: BEGIN DETAIL_LINE+ DETAIL_END;

numRows : item;
dataFormat : FORMAT (SKIP numRows)? BEGIN NEWLINE columnFormat+ END FORMAT NEWLINE;

columnFormat : columnType item NEWLINE;

columnType : TEXT | BLANK | NUMBER STRING item item | DATE STRING;

blank : NEWLINE;

position : POSITION item item item item NEWLINE;

table : (dataSource | transformation) position END tableId NEWLINE;

file : VERSION item NEWLINE blank+ (table blank*)+;