package records.gui.expressioneditor;

import javafx.scene.control.Label;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitCompound extends Consecutive<UnitExpression, Void> implements OperandNode<UnitExpression>
{
    public UnitCompound(EEDisplayNodeParent parent, boolean topLevel)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", new Pair<List<FXPlatformFunction<ConsecutiveBase<UnitExpression, ExpressionNodeParent>, OperandNode<UnitExpression>>>, List<FXPlatformFunction<ConsecutiveBase<UnitExpression, ExpressionNodeParent>, OperatorEntry<UnitExpression>>>>(Collections.singletonList((ConsecutiveBase<UnitExpression, ExpressionNodeParent> p) -> new UnitEntry(p, "")), Collections.emptyList()), topLevel ? "}" : ")");
    }
}
