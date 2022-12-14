parser grammar TableParser2;

options { tokenVocab = TableLexer2; }

item : ~NEWLINE;

tableId : item;
importType : item;
filePath : item;
//dataSourceLinkHeader : DATA tableId LINKED importType filePath dataFormat;
dataSourceImmediate : tableId NEWLINE dataFormat values;
values : VALUES detail VALUES NEWLINE;

//dataSourceLinkHeader | 
dataSource : dataSourceImmediate;

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
table: tableData | tableTransformation;

comment : CONTENT detail CONTENT NEWLINE display;
