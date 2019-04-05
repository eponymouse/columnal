package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledCSS;
import styled.StyledString;
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
    @SuppressWarnings("units")
    @Override
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        TypeSaver saver = new TypeSaver();
        boolean prevWasIdent = false;
        int curIndex = 0;
        StringBuilder s = new StringBuilder();
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
            
            // Important to try longest types first:
            for (DataType dataType : Utility.<DataType>iterableStream(Stream.<DataType>concat(Stream.<DataType>of(DataType.NUMBER, DataType.BOOLEAN, DataType.TEXT), Arrays.stream(DateTimeType.values()).<DataType>map(t -> DataType.date(new DateTimeInfo(t)))).sorted(Comparator.comparing(t -> -t.toString().length()))))
            {
                if (content.startsWith(dataType.toString(), curIndex))
                {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), new Span(curIndex, curIndex + dataType.toString().length()), c -> {});
                    curIndex += dataType.toString().length();
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
                    curIndex = end + 1;
                }
                else
                {
                    saver.locationRecorder.addErrorAndFixes(new Span(curIndex, content.length()), StyledString.s("Unit lacks closing }"), ImmutableList.of());
                    UnitLexer unitLexer = new UnitLexer();
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, content.length()), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), new Span(curIndex, content.length()), c -> {});
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
                continue nextToken;
            }

            Span invalidCharLocation = new Span(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidIdentTypeExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter.start", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            
            curIndex += 1;
        }
        @Recorded TypeExpression saved = saver.finish(new Span(curIndex, curIndex));
        return new LexerResult<>(saved, content, i -> i, false, IntStream.range(0, content.length() + 1).toArray(), StyledString.s(content), i -> i, i -> i, saver.getErrors(), ImmutableList.of(), new BitSet(), !saver.hasUnmatchedBrackets());
    }
}
