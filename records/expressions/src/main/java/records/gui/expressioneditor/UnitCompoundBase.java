package records.gui.expressioneditor;

import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnitExpression;

import java.util.Collections;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitNodeParent> implements UnitNodeParent
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel, @Nullable ConsecutiveStartContent<UnitExpression, UnitNodeParent> startContent)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", startContent != null ? startContent : new ConsecutiveStartContent<UnitExpression, UnitNodeParent>(Collections.singletonList((ConsecutiveBase<UnitExpression, UnitNodeParent> p) -> new UnitEntry(p, "", false)), Collections.emptyList()), topLevel ? '}' : ')');
    }

    @Override
    public UnitNodeParent getThisAsSemanticParent()
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
