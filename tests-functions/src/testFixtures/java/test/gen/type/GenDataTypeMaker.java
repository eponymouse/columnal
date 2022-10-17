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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenValueBase;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.JellyTypeMaker;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by neil on 13/01/2017.
 */
@SuppressWarnings("recorded")
public class GenDataTypeMaker extends GenValueBase<DataTypeMaker>
{
    private final GenJellyTypeMaker genJellyTypeMaker;
    private boolean mustHaveValues;
    
    public class DataTypeAndValueMaker
    {
        private final TypeManager typeManager;
        private final DataType dataType;

        private DataTypeAndValueMaker(TypeManager typeManager, DataType dataType)
        {
            this.typeManager = typeManager;
            this.dataType = dataType;
        }

        @OnThread(Tag.Any)
        public DataType getDataType()
        {
            return dataType;
        }

        public TypeManager getTypeManager()
        {
            return typeManager;
        }
        
        public @Value Object makeValue() throws InternalException, UserException
        {
            return GenDataTypeMaker.this.makeValue(dataType);
        }
    }

    public class DataTypeMaker
    {
        private final JellyTypeMaker jellyTypeMaker;

        public DataTypeMaker(JellyTypeMaker jellyTypeMaker)
        {
            this.jellyTypeMaker = jellyTypeMaker;
        }
        
        public DataTypeAndValueMaker makeType() throws InternalException, UserException
        {
            DataType dataType;
            do
            {
                dataType = jellyTypeMaker.makeType().makeDataType(ImmutableMap.of(), jellyTypeMaker.typeManager);
            }
            while (mustHaveValues && !hasValues(dataType));


            return new DataTypeAndValueMaker(jellyTypeMaker.typeManager, dataType);
        }

        public TypeManager getTypeManager()
        {
            return jellyTypeMaker.typeManager;
        }
    }
    
    public GenDataTypeMaker()
    {
        // All kinds:
        this(false);
    }

    public GenDataTypeMaker(boolean mustHaveValues)
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()), mustHaveValues);
    }

    public GenDataTypeMaker(ImmutableSet<TypeKinds> typeKinds, boolean mustHaveValues)
    {
        this(new GenJellyTypeMaker(typeKinds, ImmutableSet.of(), mustHaveValues), mustHaveValues);
    }
    
    protected GenDataTypeMaker(GenJellyTypeMaker genJellyTypeMaker, boolean mustHaveValues)
    {
        super(DataTypeMaker.class);
        this.genJellyTypeMaker = genJellyTypeMaker;
        this.mustHaveValues = mustHaveValues;
    }
    
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public DataTypeMaker generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        JellyTypeMaker jellyTypeMaker = genJellyTypeMaker.generate(r, generationStatus);
        return new DataTypeMaker(jellyTypeMaker);
    }

    private static boolean hasValues(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Boolean, InternalException>()
        {
            @Override
            public Boolean number(NumberInfo numberInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean text() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean bool() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                if (tags.isEmpty())
                {
                    return false;
                }
                else
                {
                    for (TagType<DataType> tag : tags)
                    {
                        if (tag.getInner() != null && !hasValues(tag.getInner()))
                            return false;
                    }
                    return true;
                }
            }

            @Override
            public Boolean record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                for (DataType type : fields.values())
                {
                    if (!hasValues(type))
                    {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Boolean array(DataType inner) throws InternalException
            {
                return hasValues(inner);
            }
        });
    }

    public static class GenTaggedType extends GenDataTypeMaker
    {
        public GenTaggedType()
        {
            super(new GenJellyTypeMaker.GenTaggedType(), true);
        }        
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface MustHaveValues {
    }
    
    public void configure(MustHaveValues mustHaveValues)
    {
        this.mustHaveValues = true;
    }
}
