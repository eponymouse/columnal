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
import annotation.userindex.qual.UserIndex;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionDefinition
{
    public static final String NAME = "element";
    
    // Takes parameters: column/array, index
    public GetElement() throws InternalException
    {
        super("list:element");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            @Value int oneBasedIndex = intArg(1);
            @UserIndex int userIndex = DataTypeUtility.userIndex(oneBasedIndex);
            addUsedLocations(locs -> {
                ExplanationLocation resultLoc = locs.get(0).getListElementLocation(oneBasedIndex - 1);
                if (resultLoc != null)
                    setResultIsLocation(resultLoc);
                return Utility.streamNullable(resultLoc);
            });
            return Utility.getAtIndex(arg(0, ListEx.class), userIndex);
        }
    }
}
