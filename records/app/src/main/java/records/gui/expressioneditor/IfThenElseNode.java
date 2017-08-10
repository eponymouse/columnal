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
public class IfThenElseNode implements OperandNode<Expression>, ExpressionParent, ErrorDisplayer
{
    private final ConsecutiveBase<Expression> parent;
    private final ObservableList<Node> nodes;
    private final @Interned Consecutive<Expression> condition;
    private final @Interned Consecutive<Expression> thenPart;
    private final @Interned Consecutive<Expression> elsePart;
    private final VBox ifLabel;
    private final VBox thenLabel;
    private final VBox elseLabel;

    @SuppressWarnings("initialization") // because of Consecutive
    public IfThenElseNode(ConsecutiveBase<Expression> parent)
    {
        this.parent = parent;
        nodes = FXCollections.observableArrayList();

        ifLabel = ExpressionEditorUtil.keyword("if", "if-keyword", this, getParentStyles());
        thenLabel = ExpressionEditorUtil.keyword("then", "if-keyword", this, getParentStyles());
        elseLabel = ExpressionEditorUtil.keyword("else", "if-keyword", this, getParentStyles());

        condition = new @Interned Consecutive<>(ConsecutiveBase.EXPRESSION_OPS, this, ifLabel, null, "if-condition", null);
        thenPart = new @Interned Consecutive<>(ConsecutiveBase.EXPRESSION_OPS, this, thenLabel, null, "if-then", null);
        elsePart = new @Interned Consecutive<>(ConsecutiveBase.EXPRESSION_OPS, this, elseLabel, null, "if-else", null);

        FXUtility.listen(condition.nodes(), c -> updateNodes());
        FXUtility.listen(thenPart.nodes(), c -> updateNodes());
        FXUtility.listen(elsePart.nodes(), c -> updateNodes());

        updateNodes();
    }

    @OnThread(Tag.FXPlatform)
    private void updateNodes()
    {
        List<Node> r = new ArrayList<>();
        r.addAll(condition.nodes());
        r.addAll(thenPart.nodes());
        r.addAll(elsePart.nodes());
        nodes.setAll(r);
    }

    @Override
    public ConsecutiveBase<Expression> getParent()
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
    public ObservableList<Node> nodes()
    {
        return nodes;
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
    public @Nullable DataType inferType()
    {
        // TODO
        return null;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        // Not applicable
        return this;
    }

    @Override
    public Expression save(ErrorDisplayerRecord<Expression> errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        return errorDisplayer.record(this, new IfThenElseExpression(condition.save(errorDisplayer, onError), thenPart.save(errorDisplayer, onError), elsePart.save(errorDisplayer, onError)));
    }

    @Override
    public OperandNode<Expression> focusWhenShown()
    {
        condition.focusWhenShown();
        return this;
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
    public List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException
    {
        if (child == condition)
            return Collections.singletonList(new Pair<>(DataType.BOOLEAN, Collections.emptyList()));
        else
            return Collections.emptyList(); // TODO: could infer from it other branch
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        if (child == condition)
            thenPart.focus(Focus.LEFT);
        else if (child == thenPart)
            elsePart.focus(Focus.LEFT);
        else
            parent.focusRightOf(this);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
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
}
