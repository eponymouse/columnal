package records.transformations.function.list;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

public class Single extends FunctionDefinition
{
    public Single() throws InternalException
    {
        super("list:get single");
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
