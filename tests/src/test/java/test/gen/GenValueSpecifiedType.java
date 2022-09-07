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

package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.TestUtil;
import test.gen.GenValueSpecifiedType.ValueGenerator;

import java.util.function.Function;

/**
 * Generates a value of a type specified by the caller.
 */
public class GenValueSpecifiedType extends GenValueBase<ValueGenerator>
{
    @SuppressWarnings("valuetype")
    public GenValueSpecifiedType()
    {
        super(ValueGenerator.class);
    }

    @Override
    public ValueGenerator generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        // Not sure if this will mess with randomness by storing the items for later use:
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        return this::makeValue;
    }
    
    public interface ValueGenerator
    {
        public @Value Object makeValue(DataType t) throws InternalException, UserException;
    }
}
