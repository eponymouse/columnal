package records.transformations.function.list;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

public class JoinLists extends FunctionDefinition
{
    public JoinLists() throws InternalException
    {
        super("list:join lists");
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
            ListEx listOfLists = arg(0, ListEx.class);
            return DataTypeUtility.value(new ListEx()
            {
                private int cachedSize = -1;
                
                @Override
                public int size() throws InternalException, UserException
                {
                    if (cachedSize < 0)
                    {
                        cachedSize = 0;
                        for (int i = 0; i < listOfLists.size(); i++)
                        {
                            cachedSize += Utility.cast(listOfLists.get(i), ListEx.class).size();
                        }
                    }
                    return cachedSize;
                }

                @Override
                public @Value Object get(int index) throws InternalException, UserException
                {
                    // We could cache the individual list sizes.
                    for (int i = 0; i < listOfLists.size(); i++)
                    {
                        ListEx subList = Utility.cast(listOfLists.get(i), ListEx.class);
                        int subListSize = subList.size(); 
                        if (index < subListSize)
                        {
                            return subList.get(index);
                        }
                        else
                        {
                            index -= subListSize;
                        }
                    }
                    // Add one to convert back to user index:
                    throw new UserException("Element index " + (index + 1) + " beyond end of list, which is of size " + size());
                }
            });
        }
    }
}
