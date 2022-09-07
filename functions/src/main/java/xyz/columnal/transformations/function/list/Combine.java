package xyz.columnal.transformations.function.list;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
        {
            ListEx list = arg(0, ListEx.class);
            if (list.size() == 0)
                throw new UserException("Called combine with empty list");
            @Value Object acc = list.get(0);
            ValueFunction function = arg(1, ValueFunction.class);
            for (int i = 1; i < list.size(); i++)
            {
                acc = function.call(new @Value Object[] {acc, list.get(i)});
            }
            return acc;
        }
    }
}
