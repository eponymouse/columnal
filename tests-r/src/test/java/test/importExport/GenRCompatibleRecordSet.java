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

package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.rinterop.ConvertFromR.TableType;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.importExport.GenRCompatibleRecordSet.RCompatibleRecordSet;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import static org.junit.Assert.assertEquals;

public class GenRCompatibleRecordSet extends Generator<RCompatibleRecordSet>
{
    public static class RCompatibleRecordSet
    {
        public final KnownLengthRecordSet recordSet;
        public final TypeManager typeManager;
        public final ImmutableSet<TableType> supportedTableTypes;

        public RCompatibleRecordSet(KnownLengthRecordSet recordSet, TypeManager typeManager, ImmutableSet<TableType> supportedTableTypes)
        {
            this.recordSet = recordSet;
            this.typeManager = typeManager;
            this.supportedTableTypes = supportedTableTypes;
        }
    }
    
    public GenRCompatibleRecordSet()
    {
        super(RCompatibleRecordSet.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public RCompatibleRecordSet generate(SourceOfRandomness random, GenerationStatus status)
    {
        boolean tibbleOnly = random.nextBoolean();
        ImmutableSet<TypeKinds> core = ImmutableSet.of(TypeKinds.NUM_TEXT_TEMPORAL, TypeKinds.MAYBE_UNNESTED, TypeKinds.NEW_TAGGED_NO_INNER, TypeKinds.BOOLEAN);
        GenDataTypeMaker gen = new GenDataTypeMaker(
                tibbleOnly ? Utility.<TypeKinds>appendToSet(Utility.<TypeKinds>appendToSet(core, TypeKinds.LIST), TypeKinds.RECORD) : core, 
                true);

        int numColumns = 1 + random.nextInt(10);
        int numRows = random.nextInt(20);

        int nextCol[] = new int[] {0};
        try
        {
            DataTypeMaker dataTypeMaker = gen.generate(random, status);
            return new RCompatibleRecordSet(new KnownLengthRecordSet(TBasicUtil.<SimulationFunction<RecordSet, EditableColumn>>makeList(random, numColumns, numColumns, () -> {
                DataTypeAndValueMaker type = dataTypeMaker.makeType();
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String columnId = "Col " + nextCol[0]++;
                return ColumnUtility.makeImmediateColumn(type.getDataType(), new ColumnId(columnId), TBasicUtil.<Either<String, @Value Object>>makeList(random, numRows, numRows, () -> Either.<String, @Value Object>right(type.makeValue())), type.makeValue());
            }), numRows), dataTypeMaker.getTypeManager(), tibbleOnly ? ImmutableSet.of(TableType.TIBBLE) : ImmutableSet.of(TableType.DATA_FRAME, TableType.TIBBLE));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
