parser grammar ExpressionParser;

options { tokenVocab = ExpressionLexer; }

tableId : (STRING | UNQUOTED_IDENT);
columnId : (STRING | UNQUOTED_IDENT);
columnRef : tableId? AT columnId;

numericLiteral : NUMBER;

terminal : columnRef | numericLiteral;

binaryOp : BINARY_OP;
binaryOpExpression : OPEN_BRACKET expression binaryOp expression CLOSE_BRACKET;

expression : binaryOpExpression | terminal;