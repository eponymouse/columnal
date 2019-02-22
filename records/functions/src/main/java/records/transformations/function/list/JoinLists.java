package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
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
        public @OnThread(Tag.Simulation) @Value Object call() throws InternalException, UserException
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
