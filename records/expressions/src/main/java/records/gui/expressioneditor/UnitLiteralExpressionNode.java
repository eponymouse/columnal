package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import log.Log;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitLiteralExpression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An expression with a unit expression inside.
 */
public final class UnitLiteralExpressionNode extends TreeLiteralNode<Expression, ExpressionSaver>
{
    private final UnitCompoundBase unit;
    
    public UnitLiteralExpressionNode(ConsecutiveBase<Expression, ExpressionSaver> parent, UnitExpression unitExpression)
    {
        super(parent);
        this.unit = new UnitCompoundBase(Utility.later(this), true, FXUtility.mouse(this), unitExpression.loadAsConsecutive(BracketedStatus.TOP_LEVEL));
        updateNodes();
        updateListeners();
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
    public void save(ExpressionSaver saver)
    {
        @Recorded UnitExpression unitExpression = unit.save();

        //Log.debug("Saved as: " + unitExpression);
        //Log.debug("  From:\n      " + unit.children.stream().map(c -> (c instanceof EntryNode) ? ((EntryNode)c).textField.getText() : "£" + c.getClass()).collect(Collectors.joining("\n      ")));
        
        saver.saveOperand(new UnitLiteralExpression(unitExpression), this, this, c -> {});
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
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression, ExpressionSaver>> quickFixes)
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
}
