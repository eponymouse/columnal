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
import org.checkerframework.checker.initialization.qual.Initialized;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.type.GenDataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 30/05/2017.
 */
public class GenEditableColumn extends GenValueBase<EditableColumn>
{
    public GenEditableColumn()
    {
        super(EditableColumn.class);
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public EditableColumn generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        GenDataType genDataType = new GenDataType(true);
        DataType type = genDataType.generate(sourceOfRandomness, generationStatus);
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        try
        {
            final @Initialized int length = sourceOfRandomness.nextInt(5000);
            List<Either<String, @Value Object>> values = new ArrayList<>();
            for (int i = 0; i < length; i++)
            {
                values.add(Either.right(makeValue(type)));
            }
            final SimulationFunction<RecordSet, EditableColumn> create = ColumnUtility.makeImmediateColumn(type, new ColumnId("C"), values, makeValue(type));
            @SuppressWarnings({"keyfor", "units"})
            RecordSet recordSet = new EditableRecordSet(Collections.singletonList(create), () -> length);
            return (EditableColumn)recordSet.getColumns().get(0);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
