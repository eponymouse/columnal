package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitNodeParent> implements UnitNodeParent
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", new Pair<List<FXPlatformFunction<ConsecutiveBase<UnitExpression, UnitNodeParent>, OperandNode<UnitExpression>>>, List<FXPlatformFunction<ConsecutiveBase<UnitExpression, UnitNodeParent>, OperatorEntry<UnitExpression, UnitNodeParent>>>>(Collections.singletonList((ConsecutiveBase<UnitExpression, UnitNodeParent> p) -> new UnitEntry(p, "")), Collections.emptyList()), topLevel ? '}' : ')');
    }

    @Override
    protected UnitNodeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
    }
}
