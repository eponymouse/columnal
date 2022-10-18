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

package xyz.columnal.transformations.expression.visitor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.transformations.expression.AddSubtractExpression;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.AndExpression;
import xyz.columnal.transformations.expression.ArrayExpression;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DefineExpression;
import xyz.columnal.transformations.expression.DefineExpression.DefineItem;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.FieldAccessExpression;
import xyz.columnal.transformations.expression.HasTypeExpression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.IfThenElseExpression;
import xyz.columnal.transformations.expression.ImplicitLambdaArg;
import xyz.columnal.transformations.expression.InvalidIdentExpression;
import xyz.columnal.transformations.expression.InvalidOperatorExpression;
import xyz.columnal.transformations.expression.LambdaExpression;
import xyz.columnal.transformations.expression.MatchAnythingExpression;
import xyz.columnal.transformations.expression.MatchExpression;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.NotEqualExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.OrExpression;
import xyz.columnal.transformations.expression.PlusMinusPatternExpression;
import xyz.columnal.transformations.expression.RaiseExpression;
import xyz.columnal.transformations.expression.RecordExpression;
import xyz.columnal.transformations.expression.StringConcatExpression;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TemporalLiteral;
import xyz.columnal.transformations.expression.TimesExpression;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitLiteralExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.time.temporal.TemporalAccessor;

public abstract class ExpressionVisitorFlat<T> implements ExpressionVisitor<T>
{
    protected abstract T makeDef(Expression expression);

    @Override
    public T notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
    {
        return makeDef(self);
    }

    @Override
    public T and(AndExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T or(OrExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T list(ArrayExpression self, ImmutableList<@Recorded Expression> items)
    {
        return makeDef(self);
    }

    @Override
    public T litBoolean(BooleanLiteral self, @Value Boolean value)
    {
        return makeDef(self);
    }

    @Override
    public T call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments)
    {
        return makeDef(self);
    }

    @Override
    public T comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        return makeDef(self);
    }

    @Override
    public T equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern)
    {
        return makeDef(self);
    }

    @Override
    public T ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
    {
        return makeDef(self);
    }

    @Override
    public T ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        return makeDef(self);
    }

    @Override
    public T invalidIdent(InvalidIdentExpression self, String text)
    {
        return makeDef(self);
    }

    @Override
    public T implicitLambdaArg(ImplicitLambdaArg self)
    {
        return makeDef(self);
    }

    @Override
    public T invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items)
    {
        return makeDef(self);
    }

    @Override
    public T matchAnything(MatchAnythingExpression self)
    {
        return makeDef(self);
    }

    @Override
    public T litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit)
    {
        return makeDef(self);
    }

    @Override
    public T plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return makeDef(self);
    }

    @Override
    public T concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }

    @Override
    public T litText(StringLiteral self, String value)
    {
        return makeDef(self);
    }

    @Override
    public T litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value)
    {
        return makeDef(self);
    }

    @Override
    public T multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return makeDef(self);
    }


    @Override
    public T record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
    {
        return makeDef(self);
    }

    @Override
    public T litType(TypeLiteralExpression self, @Recorded TypeExpression type)
    {
        return makeDef(self);
    }

    @Override
    public T litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression)
    {
        return makeDef(self);
    }

    @Override
    public T match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
    {
        return makeDef(self);
    }

    @Override
    public T define(DefineExpression self, ImmutableList<DefineItem> defines, @Recorded Expression body)
    {
        return makeDef(self);
    }

    @Override
    public T hasType(@Recorded HasTypeExpression self, @ExpressionIdentifier String lhsVar, @Recorded Expression rhsType)
    {
        return makeDef(self);
    }

    @Override
    public T lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
    {
        return makeDef(self);
    }

    @Override
    public T field(FieldAccessExpression self, Expression lhsRecord, String fieldName)
    {
        return makeDef(self);
    }
}
