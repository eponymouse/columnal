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
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.AndExpression;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.NotEqualExpression;
import xyz.columnal.transformations.expression.OrExpression;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsBooleans extends BackwardsProvider
{
    public BackwardsBooleans(SourceOfRandomness r, RequestBackwardsExpression parent)
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
        if (targetType.equals(DataType.BOOLEAN))
        {
            Boolean b = (Boolean) targetValue;
            return ImmutableList.of(
                () -> and(maxLevels, b),
                () -> or(maxLevels, b),
                () -> not(maxLevels, b),
                () -> xor(maxLevels, b),
                () -> eq(maxLevels, b),
                () -> neq(maxLevels, b),
                () -> cmp(maxLevels, b)
            );
        }
        return ImmutableList.of();
    }

    private Expression cmp(int maxLevels, boolean target) throws InternalException, UserException
    {
            // First form a valid set of values and sort them into order
            boolean ascending = r.nextBoolean();
            DataType dataType = parent.makeType();
            List<Pair<@Value Object, Expression>> operands = new ArrayList<>(TBasicUtil.<Pair<@Value Object, Expression>>makeList(r, 2, 5, () -> {
                @Value Object value = parent.makeValue(dataType);
                return new Pair<>(value, parent.make(dataType, value, maxLevels - 1));
            }));
            Collections.sort(operands, (a, b) -> { try
            {
                return Utility.compareValues(a.getFirst(), b.getFirst()) * (ascending ? 1 : -1);
            } catch (UserException | InternalException e) { throw new RuntimeException(e); }});
            List<ComparisonOperator> ops = new ArrayList<>();
            for (int i = 0; i < operands.size() - 1; i++)
            {
                // We may have randomly generated equal values, so check for that and adjust
                // operator to the or-equals variant if necessary:
                ops.add(
                        Utility.compareValues(operands.get(i).getFirst(), operands.get(i+1).getFirst()) == 0 ?
                                (ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
                                : (ascending ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN));
            }
            // Randomly duplicate a value and change to <=/>=:
            int swap = r.nextInt(0, operands.size() - 2); // Picking operator really, not operand
            // Copy from left to right or right to left:
            if (r.nextBoolean())
            {
                // Copy from right to left:
                @Value Object newTarget = operands.get(swap + 1).getFirst();
                operands.set(swap, new Pair<>(newTarget, parent.make(dataType, newTarget, maxLevels - 1)));
                ops.set(swap, ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO);
            }
            else
            {
                // Copy from left to right:
                @Value Object newTarget = operands.get(swap).getFirst();
                operands.set(swap + 1, new Pair<>(newTarget, parent.make(dataType, newTarget, maxLevels - 1)));
                ops.set(swap, ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO);
            }
            // If we are aiming for true, job done.
            if ((Boolean)target == false)
            {
                // Need to make it false by swapping two adjacent values (and getting rid of <=/>=)
                int falsifyOp = r.nextInt(0, operands.size() - 2); // Picking operator really, not operand
                Pair<@Value Object, Expression> temp = operands.get(falsifyOp);
                operands.set(falsifyOp, operands.get(falsifyOp + 1));
                operands.set(falsifyOp + 1, temp);
                ops.set(falsifyOp, ascending ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN);
            }
            return new ComparisonExpression(Utility.mapList(operands, p -> p.getSecond()), ImmutableList.copyOf(ops));
    }

    private Expression neq(int maxLevels, boolean target) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object valA = parent.makeValue(t);
        @Value Object valB;
        int attempts = 0;
        do
        {
            valB = parent.makeValue(t);
            if (attempts++ >= 100)
                return new BooleanLiteral(target);
        }
        while (Utility.compareValues(valA, valB) == 0);
        return new NotEqualExpression(parent.make(t, valA, maxLevels - 1), parent.make(t, target == true ? valB : valA, maxLevels - 1));
    }

    private Expression eq(int maxLevels, boolean target) throws InternalException, UserException
    {
        int size = r.nextInt(2, 5);
        ArrayList<Expression> expressions = new ArrayList<>();
        DataType t = parent.makeType();
        if (target == true)
        {
            @Value Object valA = parent.makeValue(t);
            for (int i = 0; i < size; i++)
            {
                expressions.add(parent.make(t, valA, maxLevels - 1));
            }
            return new EqualExpression(expressions, false);
        }
        else
        {
            // To provide a good test, we make an initial value, plus 
            // somewhere between 0 and size - 2 (incl) duplicates,
            // then the remaining 1 to size - 1 (incl) are different than original
            // (may be same as each other by coincidence, but that's okay)
            @Value Object valA = parent.makeValue(t);
            int sameAsA = r.nextInt(1, size - 1);
            for (int i = 0; i < sameAsA; i++)
            {
                expressions.add(parent.make(t, valA, maxLevels - 1));
            }
            while (expressions.size() < size)
            {
                @Value Object diffValFromA;
                int attempts = 0;
                do
                {
                    diffValFromA = parent.makeValue(t);
                    if (attempts++ >= 100)
                        return new BooleanLiteral(target);
                }
                while (Utility.compareValues(valA, diffValFromA) == 0);
                expressions.add(parent.make(t, diffValFromA, maxLevels - 1));
            }
        }
        return new EqualExpression(expressions, false);
    }

    private Expression xor(int maxLevels, boolean b) throws InternalException, UserException
    {
        // Randomly pick LHS:
        boolean lhs = r.nextBoolean();
        // If b == true, rhs == !lhs.  If b == false, rhs == lhs
        boolean rhs = b ? !lhs : lhs;

        return new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "xor", 
            parent.make(DataType.BOOLEAN, lhs, maxLevels -1),
            parent.make(DataType.BOOLEAN, rhs, maxLevels -1));
    }

    private Expression not(int maxLevels, boolean b) throws InternalException, UserException
    {
        return call("not", parent.make(DataType.BOOLEAN, !b, maxLevels -1));
    }

    private Expression or(int maxLevels, boolean b) throws InternalException, UserException
    {
        // If target is false, all must be false:
        if (b == false)
            return new OrExpression(TBasicUtil.makeList(r, 2, 5, () -> parent.make(DataType.BOOLEAN, false, maxLevels - 1)));
            // Otherwise they can take on random values, but one must be false:
        else
        {
            int size = r.nextInt(2, 5);
            int mustBeTrue = r.nextInt(0, size - 1);
            ArrayList<Expression> exps = new ArrayList<Expression>();
            for (int i = 0; i < size; i++)
                exps.add(parent.make(DataType.BOOLEAN, mustBeTrue == i ? true : r.nextBoolean(), maxLevels - 1));
            return new OrExpression(exps);
        }
    }

    private Expression and(int maxLevels, boolean b) throws InternalException, UserException
    {
        // If target is true, all must be true:
        if (b)
            return new AndExpression(TBasicUtil.makeList(r, 2, 5, () -> parent.make(DataType.BOOLEAN, true, maxLevels - 1)));
            // Otherwise they can take on random values, but one must be false:
        else
        {
            int size = r.nextInt(2, 5);
            int mustBeFalse = r.nextInt(0, size - 1);
            ArrayList<Expression> exps = new ArrayList<Expression>();
            for (int i = 0; i < size; i++)
                exps.add(parent.make(DataType.BOOLEAN, mustBeFalse == i ? false : r.nextBoolean(), maxLevels - 1));
            return new AndExpression(exps);
        }

    }
}
