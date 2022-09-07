package xyz.columnal.transformations.function.optional;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;

import java.util.ArrayList;

public class GetOptionalsFromList extends FunctionDefinition
{
    public GetOptionalsFromList() throws InternalException
    {
        super("optional:get optionals from list");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<ListEx>(ListEx.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value ListEx srcList) throws InternalException, UserException
            {
                int size = srcList.size();
                ArrayList<@Value Object> present = new ArrayList<>(size / 8);
                
                for (int i = 0; i < size; i++)
                {
                    @Value TaggedValue taggedValue = Utility.cast(srcList.get(i), TaggedValue.class);
                    if (taggedValue.getTagIndex() == 1 && taggedValue.getInner() != null)
                        present.add(taggedValue.getInner());
                }
                
                return new ListExList(present);
            }
        };
    }
}
