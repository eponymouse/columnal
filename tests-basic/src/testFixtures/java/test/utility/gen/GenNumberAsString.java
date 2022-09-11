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

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by neil on 09/12/2016.
 */
public class GenNumberAsString extends Generator<String>
{
    private final boolean fixBits;
    private int maxBits = -1;

    /**
     *
     * @param fixBits If true then
     */
    public GenNumberAsString(boolean fixBits)
    {
        super(String.class);
        this.fixBits = fixBits;
    }

    public GenNumberAsString()
    {
        this(false);
    }

    @Override
    public String generate(SourceOfRandomness sourceOfRandomness, @Nullable GenerationStatus generationStatus)
    {
        if (sourceOfRandomness.nextBoolean())
        {
            // Use awkward numbers:
            return sourceOfRandomness.choose(Arrays.<Number>asList(
                0, 1, -1,
                Byte.MIN_VALUE, Byte.MIN_VALUE + 1, Byte.MAX_VALUE,
                Short.MIN_VALUE, Short.MIN_VALUE + 1, Short.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE,
                (long)Integer.MIN_VALUE - 1L, (long)Integer.MAX_VALUE + 1L,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE,
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
            )).toString();
        }
        if (!fixBits || maxBits == -1)
        {
            maxBits = sourceOfRandomness.nextInt(3, 80);
        }
        boolean includeFractional = maxBits >= 48;
        if (includeFractional && sourceOfRandomness.nextBoolean())
        {
            // I don't think it matters here whether we come up with
            // double or big decimal; will be stored in big decimal either way.
            return String.format("%f", sourceOfRandomness.nextDouble());
        }
        else
        {
            // We geometrically distribute by uniformly distributing number of bits:
            return sourceOfRandomness.nextBigInteger(sourceOfRandomness.nextInt(1, maxBits)).toString();
        }
    }
}
