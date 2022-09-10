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

package test.utility.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenNumber extends Generator<@Value Number>
{
    private final boolean fixBits;

    @SuppressWarnings("valuetype")
    public GenNumber(boolean fixBits)
    {
        super(Number.class);
        this.fixBits = fixBits;
    }

    public GenNumber()
    {
        this(false);
    }

    @Override
    public @Value Number generate(SourceOfRandomness sourceOfRandomness, @Nullable GenerationStatus generationStatus)
    {
        @Value Number n;
        try
        {
            n = Utility.parseNumber(new GenNumberAsString(fixBits).generate(sourceOfRandomness, generationStatus));
        }
        catch (UserException e)
        {
            throw new RuntimeException(e);
        }
        List<@Value Number> rets = new ArrayList<>();
        rets.add(n);
        if (n.doubleValue() == (double)n.intValue())
        {
            // If it fits in smaller, we may randomly choose to use smaller:
            rets.add(DataTypeUtility.value(BigDecimal.valueOf(DataTypeUtility.value(n.intValue()))));
            if ((long) n.intValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.intValue()));
            if ((long) n.shortValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.shortValue()));
            if ((long) n.byteValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.byteValue()));
        }
        return sourceOfRandomness.choose(rets);
    }
}
