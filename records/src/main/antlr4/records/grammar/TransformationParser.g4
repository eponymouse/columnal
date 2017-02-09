parser grammar TransformationParser;

options { tokenVocab = TransformationLexer; }

item : ATOM | STRING;

expression: EXPRESSION_BEGIN EXPRESSION EXPRESSION_END;
value: VALUE_BEGIN VALUE VALUE_END;
typeValue: TYPE_BEGIN TYPE TYPE_VALUE VALUE VALUE_END;

/* Hide: */
hideKW : {_input.LT(1).getText().equals("HIDE")}? ATOM;
hideColumn : hideKW column=item NEWLINE;
hideColumns : hideColumn*;

/* Sort: */
orderKW : {_input.LT(1).getText().equals("ASCENDING") || _input.LT(1).getText().equals("DESCENDING")}? ATOM;
orderBy : orderKW column=item NEWLINE;
sort : orderBy+;

splitKW : {_input.LT(1).getText().equals("SPLIT")}? ATOM;
fromKW : {_input.LT(1).getText().equals("SUMMARY")}? ATOM;
summaryType : item;
summaryCol : fromKW column=item expression; // No newline because expression consumes it
splitBy : splitKW column=item NEWLINE;
summary : summaryCol+ splitBy*;

/* Concat: */
concatMissingColumnName : item;
concatOmit : {_input.LT(1).getText().equals("@OMIT")}? ATOM;
concatMissingColumn : concatMissingColumnName (concatOmit | typeValue) NEWLINE;
concatMissing : concatMissingColumn*;