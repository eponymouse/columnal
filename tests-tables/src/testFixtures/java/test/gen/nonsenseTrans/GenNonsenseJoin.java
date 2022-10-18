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
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.functions.TFunctionUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.Join;
import test.DummyManager;
import test.Transformation_Mgr;
import test.gen.GenValueBase;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseJoin extends GenValueBase<Transformation_Mgr>
{
    public GenNonsenseJoin()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        DummyManager mgr = TFunctionUtil.managerWithTestTypes().getFirst();

        TableId ourId = TBasicUtil.generateTableId(sourceOfRandomness);
        TableId srcIdA = TBasicUtil.generateTableId(sourceOfRandomness);
        TableId srcIdB = TBasicUtil.generateTableId(sourceOfRandomness);
        ImmutableList<Pair<ColumnId, ColumnId>> columns = TBasicUtil.makeList(r, 0, 5, () -> new Pair<>(TBasicUtil.generateColumnId(r), TBasicUtil.generateColumnId(r)));

        try
        {
            return new Transformation_Mgr(mgr, new Join(mgr, new InitialLoadDetails(ourId, null, null, null), srcIdA, srcIdB, r.nextBoolean(), columns));
        }
        catch (InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }

}
