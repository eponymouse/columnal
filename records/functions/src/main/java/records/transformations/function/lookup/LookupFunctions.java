package records.transformations.function.lookup;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LookupFunctions
{
    public static List<FunctionDefinition> getLookupFunctions() throws InternalException
    {
        return ImmutableList.of(
            new FunctionDefinition("lookup:lookup")
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException, UserException
                {
                    return DataTypeUtility.value(new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
                        {
                            @Value Object[] args = Utility.castTuple(arg, 3);
                            ListEx listA = Utility.cast(args[0], ListEx.class);
                            ListEx listB = Utility.cast(args[2], ListEx.class);
                            
                            if (listA.size() != listB.size())
                                throw new UserException("Lists passed to lookup function must be the same size, but first list was size: " + listA.size() + " and second list was size: " + listB.size());
                            
                            int index = getSingleItem(lookupIndexes(listA, args[1]), paramTypes.apply("a"), args[1]);
                            return listB.get(index);
                        }
                    });
                }
            },
            new FunctionDefinition("lookup:lookup all")
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException, UserException
                {
                    return DataTypeUtility.value(new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
                        {
                            @Value Object[] args = Utility.castTuple(arg, 3);
                            ListEx listA = Utility.cast(args[0], ListEx.class);
                            ListEx listB = Utility.cast(args[2], ListEx.class);

                            if (listA.size() != listB.size())
                                throw new UserException("Lists passed to lookup function must be the same size, but first list was size: " + listA.size() + " and second list was size: " + listB.size());

                            List<Integer> indexes = getAllItems(lookupIndexes(listA, args[1]));
                            return new ListEx()
                            {
                                @Override
                                public int size() throws InternalException, UserException
                                {
                                    return indexes.size();
                                }
                                
                                @Override
                                public @Value Object get(int index) throws InternalException, UserException
                                {
                                    return listB.get(indexes.get(index));
                                }
                            };
                        }
                    });
                }
            }
        );
    }

    @OnThread(Tag.Simulation)
    private static List<Integer> getAllItems(SimulationSupplier<OptionalInt> nextIndex) throws UserException, InternalException
    {
        ArrayList<Integer> r = new ArrayList<>();
        OptionalInt next;
        do
        {
            next = nextIndex.get();
            next.ifPresent(r::add);
        }
        while (next.isPresent());
        return r;
    }

    @OnThread(Tag.Simulation)
    private static int getSingleItem(SimulationSupplier<OptionalInt> nextIndex, DataType targetType, @Value Object target) throws InternalException, UserException
    {
        // Check that there's one:
        OptionalInt first = nextIndex.get();
        if (!first.isPresent())
            throw new UserException("No matching item found in lookup function for " + DataTypeUtility.valueToString(targetType, target, null));
        OptionalInt second = nextIndex.get();
        if (second.isPresent())
            throw new UserException("More than one matching item found in lookup function for " + DataTypeUtility.valueToString(targetType, target, null));
        return first.getAsInt();
    }

    // Gets the next index, or empty if successfully reached end of list and found no more:
    @OnThread(Tag.Simulation)
    private static SimulationSupplier<OptionalInt> lookupIndexes(ListEx targetList, @Value Object item)
    {
        return new SimulationSupplier<OptionalInt>()
        {
            int nextToCheck = 0;
            
            @Override
            public OptionalInt get() throws InternalException, UserException
            {
                for (int i = nextToCheck; i < targetList.size(); i++)
                {
                    if (Utility.compareValues(targetList.get(i), item) == 0)
                    {
                        nextToCheck = i + 1;
                        return OptionalInt.of(i);
                    }
                }
                return OptionalInt.empty();
            }
        };
    }
}
