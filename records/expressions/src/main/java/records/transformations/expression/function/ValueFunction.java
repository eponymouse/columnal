package records.transformations.expression.function;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.explanation.Explanation;
import records.data.explanation.ExplanationLocation;
import records.error.InternalException;
import records.error.UserException;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Set;
import java.util.function.Function;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    private final String name;
    protected boolean recordExplanation = false;
    //protected @MonotonicNonNull Explanation explanation;
    protected @Nullable ImmutableList<ExplanationLocation> explanation;
    private @Value Object @Nullable [] curArgs;
    private ArgumentLocation @Nullable[] curLocs;
    
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
                //explanation = new FunctionExplanation(name, );
            }
            return result;
        }
        else
        {
            return call();
        }
    }

    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args, ArgumentLocation @Nullable[] argumentLocations) throws InternalException, UserException
    {
        this.curArgs = args;
        this.curLocs = argumentLocations;
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
    
    public ImmutableList<ExplanationLocation> /*Explanation*/ getExplanation() throws InternalException
    {
        //if (explanation != null)
        //    return explanation;
        //else
            throw new InternalException("Function was meant to record an explanation, but did not");
    }

    public void setRecordExplanation(boolean record)
    {
        recordExplanation = record;
    }
    
    protected @Nullable ImmutableList<ExplanationLocation> withArgLoc(int argIndex, Function<ArgumentLocation, @Nullable ImmutableList<ExplanationLocation>> fetch)
    {
        return curLocs == null ? null : fetch.apply(curLocs[argIndex]);
    }
    
    public static interface ArgumentLocation
    {
        @OnThread(Tag.Simulation)
        @Nullable ImmutableList<ExplanationLocation> getValueLocation() throws InternalException;

        @OnThread(Tag.Simulation)
        @Nullable ImmutableList<ExplanationLocation> getListElementLocation(int index);
    }

    @SuppressWarnings("valuetype")
    public static @Value ValueFunction value(@UnknownIfValue ValueFunction function)
    {
        return function;
    }
}
