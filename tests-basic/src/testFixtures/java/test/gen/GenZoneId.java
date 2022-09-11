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
import com.pholser.junit.quickcheck.generator.java.time.ZoneIdGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.time.ZoneId;

/**
 * Created by neil on 16/12/2016.
 */
public class GenZoneId extends Generator<ZoneId>
{
    public GenZoneId()
    {
        super(ZoneId.class);
    }

    @Override
    public ZoneId generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        ZoneIdGenerator genZoneId = new ZoneIdGenerator();
        // Don't generate unrecognisable SystemV timezones:
        ZoneId zone;
        do
        {
            zone = genZoneId.generate(sourceOfRandomness, generationStatus);
        }
        while (zone.toString().contains("SystemV") || zone.toString().contains("GMT0") || (zone.toString().length() == 3 && zone.toString().endsWith("T")));
        return zone;
    }
}
