package records.gui.lexeditor;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpression.UnitLookupException;
import records.transformations.expression.UnitExpressionIntLiteral;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.TranslationUtility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.stream.IntStream;

public class UnitLexer extends Lexer<UnitExpression, CodeCompletionContext>
{
    public static enum UnitOp implements ExpressionToken
    {
        MULTIPLY("*", "op.times"), DIVIDE("/", "op.divide"), RAISE("^", "op.raise");

        private final String op;
        private final @LocalizableKey String localNameKey;

        private UnitOp(String op, @LocalizableKey String localNameKey)
        {
            this.op = op;
            this.localNameKey = localNameKey;
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

        public boolean isClosing()
        {
            return this == CLOSE_ROUND;
        }
    }

    private final UnitManager unitManager;
    private final boolean requireConcrete;

    public UnitLexer(UnitManager unitManager, boolean requireConcrete)
    {
        this.unitManager = unitManager;
        this.requireConcrete = requireConcrete;
    }

    @Override
    public LexerResult<UnitExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        UnitSaver saver = new UnitSaver();
        RemovedCharacters removedCharacters = new RemovedCharacters();
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        nextToken: while (curIndex < content.length())
        {
            for (UnitBracket bracket : UnitBracket.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveBracket(bracket, removedCharacters.map(curIndex, bracket.getContent()));
                    chunks.add(new ContentChunk(bracket.getContent(), bracket.isClosing() ? ChunkType.CLOSING : ChunkType.OPENING));
                    curIndex += rawLength(bracket.getContent());
                    continue nextToken;
                }
            }
            for (UnitOp op : UnitOp.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()));
                    chunks.add(new ContentChunk(op.getContent(), ChunkType.OPENING));
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
                String numContent = content.substring(startIndex, curIndex);
                saver.saveOperand(new UnitExpressionIntLiteral(Integer.parseInt(numContent)), removedCharacters.map(startIndex, curIndex));
                chunks.add(new ContentChunk(numContent, ChunkType.IDENT));
                continue nextToken;
            }

            @Nullable Pair<@UnitIdentifier String, @RawInputLocation Integer> parsed = IdentifierUtility.consumeUnitIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new SingleUnitExpression(parsed.getFirst()), removedCharacters.map(curIndex, parsed.getSecond()));
                chunks.add(new ContentChunk(parsed.getFirst(), ChunkType.IDENT));
                curIndex = parsed.getSecond();
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            String badChar = content.substring(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidSingleUnitExpression(badChar), invalidCharLocation);
            chunks.add(new ContentChunk(badChar, ChunkType.OPENING));
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded UnitExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        Pair<ArrayList<CaretPos>, ImmutableList<@CanonicalLocation Integer>> caretPositions = calculateCaretPos(chunks);
        
        if (requireConcrete)
        {
            @RawInputLocation int lastIndex = curIndex;
            try
            {
                saved.asUnit(unitManager);
            }
            catch (UnitLookupException e)
            {
                if (e.errorMessage != null || !e.quickFixes.isEmpty())
                {
                    saver.locationRecorder.addErrorAndFixes(new CanonicalSpan(CanonicalLocation.ZERO, removedCharacters.map(lastIndex)), e.errorMessage == null ? StyledString.s("") : e.errorMessage, Utility.mapListI(e.quickFixes, f -> new TextQuickFix(saver.locationRecorder.recorderFor(f.getReplacementTarget()), u -> u.save(false, true), f)));
                }
            }
        }

        return new LexerResult<>(saved, content, removedCharacters, false, ImmutableList.copyOf(caretPositions.getFirst()), ImmutableList.copyOf(caretPositions.getSecond()), StyledString.s(content), saver.getErrors(), saver.locationRecorder, makeCompletions(chunks, this::makeCompletions), new BitSet(), !saver.hasUnmatchedBrackets());
    }
    
    private CodeCompletionContext makeCompletions(String stem, @CanonicalLocation int canonIndex, ChunkType curType, ChunkType preceding)
    {
        return new CodeCompletionContext(ImmutableList.of(new LexCompletionGroup(Utility.mapListI(unitManager.getAllDeclared(), u -> {
            int len = Utility.longestCommonStartIgnoringCase(u.getName(), 0, stem, 0);
            return new LexCompletion(canonIndex, len, u.getName());
        }), null, 2)));
    }
}
