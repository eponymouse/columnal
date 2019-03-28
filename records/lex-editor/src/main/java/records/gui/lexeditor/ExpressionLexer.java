package records.gui.lexeditor;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidIdentExpression;
import utility.Pair;

public class ExpressionLexer implements Lexer<Expression>
{
    private Expression saved = new InvalidIdentExpression("");

    @SuppressWarnings("nullness") // Temporary
    @Override
    public Pair<String, CaretPosMapper> process(String content)
    {
        ExpressionSaver saver = new ExpressionSaver(null, false);
        int curIndex = 0;
        while (curIndex < content.length())
        {
            for (Keyword keyword : Keyword.values())
            {
                if (content.startsWith(keyword.getContent(), curIndex))
                {
                    saver.saveKeyword(keyword, null, c -> {});
                    curIndex += keyword.getContent().length();
                    continue;
                }
            }
            for (Op op : Op.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, null, c -> {});
                    curIndex += op.getContent().length();
                    continue;
                }
            }
            curIndex += 1;
        }
        @SuppressWarnings("nullness")
        @NonNull ConsecutiveChild<Expression, ExpressionSaver> errorDisplayer = null;
        this.saved = saver.finish(errorDisplayer);
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
