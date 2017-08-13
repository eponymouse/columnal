package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 21/02/2017.
 */
public class IfThenElseNode extends DeepNodeTree implements OperandNode<Expression>, EEDisplayNodeParent, ErrorDisplayer, ExpressionNodeParent
{
    private final ConsecutiveBase<Expression, ExpressionNodeParent> parent;
    private final ExpressionNodeParent semanticParent;
    private final @Interned Consecutive<Expression, ExpressionNodeParent> condition;
    private final @Interned Consecutive<Expression, ExpressionNodeParent> thenPart;
    private final @Interned Consecutive<Expression, ExpressionNodeParent> elsePart;
    private final VBox ifLabel;
    private final VBox thenLabel;
    private final VBox elseLabel;

    @SuppressWarnings("initialization") // because of Consecutive
    public IfThenElseNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent)
    {
        this.parent = parent;
        this.semanticParent = semanticParent;

        ifLabel = ExpressionEditorUtil.keyword("if", "if-keyword", this, getParentStyles());
        thenLabel = ExpressionEditorUtil.keyword("then", "if-keyword", this, getParentStyles());
        elseLabel = ExpressionEditorUtil.keyword("else", "if-keyword", this, getParentStyles());

        condition = new SubConsecutive(ifLabel, "if-condition");
        thenPart = new SubConsecutive(thenLabel, "if-then");
        elsePart = new SubConsecutive(elseLabel, "if-else");

        updateNodes();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.of(condition, thenPart, elsePart);
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(condition, thenPart, elsePart).flatMap(n -> n.nodes().stream());
    }

    @Override
    protected void updateDisplay()
    {

    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        //TODO
    }

    @Override
    public <C> Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        @Nullable Pair<ConsecutiveChild<? extends C>, Double> startDist = ConsecutiveChild.closestDropSingle(this, Expression.class, ifLabel, loc, forType);

        return Utility.streamNullable(startDist, condition.findClosestDrop(loc, forType), thenPart.findClosestDrop(loc, forType), elsePart.findClosestDrop(loc, forType))
            .filter(x -> x != null).min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        //TODO
    }

    @Override
    public void focusChanged()
    {
        condition.focusChanged();
        thenPart.focusChanged();
        elsePart.focusChanged();
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            condition.focus(Focus.LEFT);
        else
            elsePart.focus(Focus.RIGHT);
    }

    @Override
    public void prompt(String prompt)
    {
        // Not applicable
    }

    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        return errorDisplayer.record(this, new IfThenElseExpression(condition.save(errorDisplayer, onError), thenPart.save(errorDisplayer, onError), elsePart.save(errorDisplayer, onError)));
    }

    @Override
    public void focusWhenShown()
    {
        condition.focusWhenShown();
    }

    @Override
    @SuppressWarnings("nullness") // Because we return non-null item
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return new ReadOnlyStringWrapper("if-inner");
    }

    @Override
    public boolean isFocused()
    {
        return condition.childIsFocused() || thenPart.childIsFocused() || elsePart.childIsFocused();
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        if (child == condition)
            return Collections.singletonList(new Pair<>(DataType.BOOLEAN, Collections.emptyList()));
        else
            return Collections.emptyList(); // TODO: could infer from it other branch
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(EEDisplayNode child)
    {
        return semanticParent.getAvailableVariables(this);
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == condition)
            thenPart.focus(Focus.LEFT);
        else if (child == thenPart)
            elsePart.focus(Focus.LEFT);
        else
            parent.focusRightOf(this);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == condition)
            parent.focusLeftOf(this);
        else if (child == thenPart)
            condition.focus(Focus.RIGHT);
        else
            thenPart.focus(Focus.RIGHT);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.of("if-parent"));
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    @Override
    public void showError(String error, List<ErrorRecorder.QuickFix> quickFixes)
    {
        condition.showError(error, quickFixes);
    }

    private class SubConsecutive extends Consecutive<Expression, ExpressionNodeParent>
    {
        public SubConsecutive(Node label, String style)
        {
            super(ConsecutiveBase.EXPRESSION_OPS, IfThenElseNode.this, label, null, style, null);
        }

        @Override
        protected ExpressionNodeParent getThisAsSemanticParent()
        {
            return IfThenElseNode.this;
        }

        @Override
        public boolean isFocused()
        {
            return childIsFocused();
        }
    }
}
