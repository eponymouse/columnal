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
import utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

// Foldr, by another name.
public class Combine extends FunctionDefinition
{
    public Combine() throws InternalException
    {
        super("listprocess:combine");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
        {
            ListEx list = arg(0, ListEx.class);
            if (list.size() == 0)
                throw new UserException("Called combine with empty list");
            @Value Object acc = list.get(0);
            ValueFunction function = arg(1, ValueFunction.class);
            for (int i = 1; i < list.size(); i++)
            {
                acc = function.call(DataTypeUtility.value(new @Value Object[] {acc, list.get(i)}));
            }
            return acc;
        }
    }
}
