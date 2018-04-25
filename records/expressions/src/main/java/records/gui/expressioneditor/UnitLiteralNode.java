package records.gui.expressioneditor;

import javafx.scene.Node;
import records.data.unit.UnitManager;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitLiteralExpression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

public class UnitLiteralNode extends OtherLiteralNode implements UnitNodeParent
{
    private final UnitCompoundBase unit;
    
    public UnitLiteralNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, UnitExpression unitExpression)
    {
        super(parent);
        this.unit = new UnitCompoundBase(Utility.later(this), true, withSemanticParent(unitExpression.loadAsConsecutive(false), Utility.later(this)));
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
    public void prompt(String prompt)
    {
        unit.prompt(prompt);
    }

    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return new UnitLiteralExpression(errorDisplayer.recordUnit(unit, unit.saveUnrecorded(errorDisplayer, onError)));
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
