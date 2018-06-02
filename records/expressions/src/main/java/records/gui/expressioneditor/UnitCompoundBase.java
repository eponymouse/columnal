package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnitExpression;

import java.util.Collections;
import java.util.List;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitNodeParent> implements UnitNodeParent
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel, @Nullable List<SingleLoader<UnitExpression, UnitNodeParent>> startContent)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", startContent != null ? startContent : ImmutableList.of((p, s) -> new UnitEntry(p, "", false)), topLevel ? '}' : ')');
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
