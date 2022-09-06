parser grammar MainParser2;

options { tokenVocab = MainLexer2; }

@members {
    String currentContent;
}

item : ~NEWLINE;

detailLine : DETAIL_LINE;
detail: DETAIL_BEGIN detailLine* DETAIL_END;
blank : NEWLINE;

content : { currentContent = getCurrentToken().getText(); } ATOM detail { getCurrentToken().getText().equals(currentContent) }? ATOM NEWLINE; 

file : SOFTWARE NEWLINE VERSION item NEWLINE blank* (content blank*)* EOF;
