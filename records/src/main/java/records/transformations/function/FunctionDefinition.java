package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExConsumer;
import utility.Pair;

import java.util.List;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionDefinition
{
    private final String name;

    public FunctionDefinition(String name)
    {
        this.name = name;
    }

    public abstract @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException;

    public String getName()
    {
        return name;
    }
}
