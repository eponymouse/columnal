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

package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.junit.runner.RunWith;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import test.gen.GenRandom;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@RunWith(JUnitQuickcheck.class)
public class TestSetValue
{
    @Property(trials=200)
    @OnThread(Tag.Simulation)
    public void propSetValue(@From(GenTypeAndValueGen.class)TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        @Initialized int length = 1 + r.nextInt(100);
        List<Either<String, @Value Object>> originals = new ArrayList<>();
        List<Either<String, @Value Object>> replacements = new ArrayList<>();
        for (int i = 0; i < length; i++)
        {
            originals.add(r.nextInt(8) == 1 ? Either.left("~R" + r.nextInt(100)) : Either.right(typeAndValueGen.makeValue()));
            replacements.add(r.nextInt(8) == 1 ? Either.left("#R" + r.nextInt(100)) : Either.right(typeAndValueGen.makeValue()));
        }
        @SuppressWarnings({"keyfor", "units"})
        EditableRecordSet rs = new EditableRecordSet(Collections.singletonList(ColumnUtility.makeImmediateColumn(typeAndValueGen.getType(), new ColumnId("C0"), originals, typeAndValueGen.makeValue())), () -> length);
        Column col = rs.getColumns().get(0);

        List<Pair<Integer, Either<String, @Value Object>>> pendingReplacements = new ArrayList<>();

        // Check initial store worked:
        for (int i = 0; i < length; i++)
        {
            TBasicUtil.assertValueEitherEqual("Value " + i, originals.get(i), collapseErr(col.getType(), i));
            pendingReplacements.add(new Pair<>(i, replacements.get(i)));
        }

        // Do replacements:
        while (!pendingReplacements.isEmpty())
        {
            Pair<Integer, Either<String, @Value Object>> repl = TBasicUtil.removeRandom(r, pendingReplacements);
            col.getType().setCollapsed(repl.getFirst(), repl.getSecond());
        }

        // Check replacement worked:
        for (int i = 0; i < length; i++)
        {
            TBasicUtil.assertValueEitherEqual("Value " + i, replacements.get(i), collapseErr(col.getType(), i));
        }
    }

    @OnThread(Tag.Simulation)
    private Either<String, @Value Object> collapseErr(DataTypeValue type, int rowIndex) throws UserException, InternalException
    {
        try
        {
            return Either.right(type.getCollapsed(rowIndex));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }
}
