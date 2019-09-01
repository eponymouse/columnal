parser grammar TableParser2;

options { tokenVocab = TableLexer2; }

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
transformation : tableId NEWLINE transformationName NEWLINE SOURCE sourceName* NEWLINE detail NEWLINE;

detailLine : DETAIL_LINE;
detail: BEGIN detailLine* DETAIL_END;

numRows : item;
dataFormat : FORMAT (SKIPROWS numRows)? detail FORMAT NEWLINE;

display : DISPLAY detail DISPLAY NEWLINE;

tableData : dataSource display?;
tableTransformation : transformation display?;

comment : COMMENT CONTENT detail CONTENT NEWLINE display END COMMENT NEWLINE;
