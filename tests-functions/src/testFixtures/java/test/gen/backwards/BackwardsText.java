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

package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.StringConcatExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsText extends BackwardsProvider
{
    public BackwardsText(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        if (!targetType.equals(DataType.TEXT))
            return ImmutableList.of();
        
        return ImmutableList.of(() -> {
            int numOperands = r.nextInt(2, 5);
            List<Integer> breakpoints = new ArrayList<>();
            int[] codepoints = ((String)targetValue).codePoints().toArray();
            for (int i = 0; i < numOperands - 1; i++)
            {
                breakpoints.add(r.nextInt(0, codepoints.length));
            }
            Collections.sort(breakpoints);
            //Simplify following code by adding start and end index to list:
            breakpoints.add(0, 0);
            breakpoints.add(codepoints.length);
            List<Expression> operands = new ArrayList<>();
            for (int i = 0; i < numOperands; i++)
            {
                Expression e = parent.make(DataType.TEXT, new String(codepoints, breakpoints.get(i), breakpoints.get(i + 1) - breakpoints.get(i)), maxLevels - 1);
                operands.add(e);
            }
            return new StringConcatExpression(operands);
        });
    }
}
