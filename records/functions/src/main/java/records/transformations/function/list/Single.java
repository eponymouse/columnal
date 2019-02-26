package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

public class Single extends FunctionDefinition
{
    public Single() throws InternalException
    {
        super("list:single");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            ListEx list = arg(0, ListEx.class);
            if (list.size() == 1)
                return list.get(0);
            else
                throw new UserException("List must be of size 1, but was size " + list.size());
        }
    }
}
