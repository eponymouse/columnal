parser grammar DisplayParser;

options { tokenVocab = DisplayLexer; }

item : ~NEWLINE;

// Position is X, Y.
displayTablePosition : POSITION item item NEWLINE;
displayShowColumns : SHOWCOLUMNS (ALL | ALTERED | COLLAPSED | EXCEPT item*) NEWLINE;

tableDisplayDetails : displayTablePosition displayShowColumns EOF;

// X, Y, Width, Height
commentDisplayDetails : POSITION item item item item NEWLINE;

// Column index (zero-based), then column width
columnWidth: COLUMNWIDTH item item NEWLINE; 

globalDisplayDetails : columnWidth* EOF;
