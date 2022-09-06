package records.transformations.function.optional;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

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
