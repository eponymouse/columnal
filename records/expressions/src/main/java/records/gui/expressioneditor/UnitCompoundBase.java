package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.scene.control.Label;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;

import java.util.List;
import java.util.stream.Stream;

public class UnitCompoundBase extends Consecutive<UnitExpression, UnitSaver>
{
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel, ConsecutiveChild<?, ?> selectable, @Nullable Stream<SingleLoader<UnitExpression, UnitSaver>> startContent)
    {
        super(UNIT_OPS, parent, new PrefixSuffix(topLevel ? "{" : "(", topLevel ? "}" : ")", selectable    ), "unit-compound", startContent != null ? startContent : Stream.of(UnitEntry.load("")));
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
    public @Recorded UnitExpression save(boolean showErrors)
    {
        UnitSaver unitSaver = new UnitSaver(this, showErrors);
        for (ConsecutiveChild<UnitExpression, UnitSaver> child : children)
        {
            child.save(unitSaver);
        }
        return unitSaver.finish(children.get(children.size() - 1));
    }

    @Override
    public boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<UnitExpression, UnitSaver> child)
    {
        // They'll need to enter the '}' to close anyway:
        return true;
    }

    @Override
    public DataFormat getClipboardType()
    {
        return UnitEditor.UNIT_CLIPBOARD_TYPE;
    }
}
