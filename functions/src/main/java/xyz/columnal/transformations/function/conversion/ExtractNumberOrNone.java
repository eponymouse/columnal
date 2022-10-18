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

package xyz.columnal.transformations.function.conversion;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

public class ExtractNumberOrNone extends FunctionDefinition
{
    public static final @FuncDocKey String NAME = "conversion:extract number or none";

    public ExtractNumberOrNone() throws InternalException
    {
        super(NAME);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return getInstance(typeManager);
    }

    @OnThread(Tag.Simulation)
    public ValueFunction getInstance(TypeManager typeManager)
    {
        return new ValueFunction1<String>(String.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call1(@Value String s) throws InternalException, UserException
            {
                @MonotonicNonNull @Value Number result = null;
                for (int i = 0; i < s.length(); i++)
                {
                    if ('0' <= s.charAt(i) && s.charAt(i) <= '9')
                    {   
                        int start = i;
                        // Look before us for minus sign:
                        if (i > 0 && s.charAt(i - 1) == '-')
                            start -= 1;
                        // Chomp all digits:
                        while (i < s.length() && (('0' <= s.charAt(i) && s.charAt(i) <= '9') || s.charAt(i) == ','))
                            i += 1;
                        // Chomp dot and more digits:
                        if (i < s.length() && s.charAt(i) == '.')
                        {
                            i += 1;
                            while (i < s.length() && ('0' <= s.charAt(i) && s.charAt(i) <= '9'))
                                i += 1;
                        }
                        @Value Number parsed = null;
                        try
                        {
                            parsed = Utility.parseNumber(s.substring(start, i).replaceAll(",", ""));
                        }
                        catch (UserException e)
                        {
                            // Shouldn't happen; log but continue
                            Log.log(e);
                        }
                        if (parsed != null)
                        {
                            if (result != null)
                                return new TaggedValue(0, null, typeManager.getMaybeType());
                            result = parsed;
                        }
                        
                        // Counteract the next increment:
                        i -=1;
                    }
                }
                
                if (result == null)
                    return new TaggedValue(0, null, typeManager.getMaybeType());
                else
                    return new TaggedValue(1, result, typeManager.getMaybeType());
            }
        };
    }
}
