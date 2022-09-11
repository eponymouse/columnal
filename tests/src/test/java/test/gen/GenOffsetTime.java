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
import xyz.columnal.data.TBasicUtil;

import java.time.OffsetTime;

/**
 * Created by neil on 15/12/2016.
 */
public class GenOffsetTime extends Generator<OffsetTime>
{
    public GenOffsetTime()
    {
        super(OffsetTime.class);
    }

    @Override
    public OffsetTime generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        return OffsetTime.of(r.nextInt(24), r.nextInt(60), r.nextInt(60), r.nextInt(1000000000), TBasicUtil.generateZoneOffset(r, generationStatus));
    }
}
