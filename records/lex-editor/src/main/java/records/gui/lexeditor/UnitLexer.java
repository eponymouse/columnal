package records.gui.lexeditor;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import utility.IdentifierUtility;
import utility.Pair;

import java.util.BitSet;

public class UnitLexer implements Lexer<UnitExpression, CodeCompletionContext>
{
    @SuppressWarnings("units")
    @Override
    public LexerResult<UnitExpression, CodeCompletionContext> process(String content)
    {
        UnitSaver saver = new UnitSaver();
        int curIndex = 0;
        nextToken: while (curIndex < content.length())
        {
            for (UnitBracket bracket : UnitBracket.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveBracket(bracket, new Span(curIndex, curIndex + bracket.getContent().length()), c -> {});
                    curIndex += bracket.getContent().length();
                    continue nextToken;
                }
            }
            for (UnitOp op : UnitOp.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    curIndex += op.getContent().length();
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
            {
                int startIndex = curIndex;
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
                    curIndex += 1;
                saver.saveOperand(new UnitExpressionIntLiteral(Integer.parseInt(content.substring(startIndex, curIndex))), new Span(startIndex, curIndex), c -> {});
                continue nextToken;
            }

            @Nullable Pair<@UnitIdentifier String, Integer> parsed = IdentifierUtility.consumeUnitIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new SingleUnitExpression(parsed.getFirst()), new Span(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                continue nextToken;
            }
            
            curIndex += 1;
        }
        @Recorded UnitExpression saved = saver.finish(new Span(curIndex, curIndex));
        return new LexerResult<>(saved, content, i -> i, saver.getErrors(), ImmutableList.of(), new BitSet());
    }
}
