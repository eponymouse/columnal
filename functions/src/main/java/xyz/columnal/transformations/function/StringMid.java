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

package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

public class StringMid //extends FunctionDefinition
{
    public StringMid() throws InternalException
    {
        //super("text:middle");
    }

    //@Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            String src = arg(0, String.class);
            int codePointStart = intArg(1);
            if (codePointStart <= 0)
                throw new UserException("Invalid count when calling middle function: " + codePointStart);
            int codePointCount = intArg(2);
            if (codePointCount < 0)
                throw new UserException("Invalid count when calling middle function: " + codePointCount);
            try
            {
                // Start is one-based, so subtract one:
                int startIndex = src.offsetByCodePoints(0, codePointStart - 1);
                try
                {
                    return DataTypeUtility.value(src.substring(startIndex, src.offsetByCodePoints(startIndex, codePointCount)));
                }
                catch (IndexOutOfBoundsException e)
                {
                    // Failed to find end, just return all chars from startIndex:
                    return DataTypeUtility.value(src.substring(startIndex));
                }
            }
            catch (IndexOutOfBoundsException e)
            {
                // Searching for start index failed, so just return empty string:
                return DataTypeUtility.value("");
            }
        }
    }
}
