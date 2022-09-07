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

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.TestUtil.Transformation_Mgr;

import java.util.Arrays;
import java.util.List;

/**
 * Generates a transformation which can be successfully loaded and saved,
 * but not necessarily executed successfully.
 */
public class GenNonsenseTransformation extends Generator<Transformation_Mgr>
{
    List<Generator<Transformation_Mgr>> generators = Arrays.asList(
        new GenNonsenseCheck(),
        new GenNonsenseConcatenate(),
        new GenNonsenseFilter(),
        new GenNonsenseJoin(),
        new GenNonsenseHideColumns(),
        new GenNonsenseManualEdit(),
        new GenNonsenseSort(),
        new GenNonsenseSummaryStats(),
        new GenNonsenseTransform(),
        new GenNonsenseRTransform()
    );

    public GenNonsenseTransformation()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return sourceOfRandomness.choose(generators).generate(sourceOfRandomness, generationStatus);
    }
}
