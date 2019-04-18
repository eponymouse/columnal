package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TypeLexer extends Lexer<TypeExpression, CodeCompletionContext>
{
    public static enum Keyword implements ExpressionToken
    {
        OPEN_ROUND("("), CLOSE_ROUND(")"), OPEN_SQUARE("["), CLOSE_SQUARE("]");

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return keyword;
        }
    }

    public static enum Operator implements ExpressionToken
    {
        COMMA(",");

        private final String op;

        private Operator(String op)
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
    
    @Override
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        TypeSaver saver = new TypeSaver();
        boolean prevWasIdent = false;
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        StringBuilder s = new StringBuilder();
        StyledString.Builder d = new StyledString.Builder();
        ImmutableList.Builder<AutoCompleteDetails<CodeCompletionContext>> autoCompletes = ImmutableList.builder();
        RemovedCharacters removedCharacters = new RemovedCharacters();
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                // Keep single space after ident as it may continue ident:
                if (prevWasIdent)
                {
                    s.append(" ");
                    d.append(" ");
                }
                else
                {
                    removedCharacters.set(curIndex);
                }
                prevWasIdent = false;
                curIndex += RawInputLocation.ONE;
                continue nextToken;
            }
            prevWasIdent = false;
            
            for (Keyword bracket : Keyword.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveKeyword(bracket, removedCharacters.map(curIndex, bracket.getContent()), c -> {});
                    curIndex += rawLength(bracket.getContent());
                    s.append(bracket.getContent());
                    d.append(bracket.getContent());
                    continue nextToken;
                }
            }
            for (Operator op : Operator.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()), c -> {});
                    curIndex += rawLength(op.getContent());
                    s.append(op.getContent());
                    d.append(op.getContent() + " ");
                    continue nextToken;
                }
            }
            
            // Important to try longest types first:
            boolean matchedType = false;
            @CanonicalLocation int startOfType = removedCharacters.map(curIndex);
            for (DataType dataType : Utility.<DataType>iterableStream(streamDataTypes().sorted(Comparator.comparing(t -> -t.toString().length()))))
            {
                if (!matchedType && content.startsWith(dataType.toString(), curIndex))
                {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), removedCharacters.map(curIndex, dataType.toString()), c -> {});
                    curIndex += rawLength(dataType.toString());
                    s.append(dataType.toString());
                    d.append(dataType.toString());
                    matchedType = true;
                }
                @SuppressWarnings("units")
                @CanonicalLocation int common = Utility.longestCommonStart(content, curIndex, dataType.toString(), 0);
                if (common > 0)
                {
                    autoCompletes.add(new AutoCompleteDetails<>(new CanonicalSpan(startOfType, startOfType + common), (@CanonicalLocation int caretPos) -> ImmutableList.of(new LexCompletion(startOfType, dataType.toString() ))));
                }
            }
            if (matchedType)
                continue nextToken;
            
            if (content.charAt(curIndex) == '{')
            {
                @SuppressWarnings("units")
                @RawInputLocation int end = content.indexOf('}', curIndex + 1);
                if (end != -1)
                {
                    UnitLexer unitLexer = new UnitLexer();
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, end), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, end + RawInputLocation.ONE), c -> {});
                    s.append("{");
                    d.append("{");
                    @SuppressWarnings("units")
                    @DisplayLocation int displayOffset = d.getLengthSoFar();
                    saver.addNestedErrors(lexerResult.errors, removedCharacters.map(curIndex + RawInputLocation.ONE), displayOffset);
                    removedCharacters.orShift(lexerResult.removedChars, curIndex + lexerResult.adjustedContent.length());
                    s.append(lexerResult.adjustedContent);
                    d.append(lexerResult.display);
                    s.append("}");
                    d.append("}");
                    curIndex = end + RawInputLocation.ONE;
                }
                else
                {
                    saver.locationRecorder.addErrorAndFixes(removedCharacters.map(curIndex, content), StyledString.s("Unit lacks closing }"), ImmutableList.of());
                    UnitLexer unitLexer = new UnitLexer();
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, content.length()), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, content), c -> {});
                    s.append(content.substring(curIndex));
                    d.append(content.substring(curIndex));
                    curIndex = rawLength(content);
                }
                continue nextToken;
            }

            @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> parsed = IdentifierUtility.consumeExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                saver.saveOperand(new IdentTypeExpression(parsed.getFirst()), removedCharacters.map(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                s.append(parsed.getFirst());
                d.append(parsed.getFirst());
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidIdentTypeExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            s.append(content.charAt(curIndex));
            d.append("" + content.charAt(curIndex));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded TypeExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        
        if (content.isEmpty())
        {
            ImmutableList.Builder<LexCompletion> emptyCompletions = ImmutableList.builder();
            for (DataType dataType : Utility.<DataType>iterableStream(streamDataTypes()))
            {
                emptyCompletions.add(new LexCompletion(CanonicalLocation.ZERO, dataType.toString()));
            }
            ImmutableList<LexCompletion> built = emptyCompletions.build();
            autoCompletes.add(new AutoCompleteDetails<>(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), (caretPos) -> built));
        }
        
        @SuppressWarnings("units")
        ArrayList<CaretPos> caretPositions = new ArrayList<>(IntStream.range(0, content.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList()));
        if (caretPositions.isEmpty())
            caretPositions.add(new CaretPos(CanonicalLocation.ZERO, DisplayLocation.ZERO));
        StyledString built = d.build();
        if (built.getLength() == 0)
            built = StyledString.s(" ");
        ImmutableList<ErrorDetails> errors = saver.getErrors();
        built = padZeroWidthErrors(built, caretPositions, errors);
        return new LexerResult<>(saved, s.toString(), removedCharacters, false, ImmutableList.copyOf(caretPositions), built, errors, autoCompletes.build(), new BitSet(), !saver.hasUnmatchedBrackets());
    }

    private Stream<DataType> streamDataTypes()
    {
        return Stream.<DataType>concat(Stream.<DataType>of(DataType.NUMBER, DataType.TEXT, DataType.BOOLEAN), Arrays.stream(DateTimeType.values()).<DataType>map(t -> DataType.date(new DateTimeInfo(t))));
    }
}
