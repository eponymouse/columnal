package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.interning.qual.UnknownInterned;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.jetbrains.annotations.NotNull;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.MatchExpression.PatternMatch;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
public class ClauseNode implements ExpressionParent, ExpressionNode
{
    private final PatternMatchNode parent;
    private final ObservableList<Pair<Consecutive, Consecutive>> matches;
    private final Label mapsTo;
    private final Consecutive outcome;
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;

    @SuppressWarnings("initialization")
    public ClauseNode(String rest, PatternMatchNode parent)
    {
        this.parent = parent;
        this.mapsTo = new Label("\u2794");
        this.mapsTo.getStyleClass().add("mapsto");
        this.matches = FXCollections.observableArrayList();
        this.nodes = FXCollections.observableArrayList();
        this.childrenNodeListener = c -> {
            updateNodes();
        };
        matches.addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor Pair<@UnknownKeyFor Consecutive, @UnknownKeyFor Consecutive>>) c -> {
            updateNodes();
            updateListeners();
        });
        // Must do outcome first because updateNodes will use it:
        this.outcome = makeConsecutive().prompt("value");
        this.matches.add(new Pair<>(makeConsecutive().prompt("pattern"), makeConsecutive().prompt("condition")));

    }

    private void updateListeners()
    {
        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:
        for (Pair<Consecutive, Consecutive> match : matches)
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

    }

    private void updateNodes()
    {
        List<Node> childrenNodes = new ArrayList<Node>(Stream.concat(
            matches.stream().flatMap(p -> Stream.concat(p.getFirst().nodes().stream(), p.getSecond().nodes().stream())),
            Stream.concat(Stream.of(mapsTo), outcome.nodes().stream())).collect(Collectors.<Node>toList()));
        nodes.setAll(childrenNodes);
    }

    @NotNull
    private Consecutive makeConsecutive()
    {
        return new Consecutive(this, null, null);
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
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

    @Override
    public List<String> getAvailableVariables(ExpressionNode child)
    {
        //TODO union of clause variables or just the clause variable for guard
        // (and always mix in parent variables)
        ArrayList<@NonNull String> vars = new ArrayList<>(parent.getAvailableVariables(this));

        HashSet<@NonNull String> allClauseVars = null;
        for (Pair<Consecutive, Consecutive> match : matches)
        {
            if (match.getFirst() == child)
                return vars; // Matching side only has access to parent vars
            List<String> newMatchVars = match.getFirst().getDeclaredVariables();
            if (match.getSecond() == child)
            {
                vars.addAll(newMatchVars);
                return vars;
            }
            // Otherwise keep intersection of vars from different clauses:
            if (allClauseVars == null)
                allClauseVars = new HashSet<>(newMatchVars);
            else
                allClauseVars.retainAll(newMatchVars);
        }
        if (outcome == child)
        {
            if (allClauseVars != null)
                vars.addAll(allClauseVars);
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
    public void focusRightOf(ExpressionNode child)
    {
        if (child == outcome)
        {
            parent.focusRightOf(this);
        }
        else
        {
            boolean focusNext = false;
            for (Pair<Consecutive, Consecutive> match : matches)
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
    public void focusLeftOf(ExpressionNode child)
    {
        if (child == outcome)
        {
            matches.get(matches.size() - 1).getSecond().focus(Focus.LEFT);
        }
        else
        {
            boolean focusEarlier = false;
            for (ListIterator<Pair<Consecutive, Consecutive>> iterator = matches.listIterator(); iterator.hasPrevious(); )
            {
                Pair<Consecutive, Consecutive> match = iterator.previous();
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

    public boolean isMatchNode(Consecutive consecutive)
    {
        for (Pair<Consecutive, Consecutive> match : matches)
        {
            if (match.getFirst() == consecutive)
                return true;
        }
        return false;
    }

    public @Nullable Function<MatchExpression, MatchClause> toClauseExpression(FXPlatformConsumer<Object> onError)
    {
        List<Function<MatchExpression, Pattern>> patterns = new ArrayList<>();
        boolean allOk = true;
        for (Pair<Consecutive, Consecutive> match : matches)
        {
            @Nullable Function<MatchExpression, PatternMatch> patExp = match.getFirst().toPattern();
            @Nullable Expression matchExp = match.getSecond().toExpression(onError);
            if (patExp != null && matchExp != null)
            {
                // For checker:
                final @NonNull Function<MatchExpression, PatternMatch> patExp2 = patExp;
                final @NonNull Expression matchExp2 = matchExp;

                patterns.add(me -> new Pattern(patExp2.apply(me), Collections.singletonList(matchExp2)));
            } else
                allOk = false;
        }
        final @Nullable Expression outcomeExp = this.outcome.toExpression(onError);

        if (outcomeExp != null && allOk)
        {
            // For checker:
            final @NonNull Expression outcomeExp2 = outcomeExp;
            return me -> me.new MatchClause(Utility.<Function<MatchExpression, Pattern>, Pattern>mapList(patterns, f -> f.apply(me)), outcomeExp2);
        }
        else
            return null;
    }
}
