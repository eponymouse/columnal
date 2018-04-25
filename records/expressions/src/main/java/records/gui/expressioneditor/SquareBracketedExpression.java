package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
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
    public SquareBracketedExpression(ConsecutiveBase<Expression, ExpressionNodeParent> parent, @Nullable ConsecutiveStartContent<Expression, ExpressionNodeParent> content)
    {
        super(parent, new Label("["), new Label("]"), content, ']');
    }

    @Override
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_SQUARE_BRACKETED;
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }
}
