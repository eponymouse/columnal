parser grammar SortParser;

// TODO rename if the combination in same file works out

options { tokenVocab = BasicLexer; }

item : ATOM | STRING;

sortKW : {_input.LT(1).getText().equals("SORT")}? ATOM;
orderKW : {_input.LT(1).getText().equals("ASCENDING") || _input.LT(1).getText().equals("DESCENDING")}? ATOM;
orderBy : orderKW column=item NEWLINE;
sort : sortKW srcTableId=item NEWLINE orderBy+;

summaryKW : {_input.LT(1).getText().equals("SUMMARYOF")}? ATOM;
splitKW : {_input.LT(1).getText().equals("SPLIT")}? ATOM;
fromKW : {_input.LT(1).getText().equals("FROM")}? ATOM;
summaryType : item;
summaryCol : fromKW column=item summaryType+ NEWLINE;
splitBy : splitKW column=item NEWLINE;
summary : summaryKW srcTableId=item NEWLINE summaryCol+ splitBy*;
