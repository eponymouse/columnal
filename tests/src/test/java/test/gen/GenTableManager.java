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

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.functions.TFunctionUtil;
import xyz.columnal.data.TableManager;

/**
 * No randomness involved; this is just an easy way to pass a fresh table manager to
 * a property test.
 */
public class GenTableManager extends Generator<TableManager>
{
    public GenTableManager()
    {
        super (TableManager.class);
    }

    @Override
    public TableManager generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return TFunctionUtil.managerWithTestTypes().getFirst();
    }
}
