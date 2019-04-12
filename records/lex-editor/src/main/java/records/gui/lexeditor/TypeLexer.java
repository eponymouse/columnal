package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TypeLexer implements Lexer<TypeExpression, CodeCompletionContext>
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
    
    @SuppressWarnings("units")
    @Override
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        TypeSaver saver = new TypeSaver();
        boolean prevWasIdent = false;
        int curIndex = 0;
        StringBuilder s = new StringBuilder();
        StyledString.Builder d = new StyledString.Builder();
        BitSet missingSpots = new BitSet();
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
                    missingSpots.set(curIndex);
                }
                prevWasIdent = false;
                curIndex += 1;
                continue nextToken;
            }
            prevWasIdent = false;
            
            for (Keyword bracket : Keyword.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveKeyword(bracket, new Span(curIndex, curIndex + bracket.getContent().length()), c -> {});
                    curIndex += bracket.getContent().length();
                    s.append(bracket.getContent());
                    d.append(bracket.getContent());
                    continue nextToken;
                }
            }
            for (Operator op : Operator.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    curIndex += op.getContent().length();
                    s.append(op.getContent());
                    d.append(op.getContent() + " ");
                    continue nextToken;
                }
            }
            
            // Important to try longest types first:
            for (DataType dataType : Utility.<DataType>iterableStream(Stream.<DataType>concat(Stream.<DataType>of(DataType.NUMBER, DataType.BOOLEAN, DataType.TEXT), Arrays.stream(DateTimeType.values()).<DataType>map(t -> DataType.date(new DateTimeInfo(t)))).sorted(Comparator.comparing(t -> -t.toString().length()))))
            {
                if (content.startsWith(dataType.toString(), curIndex))
                {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), new Span(curIndex, curIndex + dataType.toString().length()), c -> {});
                    curIndex += dataType.toString().length();
                    s.append(dataType.toString());
                    d.append(dataType.toString());
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) == '{')
            {
                int end = content.indexOf('}', curIndex + 1);
                if (end != -1)
                {
                    UnitLexer unitLexer = new UnitLexer();
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, end), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), new Span(curIndex, end + 1), c -> {});
                    s.append("{");
                    d.append("{");
                    s.append(lexerResult.adjustedContent);
                    d.append(lexerResult.display);
                    s.append("}");
                    d.append("}");
                    curIndex = end + 1;
                }
                else
                {
                    saver.locationRecorder.addErrorAndFixes(new Span(curIndex, content.length()), StyledString.s("Unit lacks closing }"), ImmutableList.of());
                    UnitLexer unitLexer = new UnitLexer();
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, content.length()), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), new Span(curIndex, content.length()), c -> {});
                    s.append(content.substring(curIndex));
                    d.append(content.substring(curIndex));
                    curIndex = content.length();
                }
                continue nextToken;
            }

            @Nullable Pair<@ExpressionIdentifier String, Integer> parsed = IdentifierUtility.consumeExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                saver.saveOperand(new IdentTypeExpression(parsed.getFirst()), new Span(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                s.append(parsed.getFirst());
                d.append(parsed.getFirst());
                continue nextToken;
            }

            Span invalidCharLocation = new Span(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidIdentTypeExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            s.append(content.charAt(curIndex));
            d.append("" + content.charAt(curIndex));
            
            curIndex += 1;
        }
        @Recorded TypeExpression saved = saver.finish(new Span(curIndex, curIndex));
        @SuppressWarnings("units")
        ImmutableList<CaretPos> caretPositions = IntStream.range(0, content.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList());
        if (caretPositions.isEmpty())
            caretPositions = ImmutableList.of(new CaretPos(0, 0));
        StyledString built = d.build();
        if (built.getLength() == 0)
            built = StyledString.s(" ");
        return new LexerResult<>(saved, s.toString(), missingSpots, false, caretPositions, built, saver.getErrors(), ImmutableList.of(), new BitSet(), !saver.hasUnmatchedBrackets());
    }
}
