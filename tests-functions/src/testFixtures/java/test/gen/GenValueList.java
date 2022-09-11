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
import test.functions.TFunctionUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenValueList.ListAndType;
import xyz.columnal.utility.Utility.ListEx;

/**
 * Created by neil on 22/01/2017.
 */
public class GenValueList extends GenValueBase<ListAndType>
{
    public static class ListAndType
    {
        public final @Value ListEx list;
        // Type of the whole array, not the item inside:
        public final DataType type;

        public ListAndType(@Value ListEx list, DataType type)
        {
            this.list = list;
            this.type = type;
        }

        @Override
        public String toString()
        {
            return super.toString() + "{Type: " + type + "}";
        }
    }

    public GenValueList()
    {
        super(ListAndType.class);
    }


    @Override
    public ListAndType generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        DataType t = r.choose(TFunctionUtil.managerWithTestTypes().getSecond());
        int length = r.nextInt(0, 100);
        @Value Object[] values = new @Value Object[length];
        for (int i = 0; i < length; i++)
        {
            try
            {
                values[i] = makeValue(t);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
        return new ListAndType(DataTypeUtility.value(new ListEx()
        {
            @Override
            public int size() throws InternalException, UserException
            {
                return length;
            }

            @Override
            public @Value Object get(int index) throws InternalException, UserException
            {
                return values[index];
            }
        }), DataType.array(t));
    }
}
