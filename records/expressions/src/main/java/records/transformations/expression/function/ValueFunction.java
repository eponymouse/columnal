package records.transformations.expression.function;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FunctionInt;
import utility.Pair;
import utility.Utility;

import java.util.stream.Stream;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    // null if we are not recording explanations or have no special info
    private @MonotonicNonNull ImmutableList<ExplanationLocation> usedLocations;
    // null if we are not recording
    private @MonotonicNonNull ImmutableList<ArgumentExplanation> argExplanations;
    private @Value Object @MonotonicNonNull[] curArgs;
    

    @OnThread(Tag.Any)
    protected ValueFunction()
    {
    }

    // Not for external calling
    @OnThread(Tag.Simulation)
    protected abstract @Value Object _call() throws InternalException, UserException;

    // Call without recording an explanation
    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args) throws InternalException, UserException
    {
        this.curArgs = args;
        return _call();
    }

    // Call and record an explanation
    @OnThread(Tag.Simulation)
    public final RecordedFunctionResult callRecord(@Value Object[] args, ImmutableList<ArgumentExplanation> argumentExplanations) throws InternalException, UserException
    {
        this.curArgs = args;
        this.argExplanations = argumentExplanations;
        @Value Object result = _call();
        return new RecordedFunctionResult(result, Utility.mapListInt(argumentExplanations, e -> e.getValueExplanation()), usedLocations != null ? usedLocations : ImmutableList.of());
    }

    protected final <T> @Value T arg(int index, Class<T> tClass) throws InternalException
    {
        return Utility.cast(arg(index), tClass);
    }
    
    protected final @Value Object arg(int index) throws InternalException
    {
        if (curArgs == null)
            throw new InternalException("Function accessing missing arguments");
        if (index < 0 || index >= curArgs.length)
            throw new InternalException("Function " + getClass() + " accessing argument " + index + " but only " + curArgs.length);
        return curArgs[index];
    }
    
    protected @Value int intArg(int index) throws InternalException, UserException
    {
        return DataTypeUtility.requireInteger(arg(index, Number.class));
    }
    
    // Used by some function implementations to access explanations
    // of arguments, or of a list element of an argument.
    public static interface ArgumentExplanation
    {
        @OnThread(Tag.Simulation)
        Explanation getValueExplanation() throws InternalException;

        // If this argument is a column, give back the location
        // for the given zero-based index.
        @OnThread(Tag.Simulation)
        @Nullable ExplanationLocation getListElementLocation(int index) throws InternalException;
    }
    
    protected void setUsedLocations(FunctionInt<ImmutableList<ArgumentExplanation>, Stream<ExplanationLocation>> extractLocations) throws InternalException
    {
        if (argExplanations != null)
            this.usedLocations = extractLocations.apply(argExplanations).collect(ImmutableList.<ExplanationLocation>toImmutableList());
    }

    @SuppressWarnings("valuetype")
    public static @Value ValueFunction value(@UnknownIfValue ValueFunction function)
    {
        return function;
    }

    public static class RecordedFunctionResult
    {
        public final @Value Object result;
        public final ImmutableList<Explanation> childExplanations;
        public final ImmutableList<ExplanationLocation> usedLocations;

        public RecordedFunctionResult(@Value Object result, ImmutableList<Explanation> childExplanations, ImmutableList<ExplanationLocation> usedLocations)
        {
            this.result = result;
            this.childExplanations = childExplanations;
            this.usedLocations = usedLocations;
        }
    }
}
