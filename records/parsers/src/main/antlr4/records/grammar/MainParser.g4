parser grammar MainParser;

options { tokenVocab = MainLexer; }

item : ~NEWLINE;

tableId : item;
importType : item;
filePath : item;
dataSourceLinkHeader : DATA tableId LINKED importType filePath dataFormat;
dataSourceImmediate : DATA tableId dataFormat values NEWLINE;
values : VALUES detail VALUES;

dataSource : dataSourceLinkHeader | dataSourceImmediate;

transformationName : item;
sourceName : item;
transformation : TRANSFORMATION tableId transformationName NEWLINE SOURCE sourceName* NEWLINE detail NEWLINE;

// For copy and paste:
isolatedValues : units types dataFormat values;

detail: BEGIN DETAIL_LINE* DETAIL_END;

numRows : item;
dataFormat : FORMAT (SKIPROWS numRows)? detail FORMAT NEWLINE;

//columnFormat : columnType item NEWLINE;

//columnType : TEXT | BLANK | NUMBER STRING item item | DATE STRING;

blank : NEWLINE;

// Position is X, Y, Width, Height.  Snapped to is Table ID, width
displayTablePosition : POSITION item item NEWLINE;
displayShowColumns : SHOWCOLUMNS (ALL | ALTERED | COLLAPSED | EXCEPT item*) NEWLINE;
display : displayTablePosition displayShowColumns;

table : (dataSource | transformation) display END tableId NEWLINE;

units : UNITS detail UNITS NEWLINE;
types : TYPES detail TYPES NEWLINE;

file : VERSION item NEWLINE blank* units blank* types blank* (table blank*)+;