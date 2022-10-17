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
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LambdaExpression extends Expression
{
    private final ImmutableList<@Recorded Expression> parameters;
    private final @Recorded Expression body;

    public LambdaExpression(ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
    {
        this.parameters = parameters;
        this.body = body;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList.Builder<TypeExp> paramTypes = ImmutableList.builder();

        TypeState typeState = original;
        
        for (@Recorded Expression parameter : parameters)
        {
            CheckedExp checkedExp = parameter.check(dataLookup, typeState, ExpressionKind.PATTERN, LocationInfo.UNIT_DEFAULT, onError);
            if (checkedExp == null)
                return null;
            paramTypes.add(checkedExp.typeExp);
            typeState = checkedExp.typeState;
        }
        
        CheckedExp returnType = body.check(dataLookup, typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (returnType == null)
            return null;
        
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.function(this, paramTypes.build(), returnType.typeExp)), original);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState original) throws EvaluationException, InternalException
    {
        ValueFunction valueFunction = new ValueFunction()
        {
            @Override
            protected @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
            {
            EvaluateState state = original;
            ImmutableList.Builder<ValueResult> args = ImmutableList.builderWithExpectedSize(parameters.size());
            for (int i = 0; i < parameters.size(); i++)
            {
                ValueResult result = matchSubExpressionAsPattern(parameters.get(i), arg(i), state, args);
                state = result.evaluateState;
            }
            ValueResult bodyOutcome = fetchSubExpression(body, state, args);
            addExtraExplanation(() -> bodyOutcome.makeExplanation(ExecutionType.VALUE));
            return bodyOutcome.value;
            }
        };
        
        return explanation(ValueFunction.value(valueFunction), ExecutionType.VALUE, original, ImmutableList.of(), ImmutableList.of(), false);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.lambda(this, parameters, body);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String params = parameters.stream().map(e -> e.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames)).collect(Collectors.joining(", "));
        String body = this.body.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames);
        if (saveDestination.needKeywords())
            return "@function(" + params + ") @then " + body + "@endfunction";
        else
            return "@function" + params + " @then " + body + "@endfunction";
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
        LambdaExpression that = (LambdaExpression) o;
        return parameters.equals(that.parameters) &&
                body.equals(that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(parameters, body);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@function("),
            parameters.stream().map(e -> e.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)).collect(StyledString.joining(", ")),
            StyledString.s(") @then "),
            body.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" @endfunction")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else 
            return new LambdaExpression(Utility.mapListI(parameters, e -> e.replaceSubExpression(toReplace, replaceWith)), body.replaceSubExpression(toReplace, replaceWith));
    }
}
