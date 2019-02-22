package records.data.explanation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;

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
    private final ImmutableList<ExplanationLocation> directlyUsedLocations;
    private final ImmutableList<Explanation> directSubExplanations;

    protected Explanation(ImmutableList<ExplanationLocation> directlyUsedLocations, ImmutableList<Explanation> directSubExplanations)
    {
        this.directlyUsedLocations = directlyUsedLocations;
        this.directSubExplanations = directSubExplanations;
    }

    public abstract StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
    {
        return directlyUsedLocations;
    }

    public ImmutableList<Explanation> getDirectSubExplanations()
    {
        return directSubExplanations;
    }
}
