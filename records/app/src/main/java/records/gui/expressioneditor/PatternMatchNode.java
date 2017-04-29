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
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;
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
public class PatternMatchNode implements ExpressionParent, OperandNode
{
    private final VBox matchLabel;
    private final ConsecutiveBase source;
    private final ObservableList<ClauseNode> clauses;
    private ConsecutiveBase parent;
    private ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;

    @SuppressWarnings("initialization") // Because we pass this as the parent
    public PatternMatchNode(ConsecutiveBase parent, @Nullable Pair<Expression, List<MatchClause>> sourceAndClauses)
    {
        this.parent = parent;
        this.matchLabel = ExpressionEditorUtil.keyword("match", "match", this, getParentStyles());
        this.source = new Consecutive(this, matchLabel, null, "match", sourceAndClauses == null ? null : sourceAndClauses.getFirst().loadAsConsecutive()).prompt("expression");
        this.clauses = FXCollections.observableArrayList();
        this.nodes = FXCollections.observableArrayList();
        this.childrenNodeListener = c -> {
            updateNodes();
        };
        FXUtility.listen(clauses, c -> {
            updateNodes();
            updateListeners();
        });
        if (sourceAndClauses == null)
            clauses.add(new ClauseNode(this, null));
        else
            clauses.addAll(Utility.mapList(sourceAndClauses.getSecond(), c -> c.load(this)));
    }

    private void updateNodes()
    {
        nodes.setAll(Stream.concat(source.nodes().stream(), clauses.stream().flatMap(c -> c.nodes().stream())).collect(Collectors.<Node>toList()));
    }

    private void updateListeners()
    {
        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:
        for (ClauseNode child : clauses)
        {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(child) == null)
                child.nodes().addListener(childrenNodeListener);
            listeningTo.put(child, true);
        }
        // Stop listening to old:
        for (Iterator<Entry<ExpressionNode, Boolean>> iterator = listeningTo.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<ExpressionNode, Boolean> e = iterator.next();
            if (e.getValue() == false)
            {
                e.getKey().nodes().removeListener(childrenNodeListener);
                iterator.remove();
            }
        }

        parent.changed(this);
    }


    // Gets the outcome type
    //@Override
    //public @Nullable DataType getType(ExpressionNode child)
    //{
        //return parent.getType(this);
    //}

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException
    {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        // They are only asking for parent vars, and we don't affect those
        // ClauseNode takes care of the variables it introduces
        return parent.getAvailableVariables(this);
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
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
    public void focusLeftOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
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
    public ObservableList<Node> nodes()
    {
        return nodes;
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
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        // Ignore
        return this;
    }

    public Expression toExpression(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        Expression sourceExp = source.toExpression(errorDisplayer, onError);
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

    public PatternMatchNode focusWhenShown()
    {
        source.focusWhenShown();
        return this;
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
    public ConsecutiveBase getParent()
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
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        Pair<ConsecutiveChild, Double> startDist = new Pair<>(this, FXUtility.distanceToLeft(matchLabel, loc));

        return Stream.concat(Stream.of(startDist), clauses.stream().map(c -> c.findClosestDrop(loc)))
            .min(Comparator.comparing(Pair::getSecond)).get();
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
}
