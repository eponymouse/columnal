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
import styled.StyledCSS;
import styled.StyledString;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.BitSet;
import java.util.stream.IntStream;

public class UnitLexer implements Lexer<UnitExpression, CodeCompletionContext>
{
    @SuppressWarnings("units")
    @Override
    public LexerResult<UnitExpression, CodeCompletionContext> process(String content, int curCaretPos)
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
            
            if ((content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9') || 
                (content.charAt(curIndex) == '-' && curIndex + 1 < content.length() && (content.charAt(curIndex + 1) >= '0' && content.charAt(curIndex + 1) <= '9')))
            {
                int startIndex = curIndex;
                // Minus only allowed at start:
                if (content.charAt(curIndex) == '-')
                {
                    curIndex += 1;
                }
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

            Span invalidCharLocation = new Span(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidSingleUnitExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter.start", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            
            curIndex += 1;
        }
        @Recorded UnitExpression saved = saver.finish(new Span(curIndex, curIndex));
        return new LexerResult<>(saved, content, new BitSet(), false, IntStream.range(0, content.length() + 1).toArray(), StyledString.s(content), new BitSet(), saver.getErrors(), ImmutableList.of(), new BitSet(), !saver.hasUnmatchedBrackets());
    }
}
