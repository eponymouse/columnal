package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.SingleLoader;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The node representing the top-level of a pattern match expression.
 */
public class PatternMatchNode extends DeepNodeTree implements EEDisplayNodeParent, OperandNode<Expression>, ExpressionNodeParent
{
    private final VBox matchLabel;
    private final ConsecutiveBase<Expression, ExpressionNodeParent> source;
    private final ObservableList<ClauseNode> clauses;
    private ConsecutiveBase<Expression, ExpressionNodeParent> parent;

    @SuppressWarnings("initialization") // Because we pass this as the parent
    public PatternMatchNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, @Nullable Pair<Expression, List<MatchClause>> sourceAndClauses)
    {
        this.parent = parent;
        this.matchLabel = ExpressionEditorUtil.keyword("match", "match", this, getParentStyles());
        this.source = new Consecutive<Expression, ExpressionNodeParent>(ConsecutiveBase.EXPRESSION_OPS, this, matchLabel, null, "match", sourceAndClauses == null ? null : SingleLoader.withSemanticParent(sourceAndClauses.getFirst().loadAsConsecutive(), this), ')') {
            @Override
            public boolean isFocused()
            {
                return childIsFocused();
            }

            @Override
            protected ExpressionNodeParent getThisAsSemanticParent()
            {
                return PatternMatchNode.this;
            }

            { prompt("expression"); }
        };
        this.clauses = FXCollections.observableArrayList();
        listenToNodeRelevantList(clauses);
        if (sourceAndClauses == null)
            clauses.add(new ClauseNode(this, null));
        else
            clauses.addAll(Utility.<MatchClause, ClauseNode>mapList(sourceAndClauses.getSecond(), c -> c.load(this)));
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.<Node>concat(source.nodes().stream(), clauses.stream().flatMap(c -> c.nodes().stream()));
    }

    @Override
    protected void updateDisplay()
    {
        parent.changed(this);
    }

    @Override
    protected Stream<@NonNull EEDisplayNode> calculateChildren()
    {
        return Stream.<@NonNull EEDisplayNode>concat(Stream.of(source), clauses.stream());
    }


    // Gets the outcome type
    //@Override
    //public @Nullable DataType getType(EEDisplayNode child)
    //{
        //return parent.getType(this);
    //}

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(EEDisplayNode child)
    {
        // They are only asking for parent vars, and we don't affect those
        // ClauseNode takes care of the variables it introduces
        return parent.getThisAsSemanticParent().getAvailableVariables(this);
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == source)
        {
            clauses.get(0).focus(Focus.LEFT);
        }
        else
        {
            int index = Utility.indexOfRef(clauses, (ClauseNode)child);
            if (index != -1)
            {
                if (index < clauses.size() - 1)
                    clauses.get(index + 1).focus(Focus.LEFT);
                else
                    parent.focusRightOf(this);
            }
        }
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == source)
        {
            parent.focusLeftOf(this);
        }
        else
        {
            int index = Utility.indexOfRef(clauses, (ClauseNode)child);
            if (index != -1)
            {
                if (index > 0 )
                    clauses.get(index - 1).focus(Focus.LEFT);
                else
                    source.focus(Focus.RIGHT);
            }
        }
    }

    @Override
    public Stream<String> getParentStyles()
    {
        // Added in the consecutive, not here:
        return parent.getParentStyles();
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            source.focus(side);
        else
            clauses.get(clauses.size() - 1).focus(side);
    }

    @Override
    public void prompt(String prompt)
    {
        // Ignore
    }

    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        Expression sourceExp = source.save(errorDisplayer, onError);
        List<Function<MatchExpression, MatchClause>> clauseExps = new ArrayList<>();
        for (ClauseNode clause : clauses)
        {
            Function<MatchExpression, MatchClause> exp = clause.toClauseExpression(errorDisplayer, onError);
            clauseExps.add(exp);
        }
        return errorDisplayer.record(this, new MatchExpression(sourceExp, clauseExps));
    }

    public @Nullable DataType getMatchType()
    {
        return source.inferType();
    }

    @Override
    public void focusWhenShown()
    {
        source.focusWhenShown();
    }

    @SuppressWarnings("nullness") // Because we return wrapper which can't be null
    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return new ReadOnlyStringWrapper("match-inner");
    }

    @Override
    public boolean isFocused()
    {
        return source.childIsFocused() || clauses.stream().anyMatch(c -> c.isFocused());
    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        source.setSelected(selected);
        for (ClauseNode clause : clauses)
        {
            clause.setSelected(selected);
        }
    }

    @Override
    public <C> Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        @Nullable Pair<ConsecutiveChild<? extends C>, Double> startDist = ConsecutiveChild.closestDropSingle(this, Expression.class, matchLabel, loc, forType);

        return Stream.<Pair<ConsecutiveChild<? extends C>, Double>>concat(Utility.streamNullable(startDist), clauses.stream().flatMap(c -> Utility.streamNullable(c.findClosestDrop(loc, forType))))
            .min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        //TODO
    }

    @Override
    public void focusChanged()
    {
        source.focusChanged();
        for (ClauseNode clause : clauses)
        {
            clause.focusChanged();
        }
    }

    @Override
    public void showError(String error, List<ErrorRecorder.QuickFix> quickFixes)
    {
        source.showError(error, quickFixes);
    }

    public ClauseNode addNewCaseToRightOf(ClauseNode clause)
    {
        int index = Utility.indexOfRef(clauses, clause);
        if (index == -1)
            index = clauses.size();
        else
            index += 1;
        ClauseNode newClause = new ClauseNode(this, null);
        clauses.add(newClause);
        return newClause;
    }
}
