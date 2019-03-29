package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.SourceLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;

import java.util.Optional;

public class ExpressionLexer implements Lexer<Expression>
{
    private Expression saved = new InvalidIdentExpression("");

    @SuppressWarnings("units")
    @Override
    public Pair<String, CaretPosMapper> process(String content)
    {
        ExpressionSaver saver = new ExpressionSaver();
        @SourceLocation int curIndex = 0;
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                curIndex += 1;
                continue nextToken;
            }
            
            for (Keyword keyword : Keyword.values())
            {
                if (content.startsWith(keyword.getContent(), curIndex))
                {
                    saver.saveKeyword(keyword, new Span(curIndex, curIndex + keyword.getContent().length()), c -> {});
                    curIndex += keyword.getContent().length();
                    continue nextToken;
                }
            }
            for (Op op : Op.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    curIndex += op.getContent().length();
                    continue nextToken;
                }
            }
            
            if (content.startsWith("\"", curIndex))
            {
                // Consume string until next quote:
                int endQuote = content.indexOf("\"", curIndex + 1);
                if (endQuote != -1)
                {
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, endQuote), false)), new Span(curIndex, endQuote + 1), c -> {});
                    curIndex = endQuote + 1;
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
            {
                @SourceLocation int numberStart = curIndex;
                // Before dot:
                do
                {
                    curIndex += 1;
                }
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                
                if (curIndex < content.length() && content.charAt(curIndex) == '.')
                {
                    do
                    {
                        curIndex += 1;
                    }
                    while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                }
                Optional<@Value Number> number = Utility.parseNumberOpt(content.substring(numberStart, curIndex));
                if (number.isPresent())
                {
                    saver.saveOperand(new NumericLiteral(number.get(), null), new Span(numberStart, curIndex), c -> {});
                    continue nextToken;
                }
            }
            
            @Nullable Pair<@ExpressionIdentifier String, Integer> parsed = IdentifierUtility.consumeExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new IdentExpression(parsed.getFirst()), new Span(curIndex, parsed.getSecond()), c -> {});
                curIndex = parsed.getSecond();
                continue;
            }
            
            
            curIndex += 1;
        }
        this.saved = saver.finish(new Span(curIndex, curIndex));
        // TODO also display the errors
        return new Pair<>(content, i -> i);
    }

    @Override
    public int[] getCaretPositions()
    {
        return new int[] {0}; // TODO
    }

    @Override
    public Expression getSaved()
    {
        return saved;
    }
}
