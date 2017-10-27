package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringWithin extends FunctionDefinition
{
    public StringWithin()
    {
        super("within", "within.short");
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return ImmutableList.of(new FunctionType(Instance::new, DataType.BOOLEAN, DataType.tuple(DataType.TEXT, DataType.TEXT), null));
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            Object[] params = Utility.castTuple(param, 2);
            return Utility.cast(params[1], String.class).contains(Utility.cast(params[0], String.class));
        }
    }
}
