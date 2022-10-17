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

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * A subclass of {@link NaryOpExpression} for expressions that use
 * all their arguments for calculation every time (e.g.
 * times expression) as opposed to being able to short-circuit
 * (like in an and expression).
 */
public abstract class NaryOpTotalExpression extends NaryOpExpression
{
    public NaryOpTotalExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    @OnThread(Tag.Simulation)
    public final ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return ImplicitLambdaArg.makeImplicitFunction(this, expressions, state, s -> {
                ImmutableList.Builder<ValueResult> expressionValues = ImmutableList.builderWithExpectedSize(expressions.size());
                for (Expression expression : expressions)
                {
                    fetchSubExpression(expression, s, expressionValues);
                }
                return getValueNaryOp(expressionValues.build(), s);
            });
        }
        else
        {
            ImmutableList.Builder<ValueResult> expressionValues = ImmutableList.builderWithExpectedSize(expressions.size());
            for (Expression expression : expressions)
            {
                fetchSubExpression(expression, state, expressionValues);
            }
            return getValueNaryOp(expressionValues.build(), state);
        }
    }

    @OnThread(Tag.Simulation)
    public abstract ValueResult getValueNaryOp(ImmutableList<ValueResult> expressionValues, EvaluateState state) throws EvaluationException, InternalException;
}
