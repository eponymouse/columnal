package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
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
    public Expression save(ErrorDisplayerRecord errorDisplayers, FXPlatformConsumer<Object> onError, OperandNode<@NonNull Expression> first, OperandNode<@NonNull Expression> last)
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
                return new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last)));
            List<String> ops = opsValid.getSecond();
            if (ops.stream().allMatch(s -> s.equals(",")))
            {
                // Easy; just return this as an array:
                return new ArrayExpression(ImmutableList.copyOf(Utility.<OperandNode<@NonNull Expression>, @NonNull Expression>mapList(operands, (OperandNode<@NonNull Expression> n) -> n.save(errorDisplayers, onError))));
            }
            else if (ops.stream().anyMatch(s -> s.equals(",")))
            {
                // Mixed operators; return singleton of unfinished
                return new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last)));
            }
            else
            {
                // No commas, just a singleton:
                return new ArrayExpression(ImmutableList.of(super.save(errorDisplayers, onError, first, last)));
            }
        }
        else
        {
            // Only part of a list:
            return super.save(errorDisplayers, onError, first, last);
        }
    }
}
