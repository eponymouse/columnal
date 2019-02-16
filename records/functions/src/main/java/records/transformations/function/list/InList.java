package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import records.data.ValueFunction;

public class InList extends FunctionDefinition
{
    public InList() throws InternalException
    {
        super("list:in list");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            @Value Object[] args = Utility.castTuple(arg, 2);
            ListEx list = Utility.cast(args[1], ListEx.class);
            for (int i = 0; i < list.size(); i++)
            {
                if (Utility.compareValues(list.get(i), args[0]) == 0)
                    return DataTypeUtility.value(true);
            }
            return DataTypeUtility.value(false);
        }
    }
}
