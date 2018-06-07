package records.gui.expressioneditor;

import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.UnitEntry.UnitText;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;

import java.util.stream.Stream;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitSaver>
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel, @Nullable Stream<SingleLoader<UnitExpression, UnitSaver>> startContent)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", startContent != null ? startContent : Stream.of(UnitEntry.load(new UnitText(""))), topLevel ? '}' : ')');
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
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

    @Override
    public UnitExpression save()
    {        
        return new SingleUnitExpression("TODO");
    }
}
