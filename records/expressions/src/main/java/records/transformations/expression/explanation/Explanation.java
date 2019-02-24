package records.transformations.expression.explanation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

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
    private final @Value Object result;
    private final ImmutableList<ExplanationLocation> directlyUsedLocations;

    protected Explanation(Expression expression, EvaluateState evaluateState, @Value Object result,  ImmutableList<ExplanationLocation> directlyUsedLocations)
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
}
