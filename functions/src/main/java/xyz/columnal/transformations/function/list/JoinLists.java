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

package xyz.columnal.transformations.function.list;

import annotation.qual.Value;
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
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
