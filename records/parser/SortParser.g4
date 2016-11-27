parser grammar SortParser;

// TODO rename if the combination in same file works out

options { tokenVocab = BasicLexer; }

item : ATOM | STRING;

orderKW : {_input.LT(1).getText().equals("ASCENDING") || _input.LT(1).getText().equals("DESCENDING")}? ATOM;
orderBy : orderKW column=item NEWLINE;
sort : orderBy+;

splitKW : {_input.LT(1).getText().equals("SPLIT")}? ATOM;
fromKW : {_input.LT(1).getText().equals("SUMMARY")}? ATOM;
summaryType : item;
summaryCol : fromKW column=item summaryType+ NEWLINE;
splitBy : splitKW column=item NEWLINE;
summary : summaryCol+ splitBy*;
