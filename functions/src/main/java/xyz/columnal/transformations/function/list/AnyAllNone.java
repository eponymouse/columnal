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
import org.checkerframework.checker.nullness.qual.Nullable;
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
import xyz.columnal.transformations.expression.function.ValueFunction;

// Maybe this should be a package
public abstract class AnyAllNone
{
    private static class Processor extends ValueFunction
    {
        private final @Nullable @Value Boolean returnIfTrueFound;
        private final @Nullable @Value Boolean returnIfFalseFound;
        private final @Value Boolean returnAtEnd;

        private Processor(@Nullable Boolean returnIfTrueFound, @Nullable Boolean returnIfFalseFound, Boolean returnAtEnd)
        {
            this.returnIfTrueFound = returnIfTrueFound == null ? null : DataTypeUtility.value(returnIfTrueFound);
            this.returnIfFalseFound = returnIfFalseFound == null ? null : DataTypeUtility.value(returnIfFalseFound);
            this.returnAtEnd = DataTypeUtility.value(returnAtEnd);
        }

        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            ListEx list = arg(0, ListEx.class);
            for (int i = 0; i < list.size(); i++)
            {
                int iFinal = i;
                @Value Boolean result = Utility.cast(callArg(1, new @Value Object [] {list.get(i)}), Boolean.class);
                if (result && returnIfTrueFound != null)
                {
                    addUsedLocations(args -> Utility.streamNullable(args.get(0).getListElementLocation(iFinal)));
                    return returnIfTrueFound;
                }
                else if (!result && returnIfFalseFound != null)
                {
                    addUsedLocations(args -> Utility.streamNullable(args.get(0).getListElementLocation(iFinal)));
                    return returnIfFalseFound;
                }
            }
            return returnAtEnd;
        }
    }

    public static class Any extends FunctionDefinition
    {
        public Any() throws InternalException
        {
            super("listprocess:any");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(true, null, false);
        }
    }

    public static class All extends FunctionDefinition
    {
        public All() throws InternalException
        {
            super("listprocess:all");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(null, false, true);
        }
    }

    public static class None extends FunctionDefinition
    {
        public None() throws InternalException
        {
            super("listprocess:none");
        }

        @Override
        public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
        {
            return new Processor(false, null, true);
        }
    }
}
