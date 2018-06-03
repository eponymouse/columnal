package records.gui.expressioneditor;

import records.data.unit.UnitManager;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
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
public class UnitLiteralNode extends OtherLiteralNode
{
    private final UnitCompoundBase unit;
    
    public UnitLiteralNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, UnitExpression unitExpression)
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
    protected ErrorDisplayer<?, ?> getInnerErrorDisplayer()
    {
        return unit;
    }

    @Override
    public void save(ExpressionNodeParent saver)
    {
        saver.saveOperand(new UnitLiteralExpression(unit), this, c -> {});
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
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression, ExpressionNodeParent>> quickFixes)
    {
        // TODO
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        unit.visitLocatable(visitor);
    }

    @Override
    public UnitManager getUnitManager()
    {
        return getEditor().getTypeManager().getUnitManager();
    }
}
