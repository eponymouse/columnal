package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public abstract class PatternMatchNode implements ExpressionParent, OperandNode
{
    private final Consecutive source;
    private final ObservableList<ClauseNode> clauses;
    private ExpressionParent parent;
    private ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;

    @SuppressWarnings("initialization")
    public PatternMatchNode(ExpressionParent parent)
    {
        this.parent = parent;
        this.source = new Consecutive(this, c -> new Label("match("), new Label(") with")).prompt("expression");
        this.clauses = FXCollections.observableArrayList();
        this.nodes = FXCollections.observableArrayList();
        this.childrenNodeListener = c -> {
            updateNodes();
        };
        Utility.listen(clauses, c -> {
            updateNodes();
            updateListeners();
        });
        //clauses.add(new ClauseNode("", this));
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
    public List<Column> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        // They are only asking for parent vars, and we don't affect those
        // ClauseNode takes care of the variables it introduces
        return parent.getAvailableVariables(this);
    }

    @Override
    public TypeManager getTypeManager() throws InternalException
    {
        return parent.getTypeManager();
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void changed(ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(ExpressionNode child)
    {
        if (child == source)
        {
            clauses.get(0).focus(Focus.LEFT);
        }
        else
        {
            int index = clauses.indexOf(child);
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
    public void focusLeftOf(ExpressionNode child)
    {
        if (child == source)
        {
            parent.focusLeftOf(this);
        }
        else
        {
            int index = clauses.indexOf(child);
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

    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        @Nullable Expression sourceExp = source.toExpression(onError);
        boolean allClausesOk = true;
        List<Function<MatchExpression, MatchClause>> clauseExps = new ArrayList<>();
        for (ClauseNode clause : clauses)
        {
            @Nullable Function<MatchExpression, MatchClause> exp = clause.toClauseExpression(onError);
            if (exp != null)
                clauseExps.add(exp);
            else
                allClausesOk = false;
        }
        if (sourceExp != null && allClausesOk)
            return new MatchExpression(sourceExp, clauseExps);
        else
            return null;
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
}
