package records.gui.expressioneditor;

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

import java.util.List;
import java.util.stream.Stream;

/**
 * An expression with a unit expression inside.
 */
public class UnitLiteralExpressionNode extends OtherLiteralNode<Expression, ExpressionSaver>
{
    private final UnitCompoundBase unit;
    
    public UnitLiteralExpressionNode(ConsecutiveBase<Expression, ExpressionSaver> parent, UnitExpression unitExpression)
    {
        super(parent);
        this.unit = new UnitCompoundBase(Utility.later(this), true, unitExpression.loadAsConsecutive(BracketedStatus.TOP_LEVEL));
        updateNodes();
        Utility.later(this).updateListeners();
    }

    @Override
    protected EEDisplayNode getInnerDisplayNode()
    {
        return unit;
    }

    @Override
    public void save(ExpressionSaver saver)
    {
        saver.saveOperand(new UnitLiteralExpression(unit.save()), this, this, c -> {});
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
    public void setSelected(boolean selected)
    {
        // TODO
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
    public void clearAllErrors()
    {
        unit.clearAllErrors();
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        unit.visitLocatable(visitor);
    }
}
