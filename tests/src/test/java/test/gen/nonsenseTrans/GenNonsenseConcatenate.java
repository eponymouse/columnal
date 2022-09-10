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
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.Concatenate;
import xyz.columnal.transformations.Concatenate.IncompleteColumnHandling;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import test.gen.GenValueBase;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 02/02/2017.
 */
public class GenNonsenseConcatenate extends GenValueBase<Transformation_Mgr>
{
    public GenNonsenseConcatenate()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;

        DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();

        TableId ourId = TestUtil.generateTableId(sourceOfRandomness);
        ImmutableList<TableId> srcIds = DataTestUtil.makeList(sourceOfRandomness, 1, 5, () -> TestUtil.generateTableId(sourceOfRandomness));

        IncompleteColumnHandling incompleteColumnHandling = IncompleteColumnHandling.values()[sourceOfRandomness.nextInt(IncompleteColumnHandling.values().length)];

        try
        {
            return new Transformation_Mgr(mgr, new Concatenate(mgr, new InitialLoadDetails(ourId, null, null, null), srcIds, incompleteColumnHandling, r.nextBoolean()));
        }
        catch (InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }

}
