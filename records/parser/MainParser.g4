parser grammar MainParser;

options { tokenVocab = MainLexer; }

tableId : ITEM;
importType : ITEM;
filePath : ITEM;
dataSourceLinkHeader : DATA tableId LINKED importType filePath NEWLINE;
dataSourceImmedate : DATA tableId BEGIN NEWLINE;

immediateDataLine : ITEM+ NEWLINE;

dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

transformationName : ITEM;
transformation : TRANSFORMATION tableId transformationName NEWLINE transformationDetail+;

transformationDetail: ITEM+ NEWLINE;

numRows : ITEM;
dataFormat : FORMAT (SKIP numRows)? BEGIN NEWLINE columnFormat+ END FORMAT NEWLINE;

columnFormat : columnType ITEM NEWLINE;

columnType : TEXT | BLANK | NUMBER STRING ITEM ITEM | DATE STRING;

blank : NEWLINE;

positionLine : POSITION ITEM ITEM NEWLINE;

table : (dataSource | transformation) positionLine END tableId NEWLINE blank+;

file : VERSION ITEM NEWLINE blank+ table+;