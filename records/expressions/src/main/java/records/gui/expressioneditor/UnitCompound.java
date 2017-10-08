package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.UnitExpression;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;

public class UnitCompound extends UnitCompoundBase implements OperandNode<UnitExpression>
{
    private final ConsecutiveBase<UnitExpression, UnitNodeParent> unitParent;

    public UnitCompound(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        super(parent, topLevel);
        this.unitParent = parent;
    }

    @Override
    public ConsecutiveBase<UnitExpression, ?> getParent()
    {
        return unitParent;
    }

    @Override
    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.streamNullable(ConsecutiveChild.closestDropSingle(this, operations.getOperandClass(), nodes().get(0), loc, forType), super.findClosestDrop(loc, forType)).min(Comparator.comparing(p -> p.getSecond())).orElse(null);
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }
}
