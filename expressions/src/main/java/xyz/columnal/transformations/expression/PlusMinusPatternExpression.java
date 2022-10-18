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

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.EpsilonType;

/**
 * This is a pattern match item, value +- tolerance.
 */
public class PlusMinusPatternExpression extends BinaryOpExpression
{
    public PlusMinusPatternExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "\u00B1";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new PlusMinusPatternExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(@Recorded PlusMinusPatternExpression this, ColumnLookup data, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (kind != ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s(saveOp() + " is not valid outside a pattern"));
            return null;
        }
        
        // The LHS and RHS must be numbers with matching units.  The result is then that same number
        // LHS needs to be specific number value.
        
        
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(new NumTypeExp(this, new UnitExp(new MutUnitVar())), lhsType.typeExp, rhsType.typeExp), state);
    }

    @Override
    protected Pair<ExpressionKind, ExpressionKind> getOperandKinds()
    {
        // A pattern would not be valid on the left (how do you match 5.6 against defvar x +- 0.1 ?  Either match
        // just against var x, or give value.  That would also allow weird nestings like (1 +- 0.1) +- 0.2), and exact same idea for RHS.
        return new Pair<>(ExpressionKind.EXPRESSION, ExpressionKind.EXPRESSION);
    }

    @Override
    public @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws InternalException
    {
        throw new InternalException("Calling getValue on plus minus pattern (should only call matchAsPattern)");
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, EvaluationException
    {
        ImmutableList.Builder<ValueResult> lhsrhs = ImmutableList.builderWithExpectedSize(2);
        @Value Object lhsValue = fetchSubExpression(lhs, state, lhsrhs).value;
        @Value Object rhsValue = fetchSubExpression(rhs, state, lhsrhs).value;
        boolean match = Utility.compareNumbers(Utility.cast(value, Number.class), Utility.cast(lhsValue, Number.class), new Pair<>(EpsilonType.ABSOLUTE, Utility.toBigDecimal(Utility.cast(rhsValue, Number.class)))) == 0;
        return explanation(DataTypeUtility.value(match), ExecutionType.MATCH, state, lhsrhs.build(), ImmutableList.of(), false);
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_CONSTRAINED;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.plusMinus(this, lhs, rhs);
    }
}
