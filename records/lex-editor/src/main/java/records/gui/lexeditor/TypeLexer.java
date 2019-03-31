package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import utility.IdentifierUtility;
import utility.Pair;

public class TypeLexer implements Lexer<TypeExpression, CodeCompletionContext>
{
    @SuppressWarnings("units")
    @Override
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content)
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

            for (DataType dataType : ImmutableList.of(DataType.NUMBER, DataType.BOOLEAN, DataType.TEXT))
            {
                if (content.startsWith(dataType.toString(), curIndex))
                {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), new Span(curIndex, curIndex + dataType.toString().length()), c -> {});
                    curIndex += dataType.toString().length();
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
        @Recorded TypeExpression saved = saver.finish(new Span(curIndex, curIndex));
        return new LexerResult<>(saved, content, i -> i, saver.getErrors(), ImmutableList.of());
    }
}
