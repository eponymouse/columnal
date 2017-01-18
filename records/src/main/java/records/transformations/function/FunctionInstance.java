package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionInstance
{
    @OnThread(Tag.Simulation)
    public abstract @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params) throws UserException, InternalException;
}
