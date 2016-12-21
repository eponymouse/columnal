parser grammar MainParser;

options { tokenVocab = MainLexer; }

item : ~NEWLINE;

tableId : item;
importType : item;
filePath : item;
dataSourceLinkHeader : DATA tableId LINKED importType filePath dataFormat;
dataSourceImmediate : DATA tableId dataFormat VALUES detail VALUES NEWLINE;

dataSource : dataSourceLinkHeader | dataSourceImmediate;

transformationName : item;
sourceName : item;
transformation : TRANSFORMATION tableId transformationName NEWLINE SOURCE sourceName+ NEWLINE detail NEWLINE;

detail: BEGIN DETAIL_LINE* DETAIL_END;

numRows : item;
dataFormat : FORMAT (SKIPROWS numRows)? detail FORMAT NEWLINE;

//columnFormat : columnType item NEWLINE;

//columnType : TEXT | BLANK | NUMBER STRING item item | DATE STRING;

blank : NEWLINE;

position : POSITION item item item item NEWLINE;

table : (dataSource | transformation) position END tableId NEWLINE;

units : UNITS detail UNITS;
types : TYPES detail TYPES;

file : VERSION item NEWLINE blank* units blank* types blank* (table blank*)+;