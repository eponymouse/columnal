package records.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import records.transformations.expression.function.ValueFunction;

public class MapFunction extends FunctionDefinition
{
    public MapFunction() throws InternalException
    {
        super("listprocess:apply each");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<ListEx, ValueFunction>(ListEx.class, ValueFunction.class)
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Object call2(ListEx list, ValueFunction f) throws InternalException, UserException
            {
                ImmutableList.Builder<@Value Object> items = ImmutableList.builderWithExpectedSize(list.size());
                for (int i = 0; i < list.size(); i++)
                {
                    @Value Object x = list.get(i);
                    items.add(f.call(new @Value Object[] {x}));
                }
                
                return new ListExList(items.build());
            }
        };
    }
}
