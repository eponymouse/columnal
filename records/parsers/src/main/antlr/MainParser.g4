parser grammar MainParser;

options { tokenVocab = MainLexer; }

item : ~NEWLINE;

tableId : item;
importType : item;
filePath : item;
dataSourceLinkHeader : DATA tableId LINKED importType filePath dataFormat;
dataSourceImmediate : DATA tableId dataFormat values NEWLINE;
values : VALUES detailPrefixed VALUES;

dataSource : dataSourceLinkHeader | dataSourceImmediate;

transformationName : item;
sourceName : item;
transformation : TRANSFORMATION tableId transformationName NEWLINE SOURCE sourceName* NEWLINE detailPrefixed NEWLINE;

// For copy and paste:
isolatedValues : units types dataFormat values;

detailLine : DETAIL_LINE;
detail: BEGIN detailLine* DETAIL_END;
detailPrefixed: BEGIN detailLine* DETAIL_END;

numRows : item;
dataFormat : FORMAT (SKIPROWS numRows)? detailPrefixed FORMAT NEWLINE;

//columnFormat : columnType item NEWLINE;

//columnType : TEXT | BLANK | NUMBER STRING item item | DATE STRING;

blank : NEWLINE;

display : DISPLAY detail DISPLAY NEWLINE;

table : (dataSource | transformation) display? END tableId NEWLINE;

units : UNITS detail UNITS NEWLINE;
types : TYPES detail TYPES NEWLINE;

comment : COMMENT CONTENT detail CONTENT NEWLINE display END COMMENT NEWLINE;

topLevelItem : table | comment;

file : SOFTWARE NEWLINE VERSION item NEWLINE blank* units blank* types blank* (topLevelItem blank*)* (display blank*)?;
