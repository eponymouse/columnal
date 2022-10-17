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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public abstract class BinaryOpExpression extends Expression
{
    /*
    public static enum Op
    {
        AND("&"), OR("|"), EQUALS("="), ADD("+"), SUBTRACT("-");

        private final String symbol;

        Op(String symbol)
        {
            this.symbol = symbol;
        }

        public static @Nullable Op parse(String text)
        {
            for (Op op : values())
                if (op.symbol.equals(text))
                    return op;
            return null;
        }
    }

    private final Op op;*/
    protected final @Recorded Expression lhs;
    protected final @Recorded Expression rhs;
    protected @MonotonicNonNull CheckedExp lhsType;
    protected @MonotonicNonNull CheckedExp rhsType;

    protected BinaryOpExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = lhs.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + " " + saveOp() + " " + rhs.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames);
        if (surround != BracketedStatus.NEED_BRACKETS)
            return inner;
        else
            return "(" + inner + ")";
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString inner = StyledString.concat(lhs.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s(" " + saveOp() + " "), rhs.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler));
        if (surround != BracketedStatus.NEED_BRACKETS)
            return expressionStyler.styleExpression(inner, this);
        else
            return expressionStyler.styleExpression(StyledString.roundBracket(inner), this);
    }

    // For saving to string:
    protected abstract String saveOp();

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BinaryOpExpression that = (BinaryOpExpression) o;

        if (!saveOp().equals(that.saveOp())) return false;
        if (!lhs.equals(that.lhs)) return false;
        return rhs.equals(that.rhs);
    }

    @Override
    public int hashCode()
    {
        int result = lhs.hashCode();
        result = 31 * result + saveOp().hashCode();
        result = 31 * result + rhs.hashCode();
        return result;
    }
    
    public abstract BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS);

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return copy(lhs.replaceSubExpression(toReplace, replaceWith), rhs.replaceSubExpression(toReplace, replaceWith));
    }

    @Override
    @OnThread(Tag.Simulation)
    public final ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        if (lhs instanceof ImplicitLambdaArg || rhs instanceof ImplicitLambdaArg)
        {
            return ImplicitLambdaArg.makeImplicitFunction(this, ImmutableList.of(lhs, rhs), state, s -> {
                ImmutableList.Builder<ValueResult> lhsrhs = ImmutableList.builderWithExpectedSize(2);
                ValueResult lhsValue = fetchSubExpression(lhs, s, lhsrhs);
                ValueResult rhsValue = fetchSubExpression(rhs, s, lhsrhs);
                try
                {
                    @Value Object result = getValueBinaryOp(lhsValue, rhsValue);
                    return result(result, s, lhsrhs.build());
                }
                catch (UserException e)
                {
                    throw new EvaluationException(e, this, ExecutionType.VALUE, s, lhsrhs.build());
                }
            });
        }
        else
        {
            ImmutableList.Builder<ValueResult> lhsrhs = ImmutableList.builderWithExpectedSize(2);
            ValueResult lhsValue = fetchSubExpression(lhs, state, lhsrhs);
            ValueResult rhsValue = fetchSubExpression(rhs, state, lhsrhs);
            @Value Object result;
            try
            {
                result = getValueBinaryOp(lhsValue, rhsValue);
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, this, ExecutionType.VALUE, state, lhsrhs.build());
            }
            return result(result, state, lhsrhs.build());
        }
    }

    // This is allowed to throw UserException since it won't fetch any
    // sub-expressions and thus we can assume it is top of a stack:
    @OnThread(Tag.Simulation)
    public abstract @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws UserException, InternalException;

    @Override
    public final @Nullable CheckedExp check(@Recorded BinaryOpExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(lhs, rhs), typeState, onError);
        typeState = lambda.getSecond();
        @Nullable CheckedExp lhsChecked = lhs.check(dataLookup, typeState, getOperandKinds().getFirst(), argLocationInfo(), onError);
        if (lhsChecked == null)
            return null;
        @Nullable CheckedExp rhsChecked = rhs.check(dataLookup, lhsChecked.typeState, getOperandKinds().getSecond(), argLocationInfo(), onError);
        if (rhsChecked == null)
            return null;
        lhsType = lhsChecked;
        rhsType = rhsChecked;
        @Nullable CheckedExp checked = checkBinaryOp(dataLookup, typeState, kind, onError);
        return checked == null ? null : checked.applyToType(lambda.getFirst());
    }

    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_DEFAULT;
    }

    protected abstract Pair<ExpressionKind, ExpressionKind> getOperandKinds();
    
    @RequiresNonNull({"lhsType", "rhsType"})
    protected abstract @Nullable CheckedExp checkBinaryOp(@Recorded BinaryOpExpression this, ColumnLookup data, TypeState typeState, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(
            lhs._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), newLHS -> copy(p.getSecond().apply(newLHS), rhs))),
            rhs._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), newRHS -> copy(lhs, p.getSecond().apply(newRHS))))
        );
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // Most binary ops require same type, so this is typical (can always override):
        if (r.nextBoolean())
        {
            return copy(lhsType != null && lhsType.typeExp.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(rhsType == null ? null : rhsType.typeExp), rhs);
        }
        else
        {
            return copy(lhs, rhsType != null && rhsType.typeExp.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(lhsType == null ? null : lhsType.typeExp));
        }
    }

    public Expression getLHS()
    {
        return lhs;
    }

    public Expression getRHS()
    {
        return rhs;
    }

    public String _test_getOperatorEntry()
    {
        return saveOp();
    }
}
