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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Created by neil on 13/12/2016.
 */
public class RaiseExpression extends BinaryOpExpression
{
    public RaiseExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "^";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new RaiseExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(@Recorded RaiseExpression this, ColumnLookup data, TypeState typeState, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        final @NonNull @Recorded TypeExp lhsTypeFinal = lhsType.typeExp;
        final @NonNull TypeExp rhsTypeFinal = rhsType.typeExp;
        
        // Raise expression is sort of an overloaded operator.  If the right-hand side is an integer
        // constant, it adjusts the units on the left-hand side.  Otherwise, it requires unit-less
        // items on both sides.
        
        // So, we attempt to constant-fold the RHS to distinguish:
        Optional<Rational> rhsPower = rhs.constantFold();
        if (rhsPower.isPresent())
        {
            Rational r = rhsPower.get();
            boolean numeratorOne = r.getNum().equals(BigInteger.ONE);
            boolean denominatorOne = r.getDen().equals(BigInteger.ONE);
            if (numeratorOne && denominatorOne)
            {
                // Raising to power 1, just leave type as-is:
                return new CheckedExp(lhsTypeFinal, typeState);
            }
            else if (numeratorOne || denominatorOne)
            {
                final TypeExp ourType;
                // Either raising to an integer power, or rooting:
                try
                {
                    if (numeratorOne)
                    {
                        // Rooting by integer power:
                        
                        // We raise LHS units to the opposite:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getDen().intValueExact())))) == null)
                            return null;
                        ourType = new NumTypeExp(this, new UnitExp(lhsUnit));
                    }
                    else
                    {
                        // Raising to integer power:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit)))) == null)
                            return null;
                        ourType = new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getNum().intValueExact()));
                    }
                    return new CheckedExp(onError.recordTypeNN(this, ourType), typeState);
                }
                catch (ArithmeticException e)
                {
                    onError.recordError(rhs, StyledString.s("Power is too large to track the units"));
                    return null;
                }
            }
            
            // If power is not 1, integer, or 1/integer, fall through:
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.plainNumber(this), lhsTypeFinal)) == null)
            return null;
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.plainNumber(this), rhsTypeFinal)) == null)
            return null;
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.plainNumber(this)), typeState);
    }

    @Override
    protected Pair<ExpressionKind, ExpressionKind> getOperandKinds()
    {
        return new Pair<>(ExpressionKind.EXPRESSION, ExpressionKind.EXPRESSION);
    }

    @Override
    public @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws UserException, InternalException
    {
        return Utility.raiseNumber(Utility.cast(lhsValue.value, Number.class), Utility.cast(rhsValue.value, Number.class));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.raise(this, lhs, rhs);
    }
}
