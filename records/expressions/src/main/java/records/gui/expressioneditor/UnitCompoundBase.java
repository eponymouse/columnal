package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.scene.control.Label;
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
    public UnitCompoundBase(EEDisplayNodeParent parent, boolean topLevel, @Nullable Stream<SingleLoader<UnitExpression, UnitSaver>> startContent)
    {
        super(UNIT_OPS, parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), "unit-compound", startContent != null ? startContent : Stream.of(UnitEntry.load("")));
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
    public @Recorded UnitExpression save()
    {
        UnitSaver unitSaver = new UnitSaver() {

            @Override
            public <EXPRESSION> void recordError(EXPRESSION src, StyledString error)
            {
                
            }

            @Override
            public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
            {

            }

            @Override
            @SuppressWarnings("recorded")
            public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
            {
                return typeExp;
            }
        };
        for (ConsecutiveChild<UnitExpression, UnitSaver> child : children)
        {
            child.save(unitSaver);
        }
        return unitSaver.finish(this);
    }
}
