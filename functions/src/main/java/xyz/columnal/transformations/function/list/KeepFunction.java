package xyz.columnal.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class KeepFunction extends FunctionDefinition
{
    public KeepFunction() throws InternalException
    {
        super("listprocess:select");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<ListEx, ValueFunction>(ListEx.class, ValueFunction.class)
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Object call2(ListEx list, ValueFunction keep) throws InternalException, UserException
            {
                // We could copy or share here.  My guess is that usually
                // the argument will be a column, so share is the better
                // implementation.  However, copy is simpler.

                ImmutableList.Builder<@Value Object> items = ImmutableList.builder();
                for (int i = 0; i < list.size(); i++)
                {
                    @Value Object x = list.get(i);
                    if (Utility.cast(keep.call(new @Value Object[] {x}), Boolean.class))
                        items.add(x);
                }
                
                return new ListExList(items.build());
            }
        };
    }
}
