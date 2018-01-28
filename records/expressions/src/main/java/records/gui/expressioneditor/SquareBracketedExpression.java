package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.List;

public class SquareBracketedExpression extends BracketedExpression
{
    public SquareBracketedExpression(OperandOps<Expression, ExpressionNodeParent> operations, ConsecutiveBase<Expression, ExpressionNodeParent> parent, @Nullable ConsecutiveStartContent<Expression, ExpressionNodeParent> content)
    {
        super(operations, parent, new Label("["), new Label("]"), content, ']');
    }

    @Override
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_SQUARE_BRACKETED;
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayers, ErrorAndTypeRecorder onError, OperandNode<@NonNull Expression, ExpressionNodeParent> first, OperandNode<@NonNull Expression, ExpressionNodeParent> last)
    {
        int firstIndex = operands.indexOf(first);
        int lastIndex = operands.indexOf(last);
        boolean roundBracketed = false;
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = operands.size() - 1;
        }
        // May be because it was -1, or just those values were passed directly:
        if (firstIndex == 0 && lastIndex == operands.size() - 1)
        {
            Pair<Boolean, List<String>> opsValid = getOperators(firstIndex, lastIndex);
            if (!opsValid.getFirst())
                return errorDisplayers.record(this, new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last))));
            List<String> ops = opsValid.getSecond();
            if (ops.stream().allMatch(s -> s.equals(",")))
            {
                // Easy; just return this as an array:
                return errorDisplayers.record(this, new ArrayExpression(ImmutableList.copyOf(Utility.<OperandNode<@NonNull Expression, ExpressionNodeParent>, @NonNull Expression>mapList(operands, (OperandNode<@NonNull Expression, ExpressionNodeParent> n) -> n.save(errorDisplayers, onError)))));
            }
            else if (ops.stream().anyMatch(s -> s.equals(",")))
            {
                // Mixed operators; return singleton of unfinished
                return errorDisplayers.record(this, new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last))));
            }
            else
            {
                // No commas, just a singleton:
                return errorDisplayers.record(this, new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last))));
            }
        }
        else
        {
            // Only part of a list:
            return super.save(errorDisplayers, onError, first, last);
        }
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }
}
