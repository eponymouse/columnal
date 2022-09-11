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
import test.functions.TFunctionUtil;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.RTransformation;
import test.DummyManager;
import test.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 27/11/2016.
 */
public class GenNonsenseRTransform extends Generator<Transformation_Mgr>
{
    public GenNonsenseRTransform()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        try
        {
            DummyManager mgr = TFunctionUtil.managerWithTestTypes().getFirst();

            ImmutableList<TableId> srcIds = TBasicUtil.makeList(sourceOfRandomness, 0, 5, () -> TBasicUtil.generateTableId(sourceOfRandomness));
            ImmutableList<String> pkgs = TBasicUtil.makeList(sourceOfRandomness, 0, 10, () -> TBasicUtil.generateIdent(sourceOfRandomness));
            String rExpression = TBasicUtil.makeNonEmptyString(sourceOfRandomness, generationStatus);
            
            return new Transformation_Mgr(mgr, new RTransformation(mgr, TFunctionUtil.ILD, srcIds, pkgs, rExpression));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}
