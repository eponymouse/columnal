/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.function.lookup;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class LookupFunctions
{
    public static List<FunctionDefinition> getLookupFunctions() throws InternalException
    {
        return ImmutableList.of(
            new FunctionDefinition("lookup:lookup")
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
                {
                    return ValueFunction.value(new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                        {
                            ListEx listA = arg(0, ListEx.class);
                            ListEx listB = arg(2, ListEx.class);
                            
                            if (listA.size() != listB.size())
                                throw new UserException("Lists passed to lookup function must be the same size, but first list was size: " + listA.size() + " and second list was size: " + listB.size());
                            
                            int index = getSingleItem(lookupIndexes(listA, arg(1)), arg(1));
                            return listB.get(index);
                        }
                    });
                }
            },
            new FunctionDefinition("lookup:lookup all")
            {
                @Override
                public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
                {
                    return ValueFunction.value(new ValueFunction()
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                        {
                            ListEx listA = arg(0, ListEx.class);
                            ListEx listB = arg(2, ListEx.class);

                            if (listA.size() != listB.size())
                                throw new UserException("Lists passed to lookup function must be the same size, but first list was size: " + listA.size() + " and second list was size: " + listB.size());

                            List<Integer> indexes = getAllItems(lookupIndexes(listA, arg(1)));
                            return DataTypeUtility.value(new ListEx()
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
                            });
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
    private static int getSingleItem(SimulationSupplier<OptionalInt> nextIndex, @Value Object target) throws InternalException, UserException
    {
        // Check that there's one:
        OptionalInt first = nextIndex.get();
        if (!first.isPresent())
            throw new UserException("No matching item found in lookup function for " + DataTypeUtility.valueToString(target));
        OptionalInt second = nextIndex.get();
        if (second.isPresent())
            throw new UserException("More than one matching item found in lookup function for " + DataTypeUtility.valueToString(target));
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
