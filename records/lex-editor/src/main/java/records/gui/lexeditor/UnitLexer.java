package records.gui.lexeditor;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.BitSet;
import java.util.stream.IntStream;

public class UnitLexer extends Lexer<UnitExpression, CodeCompletionContext>
{
    public static enum UnitOp implements ExpressionToken
    {
        MULTIPLY("*"), DIVIDE("/"), RAISE("^");

        private final String op;

        private UnitOp(String op)
        {
            this.op = op;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return op;
        }
    }

    public static enum UnitBracket implements ExpressionToken
    {
        OPEN_ROUND("("), CLOSE_ROUND(")");

        private final String bracket;

        private UnitBracket(String bracket)
        {
            this.bracket = bracket;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return bracket;
        }
    }
    
    @Override
    public LexerResult<UnitExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        UnitSaver saver = new UnitSaver();
        RemovedCharacters removedCharacters = new RemovedCharacters();
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        nextToken: while (curIndex < content.length())
        {
            for (UnitBracket bracket : UnitBracket.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveBracket(bracket, removedCharacters.map(curIndex, bracket.getContent()), c -> {});
                    curIndex += rawLength(bracket.getContent());
                    continue nextToken;
                }
            }
            for (UnitOp op : UnitOp.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()), c -> {});
                    curIndex += rawLength(op.getContent());
                    continue nextToken;
                }
            }
            
            if ((content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9') || 
                (content.charAt(curIndex) == '-' && curIndex + 1 < content.length() && (content.charAt(curIndex + 1) >= '0' && content.charAt(curIndex + 1) <= '9')))
            {
                @RawInputLocation int startIndex = curIndex;
                // Minus only allowed at start:
                if (content.charAt(curIndex) == '-')
                {
                    curIndex += RawInputLocation.ONE;
                }
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
                    curIndex += RawInputLocation.ONE;
                saver.saveOperand(new UnitExpressionIntLiteral(Integer.parseInt(content.substring(startIndex, curIndex))), removedCharacters.map(startIndex, curIndex), c -> {});
                continue nextToken;
            }

            @Nullable Pair<@UnitIdentifier String, @RawInputLocation Integer> parsed = IdentifierUtility.consumeUnitIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new SingleUnitExpression(parsed.getFirst()), removedCharacters.map(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidSingleUnitExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded UnitExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        @SuppressWarnings("units")
        ImmutableList<CaretPos> caretPositions = IntStream.range(0, content.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList());
        return new LexerResult<>(saved, content, removedCharacters, false, caretPositions, StyledString.s(content), saver.getErrors(), ImmutableList.of(), new BitSet(), !saver.hasUnmatchedBrackets());
    }
}
