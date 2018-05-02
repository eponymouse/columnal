package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

// Foldr, by another name.
public class Combine extends FunctionDefinition
{
    public Combine() throws InternalException
    {
        super("listprocess:combine");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            @Value Object @Value [] args = Utility.castTuple(arg, 2);
            ListEx list = Utility.cast(args[0], ListEx.class);
            if (list.size() == 0)
                throw new UserException("Called combine with empty list");
            @Value Object acc = list.get(0);
            ValueFunction function = Utility.cast(args[1], ValueFunction.class);
            for (int i = 1; i < list.size(); i++)
            {
                acc = function.call(DataTypeUtility.value(new @Value Object[] {acc, list.get(i)}));
            }
            return acc;
        }
    }
}
