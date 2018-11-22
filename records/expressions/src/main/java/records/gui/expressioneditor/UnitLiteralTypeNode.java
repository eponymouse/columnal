package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

/**
 * A TypeExpression with a unit expression inside.
 */
public final class UnitLiteralTypeNode extends TreeLiteralNode<TypeExpression, TypeSaver>
{
    private final UnitCompoundBase unit;
    
    public UnitLiteralTypeNode(ConsecutiveBase<TypeExpression, TypeSaver> parent, UnitExpression unitExpression)
    {
        super(parent);
        this.unit = new UnitCompoundBase(Utility.later(this), true, FXUtility.mouse(this), unitExpression.loadAsConsecutive(BracketedStatus.TOP_LEVEL));
        updateNodes();
        Utility.later(this).updateListeners();
    }

    @Override
    protected EEDisplayNode getInnerDisplayNode()
    {
        return unit;
    }

    @Override
    public void removeNestedBlanks()
    {
        unit.removeBlanks();
    }

    @Override
    public void save(TypeSaver saver)
    {
        saver.saveOperand(new UnitLiteralTypeExpression(unit.save(saver.isShowingErrors())), this, this, c -> {});
    }

    @Override
    public void unmaskErrors()
    {
        unit.unmaskErrors();
    }

    @Override
    public boolean isFocusPending()
    {
        return false;
    }

    @Override
    public void flushFocusRequest()
    {
        unit.flushFocusRequest();
    }
    
    @Override
    public void setHoverDropLeft(boolean on)
    {
        // TODO
    }

    @Override
    public void focusChanged()
    {
        unit.focusChanged();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return unit._test_getHeaders();
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<TypeExpression, TypeSaver>> quickFixes)
    {
        // TODO
    }

    @Override
    public ImmutableList<ErrorInfo> getErrors()
    {
        return ImmutableList.of();
    }

    @Override
    public void clearAllErrors()
    {
        unit.clearAllErrors();
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        unit.visitLocatable(visitor);
    }

    @Override
    public void bindDisable(BooleanExpression disabledProperty)
    {
        unit.bindDisable(disabledProperty);
    }

    @Override
    public void setSelected(boolean selected, boolean focus, @Nullable FXPlatformRunnable onFocusLost)
    {
        unit.setEntireSelected(selected, focus, onFocusLost);
    }

    @Override
    public boolean isSelectionFocused()
    {
        return unit.isSelectionFocused();
    }
}
