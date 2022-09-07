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
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.TableId;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;

import java.time.temporal.TemporalAccessor;
import java.util.List;

public interface ExpressionVisitor<T>
{
    T notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs);
    T divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops);

    T and(AndExpression self, ImmutableList<@Recorded Expression> expressions);
    T or(OrExpression self, ImmutableList<@Recorded Expression> expressions);

    T list(ArrayExpression self, ImmutableList<@Recorded Expression> items);

    T litBoolean(BooleanLiteral self, @Value Boolean value);

    T call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments);

    T comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators);
    // Singular name to avoid clash with Object.equals
    T equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern);

    T ident(@Recorded IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable);

    T ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression);

    T invalidIdent(InvalidIdentExpression self, String text);

    T implicitLambdaArg(ImplicitLambdaArg self);

    T invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items);

    T matchAnything(MatchAnythingExpression self);

    T litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit);

    T plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions);

    T litText(StringLiteral self, String rawValue);

    T litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value);

    T multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions);

    T litType(TypeLiteralExpression self, @Recorded TypeExpression type);

    T litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression);

    T match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses);
    
    T define(DefineExpression self, ImmutableList<DefineExpression.DefineItem> defines, @Recorded Expression body);

    T hasType(@Recorded HasTypeExpression self, @ExpressionIdentifier String lhsVar, @Recorded Expression rhsType);

    T lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body);

    T record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members);

    T field(FieldAccessExpression self, @Recorded Expression lhsRecord, String fieldName);
}
