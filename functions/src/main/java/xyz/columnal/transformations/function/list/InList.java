package xyz.columnal.transformations.function.list;

import annotation.funcdoc.qual.FuncDocKey;
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
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class InList extends FunctionDefinition
{
    public static final @FuncDocKey String NAME = "listprocess:list contains";

    public InList() throws InternalException
    {
        super(NAME);
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
        {
            ListEx list = Utility.cast(arg(0), ListEx.class);
            @Value Object target = arg(1);
            for (int i = 0; i < list.size(); i++)
            {
                if (Utility.compareValues(list.get(i), target) == 0)
                    return DataTypeUtility.value(true);
            }
            return DataTypeUtility.value(false);
        }
    }
}
