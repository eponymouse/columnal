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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression.JellyRecorder;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A variable name, and a fixed type it should conform to.  Like the :: operator in Haskell expressions,
 * (or like the `asTypeOf` function if you specified a type). 
 */
public class HasTypeExpression extends Expression
{
    // Var name, without the leading decorator
    private final @ExpressionIdentifier String lhsVar;
    private final @Recorded Expression rhsType;

    public HasTypeExpression(@ExpressionIdentifier String lhsVar, @Recorded Expression rhsType)
    {
        this.lhsVar = lhsVar;
        this.rhsType = rhsType;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (lhsVar == null)
            onError.recordError(this, StyledString.s("Left-hand side of :: must be a valid name"));
        
        @Nullable @Recorded TypeExpression rhsTypeExpression = rhsType.visit(new ExpressionVisitorFlat<@Nullable @Recorded TypeExpression>()
        {
            @Override
            protected @Nullable @Recorded TypeExpression makeDef(Expression expression)
            {
                onError.recordError(rhsType, StyledString.s("Right-hand side of :: must be a type{} expression"));
                return null;
            }

            @Override
            public @Nullable @Recorded TypeExpression litType(TypeLiteralExpression self, @Recorded TypeExpression type)
            {
                return type;
            }
        });
        
        if (rhsTypeExpression == null)
            return null;
        
        TypeState typeState = original.addPreType(lhsVar, rhsTypeExpression.toJellyType(original.getTypeManager(), new JellyRecorder()
        {
            @SuppressWarnings("recorded")
            @Override
            public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source)
            {
                return jellyType;
            }
        }).makeTypeExp(ImmutableMap.of()), (StyledString s) -> onError.<Expression>recordError(this, s));
        
        if (typeState == null)
            return null;
        
        // Our type shouldn't be directly used, but we don't want to return null, hence void:
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.fromDataType(this, typeState.getTypeManager().getVoidType().instantiate(ImmutableList.of(), typeState.getTypeManager()))), typeState);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws InternalException
    {
        throw new InternalException("Type definitions have no value");
    }

    @Override
    public <T> T visit(@Recorded HasTypeExpression this, ExpressionVisitor<T> visitor)
    {
        return visitor.hasType(this, lhsVar, rhsType);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return lhsVar + " :: " + rhsType.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HasTypeExpression that = (HasTypeExpression) o;
        return lhsVar.equals(that.lhsVar) &&
                rhsType.equals(that.rhsType);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lhsVar, rhsType);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(StyledString.s(lhsVar), StyledString.s(" :: "), rhsType.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler)), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (toReplace == this)
            return replaceWith;
        else
            return new HasTypeExpression(lhsVar, rhsType.replaceSubExpression(toReplace, replaceWith));
    }

    public @ExpressionIdentifier String getVarName()
    {
        return lhsVar;
    }
}
