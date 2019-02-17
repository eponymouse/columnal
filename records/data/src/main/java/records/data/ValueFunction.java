package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    protected boolean recordBooleanExplanation = false;
    protected @Nullable ExplanationLocation booleanExplanation;
    private @Value Object @Nullable [] curArgs;

    @OnThread(Tag.Simulation)
    protected abstract @Value Object call() throws InternalException, UserException;

    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args) throws InternalException, UserException
    {
        this.curArgs = args;
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
    
    public @Nullable ImmutableList<ExplanationLocation> getBooleanExplanation()
    {
        return booleanExplanation == null ? null : ImmutableList.of(booleanExplanation);
    }

    public void setRecordBooleanExplanation(boolean record)
    {
        recordBooleanExplanation = record;
    }
}
