package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.UnitExpression;
import styled.StyledShowable;
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
    public void visitLocatable(LocatableVisitor visitor)
    {
        super.visitLocatable(visitor);
        visitor.register(this, operations.getOperandClass());
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
    public @Recorded UnitExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.recordUnit(this, saveUnrecorded(errorDisplayer, onError));
    }
}
