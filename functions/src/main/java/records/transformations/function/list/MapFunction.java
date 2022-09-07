package records.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
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
