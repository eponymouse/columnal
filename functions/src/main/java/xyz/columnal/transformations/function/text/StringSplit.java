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

package xyz.columnal.transformations.function.text;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

import java.util.regex.Pattern;

public class StringSplit extends FunctionDefinition
{
    public StringSplit() throws InternalException
    {
        super("text:split text");
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<String, String>(String.class, String.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value String text, @Value String separator) throws InternalException, UserException
            {
                String[] split;
                if (separator.isEmpty())
                    split = text.codePoints().mapToObj(n -> Utility.codePointToString(n)).toArray(String[]::new);
                else
                    split = text.split(Pattern.quote(separator), -1);
                return DataTypeUtility.value(new ListEx() {

                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return split.length;
                    }

                    @Override
                    public @Value Object get(int index) throws InternalException, UserException
                    {
                        if (index < 0 || index >= split.length)
                            throw new UserException("Invalid list index: " + index);
                        return DataTypeUtility.value(split[index]);
                    }
                });
            }
        };
    }
}
