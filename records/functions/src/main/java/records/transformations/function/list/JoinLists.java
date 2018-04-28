package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.types.MutVar;
import records.types.TypeClassRequirements;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class JoinLists extends FunctionDefinition
{
    public JoinLists()
    {
        super("list/join lists", "joinLists.mini", JoinLists::joinLists);
    }

    private static FunctionTypes joinLists(TypeManager typeManager)
    {
        TypeExp innerType = new MutVar(null, TypeClassRequirements.empty());
        TypeExp listOfInner = TypeCons.list(null, innerType);
        TypeExp listOfLists = TypeCons.list(null, listOfInner);
        
        return new FunctionTypesUniform(typeManager, Instance::new, listOfInner, listOfLists);
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
        {
            ListEx listOfLists = Utility.cast(arg, ListEx.class);
            return new ListEx()
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
            };
        }
    }
}
