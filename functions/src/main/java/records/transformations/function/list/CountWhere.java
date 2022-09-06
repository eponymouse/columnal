package records.transformations.function.list;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

public class CountWhere extends FunctionDefinition
{
    public CountWhere() throws InternalException
    {
        super("listprocess:count where");
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
                int count = 0;
                for (int i = 0; i < list.size(); i++)
                {
                    @Value Object x = list.get(i);
                    if (Utility.cast(keep.call(new @Value Object[] {x}), Boolean.class))
                        count += 1;
                }
                
                return DataTypeUtility.value(count);
            }
        };
    }
}
