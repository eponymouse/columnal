package records.transformations.expression.explanation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Set;
import java.util.function.Function;

/**
 * An explanation of how a value came to be calculated.
 * Used for display (with hyperlinks), and also potentially
 * for analysing which data locations were used to produce
 * a given value.
 */
public abstract class Explanation
{
    private final Expression expression;
    private final EvaluateState evaluateState;
    private final @Nullable @Value Object result;
    private final ImmutableList<ExplanationLocation> directlyUsedLocations;

    protected Explanation(Expression expression, EvaluateState evaluateState, @Nullable @Value Object result,  ImmutableList<ExplanationLocation> directlyUsedLocations)
    {
        this.expression = expression;
        this.evaluateState = evaluateState;
        this.result = result;
        this.directlyUsedLocations = directlyUsedLocations;
    }

    @OnThread(Tag.Simulation)
    public abstract StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation) throws InternalException, UserException;

    public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
    {
        return directlyUsedLocations;
    }

    @OnThread(Tag.Simulation)
    public abstract ImmutableList<Explanation> getDirectSubExplanations() throws InternalException;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || !(o instanceof Explanation)) return false;

        Explanation that = (Explanation) o;

        if (!expression.equals(that.expression)) return false;
        ImmutableSet<String> usedVars = expression.allVariableReferences().collect(ImmutableSet.<String>toImmutableSet());
        if (!evaluateState.varFilteredTo(usedVars).equals(that.evaluateState.varFilteredTo(usedVars))) return false;
        if (!directlyUsedLocations.equals(that.directlyUsedLocations)) return false;
        try
        {
            if (!getDirectSubExplanations().equals(that.getDirectSubExplanations())) return false;
            // null compares equal to any value, for testing:
            if (result == null || that.result == null)
                return true;
            return Utility.compareValues(result, that.result) == 0;
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            return false;
        }
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public int hashCode()
    {
        int result1 = expression.hashCode();
        result1 = 31 * result1 + evaluateState.hashCode();
        result1 = 31 * result1 + directlyUsedLocations.hashCode();
        try
        {
            result1 = 31 * result1 + getDirectSubExplanations().hashCode();
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        // We don't hash result.  This means we get more hash collisions,
        // but it's not invalid...
        return result1;
    }

    // Only used for testing:
    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public String toString()
    {
        try
        {
            return "Explanation{" +
                    "expression=" + expression +
                    ", evaluateState.rowIndex=" + evaluateState._test_getOptionalRowIndex() +
                    ", evaluateState.vars=" + evaluateState._test_getVariables() +
                    ", result=" + result +
                    ", directlyUsedLocations=" + directlyUsedLocations +
                    ", directSubExplanations=" + getDirectSubExplanations() +
                    '}';
        }
        catch (InternalException e)
        {
            return "InternalException: " + e.getLocalizedMessage();
        }
    }
}
