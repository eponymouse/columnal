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

import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

/**
 * Generates a class which wraps a type and allows for generation of
 * as many values of that type as you want.
 * 
 * A thin simplifying wrapper over GenDataTypeMaker that makes one type
 * and sticks with it.
 */
public class GenTypeAndValueGen extends Generator<TypeAndValueGen>
{
    private final GenDataTypeMaker genDataTypeMaker;

    public class TypeAndValueGen
    {
        private final DataTypeAndValueMaker dataTypeAndValueMaker;

        public TypeAndValueGen(DataTypeAndValueMaker dataTypeAndValueMaker)
        {
            this.dataTypeAndValueMaker = dataTypeAndValueMaker;
        }

        public DataType getType()
        {
            return dataTypeAndValueMaker.getDataType();
        }

        public TypeManager getTypeManager()
        {
            return dataTypeAndValueMaker.getTypeManager();
        }

        public @Value Object makeValue() throws InternalException, UserException
        {
            return dataTypeAndValueMaker.makeValue();
        }
    }

    public GenTypeAndValueGen()
    {
        this(false);
    }

    // If false, can be any type
    public GenTypeAndValueGen(boolean onlyNumTextTemporal)
    {
        super(TypeAndValueGen.class);
        this.genDataTypeMaker = onlyNumTextTemporal ?
            new GenDataTypeMaker(ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL), true) :        
            new GenDataTypeMaker(true);
    }


    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public TypeAndValueGen generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            DataTypeMaker generated = genDataTypeMaker.generate(sourceOfRandomness, generationStatus);
            DataTypeAndValueMaker result = generated.makeType();
            return new TypeAndValueGen(result);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public TypeAndValueGen generate(Random r)
    {
        SourceOfRandomness sourceOfRandomness = new SourceOfRandomness(r);
        return generate(sourceOfRandomness, new SimpleGenerationStatus(new GeometricDistribution(), sourceOfRandomness, 1));
    }

}
