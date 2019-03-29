package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import utility.IdentifierUtility;
import utility.Pair;

public class TypeLexer implements Lexer<TypeExpression>
{
    private TypeExpression saved = new InvalidIdentTypeExpression("");
    
    @Override
    public TypeExpression getSaved()
    {
        return saved;
    }

    @SuppressWarnings("units")
    @Override
    public Pair<String, CaretPosMapper> process(String content)
    {
        TypeSaver saver = new TypeSaver();
        int curIndex = 0;
        nextToken: while (curIndex < content.length())
        {
            for (Keyword bracket : Keyword.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveKeyword(bracket, new Span(curIndex, curIndex + bracket.getContent().length()), c -> {});
                    curIndex += bracket.getContent().length();
                    continue nextToken;
                }
            }
            for (Operator op : Operator.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    curIndex += op.getContent().length();
                    continue nextToken;
                }
            }

            @Nullable Pair<@ExpressionIdentifier String, Integer> parsed = IdentifierUtility.consumeExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new IdentTypeExpression(parsed.getFirst()), new Span(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                continue nextToken;
            }
            
            curIndex += 1;
        }
        saved = saver.finish(new Span(curIndex, curIndex));
        return new Pair<>(content, i -> i);
    }

    @Override
    public int[] getCaretPositions()
    {
        return new int[0];
    }
}
