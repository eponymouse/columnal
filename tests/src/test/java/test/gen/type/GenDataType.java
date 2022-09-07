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

package test.gen.type;

import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A simplifying wrapper for GenDataTypeMaker that just gives the data type
 */
public class GenDataType extends Generator<DataType>
{
    private final GenDataTypeMaker genDataTypeMaker;
    
    public GenDataType()
    {
        // All kinds:
        this(false);
    }

    public GenDataType(boolean mustHaveValues)
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()), mustHaveValues);
    }

    public GenDataType(ImmutableSet<TypeKinds> typeKinds, boolean mustHaveValues)
    {
        super(DataType.class);
        genDataTypeMaker = new GenDataTypeMaker(typeKinds, mustHaveValues);
    }
    
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public DataType generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            return genDataTypeMaker.generate(r, generationStatus).makeType().getDataType();
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    /*
    public static class GenTaggedType extends Generator<DataTypeAndManager>
    {
        public GenTaggedType()
        {
            super(DataTypeAndManager.class);
        }

        @Override
        public DataTypeAndManager generate(SourceOfRandomness random, GenerationStatus status)
        {
            GenJellyType.GenTaggedType genJellyTagged = new GenJellyType.GenTaggedType();
            
            JellyTypeAndManager jellyTypeAndManager = genJellyTagged.generate(random, status);
            
            try
            {
                return new DataTypeAndManager(jellyTypeAndManager.typeManager, jellyTypeAndManager.jellyType.makeDataType(ImmutableMap.of(), jellyTypeAndManager.typeManager));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    */
}
