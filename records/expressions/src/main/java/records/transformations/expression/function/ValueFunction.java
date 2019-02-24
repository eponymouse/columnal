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
import utility.ExFunction;
import utility.Utility;

import java.util.function.Function;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    private final String name;
    protected boolean recordExplanation = false;
    private @MonotonicNonNull Explanation explanation;
    private @Value Object @Nullable [] curArgs;
    private ArgumentExplanation @Nullable[] curLocs;
    
    @OnThread(Tag.Any)
    protected ValueFunction(/*String name*/)
    {
        this.name = "TODO!!";
    }

    @OnThread(Tag.Simulation)
    protected abstract @Value Object call() throws InternalException, UserException;

    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args) throws InternalException, UserException
    {
        this.curArgs = args;
        if (recordExplanation)
        {
            // We provide a default explanation which is only
            // used if the call() function hasn't filled it in.
            @Value Object result = call();
            if (explanation == null)
            {
               // TODO make an explanation 
            }
            return result;
        }
        else
        {
            return call();
        }
    }

    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args, ArgumentExplanation @Nullable[] argumentExplanations) throws InternalException, UserException
    {
        this.curArgs = args;
        this.curLocs = argumentExplanations;
        return call();
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
    
    public Explanation getExplanation() throws InternalException
    {
        if (explanation != null)
            return explanation;
        else
            throw new InternalException("Function was meant to record an explanation, but did not");
    }

    public void setRecordExplanation(boolean record)
    {
        recordExplanation = record;
    }
    
    protected void setExplanation(@Nullable Explanation explanation)
    {
        if (explanation != null && recordExplanation)
            this.explanation = explanation;
    }
    
    protected @Nullable Explanation withArgLoc(int argIndex, ExFunction<ArgumentExplanation, @Nullable Explanation> fetch) throws InternalException, UserException
    {
        return curLocs == null ? null : fetch.apply(curLocs[argIndex]);
    }
    
    // Used by some function implementations to access explanations
    // of arguments, or of a list element of an argument.
    public static interface ArgumentExplanation
    {
        @OnThread(Tag.Simulation)
        Explanation getValueExplanation() throws InternalException;

        @OnThread(Tag.Simulation)
        @Nullable Explanation getListElementExplanation(int index, @Value Object value) throws InternalException;
    }

    @SuppressWarnings("valuetype")
    public static @Value ValueFunction value(@UnknownIfValue ValueFunction function)
    {
        return function;
    }
}
