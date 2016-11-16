parser grammar SortParser;

// TODO rename if the combination in same file works out

options { tokenVocab = BasicLexer; }

sortKW : {_input.LT(1).getText().equals("SORT")}? ATOM;
orderKW : {_input.LT(1).getText().equals("ASCENDING") || _input.LT(1).getText().equals("DESCENDING")}? ATOM;
orderBy : orderKW column=ATOM NEWLINE;
sort : sortKW srcTableId=ATOM NEWLINE orderBy+;

summaryKW : {_input.LT(1).getText().equals("SUMMARYOF")}? ATOM;
splitKW : {_input.LT(1).getText().equals("SPLIT")}? ATOM;
fromKW : {_input.LT(1).getText().equals("FROM")}? ATOM;
summaryType : ATOM;
summaryCol : fromKW column=ATOM summaryType+ NEWLINE;
splitBy : splitKW column=ATOM NEWLINE;
summary : summaryKW srcTableId=ATOM NEWLINE summaryCol+ splitBy*;
