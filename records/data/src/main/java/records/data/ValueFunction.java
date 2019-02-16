package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    protected boolean recordBooleanExplanation = false;
    protected @Nullable ExplanationLocation booleanExplanation;

    @OnThread(Tag.Simulation)
    public abstract @Value Object call(@Value Object arg) throws InternalException, UserException;
    
    public @Nullable ImmutableList<ExplanationLocation> getBooleanExplanation()
    {
        return booleanExplanation == null ? null : ImmutableList.of(booleanExplanation);
    }

    public void setRecordBooleanExplanation(boolean record)
    {
        recordBooleanExplanation = record;
    }
}
