package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.UnitExpression;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;

public class UnitCompound extends UnitCompoundBase implements OperandNode<UnitExpression, UnitNodeParent>
{
    private final ConsecutiveBase<UnitExpression, UnitNodeParent> unitParent;

    public UnitCompound(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel, @Nullable ConsecutiveStartContent<UnitExpression, UnitNodeParent> startContent)
    {
        super(parent, topLevel, startContent);
        this.unitParent = parent;
    }

    @Override
    public ConsecutiveBase<UnitExpression, UnitNodeParent> getParent()
    {
        return unitParent;
    }

    @Override
    public <C extends LoadableExpression<C, ?>> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType)
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

    @Override
    public UnitExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.recordUnit(this, saveUnrecorded(errorDisplayer, onError));
    }
}
