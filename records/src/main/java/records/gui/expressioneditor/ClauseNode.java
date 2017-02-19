package records.gui.expressioneditor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.MatchExpression.PatternMatch;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 19/12/2016.
 */
public abstract class ClauseNode implements ExpressionParent, ExpressionNode
{
    private final PatternMatchNode parent;
    private final ObservableList<Pair<ConsecutiveBase, ConsecutiveBase>> matches;
    private final Label mapsTo;
    private final ConsecutiveBase outcome;
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private @MonotonicNonNull ListChangeListener<Node> childrenNodeListener;

    public ClauseNode(String rest, PatternMatchNode parent)
    {
        this.parent = parent;
        this.mapsTo = new Label("\u2794");
        this.mapsTo.getStyleClass().add("mapsto");
        this.matches = FXCollections.observableArrayList();
        this.nodes = FXCollections.observableArrayList();
        // Must initialize outcome first because updateNodes will use it:
        this.outcome = makeConsecutive().prompt("value");
        Utility.listen(matches, c -> {
            updateNodes();
            updateListeners();
        });
        this.matches.add(new Pair<>(makeConsecutive().prompt("pattern"), makeConsecutive().prompt("condition")));

    }

    private void updateListeners(@UnknownInitialization(ClauseNode.class) ClauseNode this)
    {
        if (childrenNodeListener == null)
        {
            this.childrenNodeListener = c -> {
                updateNodes();
            };
        }

        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:
        for (Pair<ConsecutiveBase, ConsecutiveBase> match : matches)
        {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(match.getFirst()) == null)
                match.getFirst().nodes().addListener(childrenNodeListener);
            if (listeningTo.get(match.getSecond()) == null)
                match.getSecond().nodes().addListener(childrenNodeListener);
            listeningTo.put(match.getFirst(), true);
            listeningTo.put(match.getSecond(), true);
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

    private void updateNodes(@UnknownInitialization(ClauseNode.class) ClauseNode this)
    {
        List<Node> childrenNodes = new ArrayList<Node>(Stream.concat(
            matches.stream().flatMap(p -> Stream.concat(p.getFirst().nodes().stream(), p.getSecond().nodes().stream())),
            Stream.concat(Stream.of(mapsTo), outcome.nodes().stream())).collect(Collectors.<Node>toList()));
        nodes.setAll(childrenNodes);
    }

    @SuppressWarnings("initialization") // Because of Consecutive
    private Consecutive makeConsecutive(@UnknownInitialization(Object.class) ClauseNode this)
    {
        return new Consecutive(this, null, null, "clause", null);
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
            matches.get(0).getFirst().focus(side);
        else
            matches.get(matches.size() - 1).getSecond().focus(side);
    }

    /*
    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        if (child == outcome) // Can't predict, unless we consult sibling clauses or go two up
            return parent.getType(this);

        DataType matchedType = parent.getMatchType();
        for (Pair<Consecutive, Consecutive> match : matches)
        {
            if (match.getFirst() == child)
                return matchedType;
            if (match.getSecond() == child)
                return DataType.BOOLEAN; // Guard
        }
        // Not a child of ours!
        return null;
    }*/

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        //TODO union of clause variables or just the clause variable for guard
        // (and always mix in parent variables)
        ArrayList<Pair<String, @Nullable DataType>> vars = new ArrayList<>(parent.getAvailableVariables(this));

        Multimap<@NonNull String, @Nullable DataType> allClauseVars = null;
        for (Pair<ConsecutiveBase, ConsecutiveBase> match : matches)
        {
            if (match.getFirst() == child)
                return vars; // Matching side only has access to parent vars
            List<Pair<String, @Nullable DataType>> newMatchVars = match.getFirst().getDeclaredVariables();
            if (match.getSecond() == child)
            {
                vars.addAll(newMatchVars);
                return vars;
            }
            // Otherwise keep intersection of vars from different clauses:
            if (allClauseVars == null)
            {
                allClauseVars = ArrayListMultimap.create();
                for (Pair<String, @Nullable DataType> newMatchVar : newMatchVars)
                {
                    allClauseVars.put(newMatchVar.getFirst(), newMatchVar.getSecond());
                }
            }
            else
            {
                ArrayListMultimap<@NonNull String, @Nullable DataType> newAllClauseVars = ArrayListMultimap.create();
                for (Pair<String, @Nullable DataType> newMatchVar : newMatchVars)
                {
                    // Key must be in newMatchVars and allClauseVars, and if so we retain both types
                    if (allClauseVars.containsKey(newMatchVar.getFirst()))
                    {
                        newAllClauseVars.putAll(newMatchVar.getFirst(), allClauseVars.get(newMatchVar.getFirst()));
                        newAllClauseVars.put(newMatchVar.getFirst(), newMatchVar.getSecond());
                    }
                }
                allClauseVars = newAllClauseVars;
            }
        }
        if (outcome == child)
        {
            if (allClauseVars != null)
            {
                for (Entry<String, Collection<@Nullable DataType>> varType : allClauseVars.asMap().entrySet())
                {
                    // If all types are non-null and the same, add as known type
                    // Otherwise, must add null (unknown type).
                    // This is the behaviour of checkAllSame so we can just use that:
                    @Nullable DataType t;
                    try
                    {
                        List<DataType> nonNull = new ArrayList<>();
                        for (@Nullable DataType dataType : varType.getValue())
                        {
                            if (dataType != null)
                                nonNull.add(dataType);
                        }
                        if (nonNull.size() == varType.getValue().size())
                            t = DataType.checkAllSame(nonNull, s -> {});
                        else
                            t = null;
                    }
                    catch (InternalException | UserException e)
                    {
                        // Don't worry too much about it, just give unknown type:
                        t = null;
                    }
                    vars.add(new Pair<>(varType.getKey(), t));
                }
            }
            return vars;
        }
        Utility.logStackTrace("Unknown child: " + child);
        return vars;
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
        if (child == outcome)
        {
            parent.focusRightOf(this);
        }
        else
        {
            boolean focusNext = false;
            for (Pair<ConsecutiveBase, ConsecutiveBase> match : matches)
            {
                if (focusNext)
                {
                    match.getFirst().focus(Focus.LEFT);
                    return;
                }
                if (match.getFirst() == child)
                {
                    match.getSecond().focus(Focus.LEFT);
                    return;
                }
                if (match.getSecond() == child)
                {
                    focusNext = true;
                }
            }
            if (focusNext)
                parent.focusRightOf(this);
        }
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        if (child == outcome)
        {
            matches.get(matches.size() - 1).getSecond().focus(Focus.LEFT);
        }
        else
        {
            boolean focusEarlier = false;
            for (ListIterator<Pair<ConsecutiveBase, ConsecutiveBase>> iterator = matches.listIterator(); iterator.hasPrevious(); )
            {
                Pair<ConsecutiveBase, ConsecutiveBase> match = iterator.previous();
                if (focusEarlier)
                {
                    match.getSecond().focus(Focus.RIGHT);
                    return;
                }
                if (match.getSecond() == child)
                {
                    match.getFirst().focus(Focus.RIGHT);
                    return;
                }
                if (match.getSecond() == child)
                {
                    focusEarlier = true;
                }
            }
            if (focusEarlier)
                parent.focusLeftOf(this);
        }
    }

    public ExpressionNode focusWhenShown()
    {
        matches.get(0).getFirst().focus(Focus.LEFT);
        return this;
    }

    public boolean isMatchNode(ConsecutiveBase consecutive)
    {
        for (Pair<ConsecutiveBase, ConsecutiveBase> match : matches)
        {
            if (match.getFirst() == consecutive)
                return true;
        }
        return false;
    }

    public Function<MatchExpression, MatchClause> toClauseExpression(FXPlatformConsumer<Object> onError)
    {
        List<Function<MatchExpression, Pattern>> patterns = new ArrayList<>();
        for (Pair<ConsecutiveBase, ConsecutiveBase> match : matches)
        {
            Function<MatchExpression, PatternMatch> patExp = match.getFirst().toPattern();
            Expression matchExp = match.getSecond().toExpression(onError);
            patterns.add(me -> new Pattern(patExp.apply(me), matchExp));
        }
        Expression outcomeExp = this.outcome.toExpression(onError);
        return me -> me.new MatchClause(Utility.<Function<MatchExpression, Pattern>, Pattern>mapList(patterns, f -> f.apply(me)), outcomeExp);
    }
}
