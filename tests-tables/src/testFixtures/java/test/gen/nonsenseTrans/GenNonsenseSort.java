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

package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import test.DummyManager;
import test.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenNonsenseSort extends Generator<Transformation_Mgr>
{
    public GenNonsenseSort()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TBasicUtil.generateTableIdPair(sourceOfRandomness);
        ImmutableList<Pair<ColumnId, Direction>> cols = TBasicUtil.makeList(sourceOfRandomness, 1, 10, () -> new Pair<>(TBasicUtil.generateColumnId(sourceOfRandomness), sourceOfRandomness.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING));

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Sort(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), cols));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
