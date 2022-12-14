lexer grammar StringLexerShared;

fragment ESCAPED_AT : '^a';
fragment ESCAPED_ESCAPE : '^c';
fragment ESCAPED_QUOTE : '^q';
fragment ESCAPED_TAB : '^t';
fragment ESCAPED_N : '^n';
fragment ESCAPED_R : '^r';
STRING : ('"' ( ~[\n\r^"] | ESCAPED_TAB | ESCAPED_QUOTE | ESCAPED_R | ESCAPED_N | ESCAPED_AT | ESCAPED_ESCAPE )*? '"')
  { String orig = getText(); setText(GrammarUtility.processEscapes(orig)); };
