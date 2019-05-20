package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

public class Min extends FunctionDefinition
{
    public static final String NAME = "minimum";
    
    public Min() throws InternalException
    {
        super("comparison:minimum");
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
            @Value ListEx list = arg(0, ListEx.class);
            if (list.size() == 0)
            {
                throw new UserException("Cannot take minimum of empty list");
            }
            else
            {
                @Value Object min = list.get(0);
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(min, val) > 0)
                        min = val;
                }
                return min;
            }
        }
    }
}
