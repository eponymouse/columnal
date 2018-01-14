package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitNodeParent> implements UnitNodeParent
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", new ConsecutiveStartContent<UnitExpression, UnitNodeParent>(Collections.singletonList((ConsecutiveBase<UnitExpression, UnitNodeParent> p) -> new UnitEntry(p, "", false)), Collections.emptyList()), topLevel ? '}' : ')');
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

    @Override
    public UnitManager getUnitManager()
    {
        return parent.getEditor().getTypeManager().getUnitManager();
    }

    @Override
    public void showType(String type)
    {
        // This shouldn't occur for units
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return true;
    }
}
