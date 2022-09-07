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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenUnit extends Generator<Unit>
{
    private @MonotonicNonNull List<SingleUnit> units;

    public GenUnit()
    {
        super(Unit.class);
    }

    @Override
    public Unit generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            if (units == null)
            {

                    UnitManager mgr = new UnitManager();
                    units = mgr.getAllDeclared();

            }

            Unit u = Unit.SCALAR;
            int numUnits = r.nextInt(0, 5);
            for (int i = 0; i < numUnits; i++)
            {
                int power = r.nextInt(1, 10);
                if (r.nextBoolean())
                    power = -power;

                u = u.times(new Unit(r.choose(units)).raisedTo(power));
            }
            return u;
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
