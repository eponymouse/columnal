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
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExBiFunction;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Created by neil on 02/12/2016.
 */
public class GenColumn extends GenValueBase<ExBiFunction<Integer, RecordSet, Column>>
{
    private final TableManager mgr;
    // Static so we don't get unintended duplicates during testing:
    private static Supplier<ColumnId> nextCol = new Supplier<ColumnId>() {
        int nextId = 0;
        @Override
        public ColumnId get()
        {
            return new ColumnId(IdentifierUtility.identNum("GenCol", (nextId++)));
        }
    };
    private final List<DataType> distinctTypes;
    private final boolean canHaveErrors;

    @SuppressWarnings("unchecked")
    public GenColumn(TableManager mgr, List<DataType> distinctTypes, boolean canHaveErrors)
    {
        super((Class<ExBiFunction<Integer, RecordSet, Column>>)(Class<?>)BiFunction.class);
        this.mgr = mgr;
        this.distinctTypes = distinctTypes;
        this.canHaveErrors = canHaveErrors;
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        DataType type = sourceOfRandomness.choose(distinctTypes);
        try
        {
            return columnForType(type, sourceOfRandomness);
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Only valid to call after generate has been called at least once
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExBiFunction<Integer, RecordSet, Column> columnForType(DataType type, SourceOfRandomness sourceOfRandomness) throws InternalException
    {
        return (len, rs) -> ColumnUtility.makeImmediateColumn(type, nextCol.get(), Utility.<Either<String, @Value Object>>makeListEx(len, i -> {
            if (canHaveErrors && sourceOfRandomness.nextInt(10) == 1)
                return Either.left("#" + r.nextInt());
            else
                return Either.right(makeValue(type));
        }), makeValue(type)).apply(rs);
    }
}
