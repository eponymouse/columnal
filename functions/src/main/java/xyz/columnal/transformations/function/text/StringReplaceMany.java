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
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

public class StringReplaceMany extends FunctionDefinition
{
    public StringReplaceMany() throws InternalException
    {
        super("text:replace many");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            ListEx replacements = arg(0, ListEx.class);
            @Value String whole = arg(1, String.class);
            String[] finds = new String[replacements.size()];
            String[] replaces = new String[finds.length];
            for (int i = 0; i < finds.length; i++)
            {
                Record record = Utility.cast(replacements.get(i), Record.class);
                finds[i] = Utility.cast(record.getField("find"), String.class);
                replaces[i] = Utility.cast(record.getField("replace"), String.class);
            }
            // Quite naive implementation, could be sped up:
            StringBuilder stringBuilder = new StringBuilder();
            int beginSegment = 0;
            int charIndex = 0;
            nextChar: while (charIndex < whole.length())
            {
                for (int findIndex = 0; findIndex < finds.length; findIndex++)
                {
                    String find = finds[findIndex];
                    if (whole.startsWith(find, charIndex))
                    {
                        if (beginSegment < charIndex)
                        {
                            stringBuilder.append(whole, beginSegment, charIndex);
                        }
                        stringBuilder.append(replaces[findIndex]);
                        charIndex += find.length();
                        beginSegment = charIndex;
                        continue nextChar;
                    }
                }
                charIndex++;
            }
            if (beginSegment == 0) // Replaced nothing, return original
                return whole;
            else
                return DataTypeUtility.value(stringBuilder.append(whole, beginSegment, charIndex).toString());
        }
    }
}
